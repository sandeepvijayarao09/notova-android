package com.notova.app.ui.auth

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notova.app.ui.RealMainDispatcherRule
import com.notova.app.ui.waitForState
import com.notova.integrations.api.NotovaBackendApi
import com.notova.integrations.auth.AuthRepository
import com.notova.integrations.auth.AuthTokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit

/**
 * Coverage for [AuthViewModel]: validation, login/register success (stores token + advances the
 * launch route reactively), failure surfaces an error, and sign-out clears the store. The
 * [AuthRepository] is wired to a [MockWebServer], so the network contract is exercised end-to-end
 * without a live backend.
 *
 * Uses a REAL Main dispatcher + StateFlow polling because the suspend Retrofit calls resume on
 * OkHttp's background threads (virtual-time advancement can't await them).
 */
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = RealMainDispatcherRule()

    private lateinit var server: MockWebServer
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
        val api =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(NotovaBackendApi::class.java)
        store = FakeTokenStore()
        repository = AuthRepository(api, store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun viewModel() = AuthViewModel(repository)

    @Test
    fun `route settles to signed-out with no token`() {
        val vm = viewModel()
        val route = waitForState(supplier = { vm.route.value }) { it != AuthRoute.LOADING }
        assertEquals(AuthRoute.SIGNED_OUT, route)
    }

    @Test
    fun `login success stores the token and advances the route to signed-in`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"user":{"id":"u1","email":"a@b.com","createdAt":"2026-01-01T00:00:00Z"},""" +
                    """"accessToken":"at","refreshToken":"rt"}""",
            ),
        )
        val vm = viewModel()
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("password1")

        vm.signIn()
        waitForState(supplier = { vm.uiState.value }) { !it.submitting }

        assertEquals("at", store.accessTokenBlocking())
        val route = waitForState(supplier = { vm.route.value }) { it == AuthRoute.SIGNED_IN }
        assertEquals(AuthRoute.SIGNED_IN, route)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `create account success stores the token and advances the route`() {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"user":{"id":"u2","email":"new@x.com","createdAt":"2026-01-01T00:00:00Z"},""" +
                    """"accessToken":"a2","refreshToken":"r2"}""",
            ),
        )
        val vm = viewModel()
        vm.onEmailChange("new@x.com")
        vm.onPasswordChange("password1")

        vm.createAccount()
        waitForState(supplier = { vm.uiState.value }) { !it.submitting }

        assertEquals("a2", store.accessTokenBlocking())
        assertEquals(AuthRoute.SIGNED_IN, waitForState(supplier = { vm.route.value }) { it == AuthRoute.SIGNED_IN })
    }

    @Test
    fun `login failure surfaces a friendly error and does not advance`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        val vm = viewModel()
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("password1")

        vm.signIn()
        val state = waitForState(supplier = { vm.uiState.value }) { it.error != null }

        assertEquals("Incorrect email or password.", state.error)
        assertEquals(AuthRoute.SIGNED_OUT, vm.route.value)
        assertNull(store.accessTokenBlocking())
    }

    @Test
    fun `blank email is rejected by client-side validation without a network call`() {
        val vm = viewModel()
        vm.onPasswordChange("password1")

        vm.signIn()
        val state = waitForState(supplier = { vm.uiState.value }) { it.error != null }

        assertEquals("Enter your email.", state.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `short password is rejected by client-side validation`() {
        val vm = viewModel()
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("short")

        vm.signIn()
        val state = waitForState(supplier = { vm.uiState.value }) { it.error != null }

        assertEquals("Password must be at least 8 characters.", state.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `sign-out clears the store and routes back to signed-out`() {
        runBlocking { store.save(AuthTokens("at", "rt")) }
        val vm = viewModel()
        assertEquals(AuthRoute.SIGNED_IN, waitForState(supplier = { vm.route.value }) { it == AuthRoute.SIGNED_IN })

        vm.signOut()
        val route = waitForState(supplier = { vm.route.value }) { it == AuthRoute.SIGNED_OUT }

        assertNull(store.accessTokenBlocking())
        assertEquals(AuthRoute.SIGNED_OUT, route)
    }

    @Test
    fun `continue offline advances to signed-in without a token or network call`() {
        val vm = viewModel()
        assertEquals(AuthRoute.SIGNED_OUT, waitForState(supplier = { vm.route.value }) { it != AuthRoute.LOADING })

        vm.continueOffline()
        val route = waitForState(supplier = { vm.route.value }) { it == AuthRoute.SIGNED_IN }

        assertEquals(AuthRoute.SIGNED_IN, route)
        assertNull(store.accessTokenBlocking())
        assertEquals(0, server.requestCount)
    }
}
