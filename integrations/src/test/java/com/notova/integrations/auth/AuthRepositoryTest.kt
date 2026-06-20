package com.notova.integrations.auth

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notova.integrations.api.NotovaBackendApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Drives [AuthRepository] against a [MockWebServer]: login/register success persists the token pair
 * in the store and reports [AuthResult.Success]; failures map to friendly [AuthResult.Failure]
 * messages without leaking the HTTP exception; sign-out clears the store.
 */
class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var api: NotovaBackendApi
    private lateinit var store: FakeTokenStore
    private lateinit var repository: AuthRepository

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val contentType = "application/json".toMediaType()
        api =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(NotovaBackendApi::class.java)
        store = FakeTokenStore()
        repository = AuthRepository(api, store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(
        code: Int,
        body: String,
    ) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `login success stores the token pair and reports success`() =
        runBlocking {
            enqueue(
                200,
                """{"user":{"id":"u1","email":"a@b.com","createdAt":"2026-01-01T00:00:00Z"},""" +
                    """"accessToken":"at","refreshToken":"rt"}""",
            )

            val result = repository.login("a@b.com", "pw")

            assertEquals(AuthResult.Success, result)
            assertEquals("at", store.accessTokenBlocking())
            assertEquals("rt", store.refreshTokenBlocking())
            assertEquals(true, repository.isSignedIn.first())
            val request = server.takeRequest()
            assertEquals("/v1/auth/login", request.path)
            assertTrue(request.body.readUtf8().contains("\"email\":\"a@b.com\""))
        }

    @Test
    fun `register success stores the token pair and reports success`() =
        runBlocking {
            enqueue(
                201,
                """{"user":{"id":"u2","email":"new@x.com","createdAt":"2026-01-01T00:00:00Z"},""" +
                    """"accessToken":"a2","refreshToken":"r2"}""",
            )

            val result = repository.register("new@x.com", "pw")

            assertEquals(AuthResult.Success, result)
            assertEquals("a2", store.accessTokenBlocking())
            assertEquals("/v1/auth/register", server.takeRequest().path)
        }

    @Test
    fun `login with bad credentials surfaces a friendly failure and stores nothing`() =
        runBlocking {
            enqueue(401, """{"error":{"code":"unauthorized","message":"no"}}""")

            val result = repository.login("a@b.com", "wrong")

            assertTrue(result is AuthResult.Failure)
            assertEquals("Incorrect email or password.", (result as AuthResult.Failure).message)
            assertEquals(null, store.accessTokenBlocking())
            assertEquals(false, repository.isSignedIn.first())
        }

    @Test
    fun `register conflict surfaces an account-exists failure`() =
        runBlocking {
            enqueue(409, """{"error":{"code":"conflict","message":"exists"}}""")

            val result = repository.register("taken@x.com", "pw")

            assertTrue(result is AuthResult.Failure)
            assertEquals("An account with that email already exists.", (result as AuthResult.Failure).message)
        }

    @Test
    fun `server error maps to a generic try-again failure`() =
        runBlocking {
            enqueue(500, """{"error":{"code":"server_error","message":"boom"}}""")

            val result = repository.login("a@b.com", "pw")

            assertTrue(result is AuthResult.Failure)
            assertTrue((result as AuthResult.Failure).message.contains("500"))
        }

    @Test
    fun `signOut clears the token store`() =
        runBlocking {
            store.save(AuthTokens("at", "rt"))
            assertEquals(true, repository.isSignedIn.first())

            repository.signOut()

            assertEquals(null, store.accessTokenBlocking())
            assertEquals(false, repository.isSignedIn.first())
        }
}
