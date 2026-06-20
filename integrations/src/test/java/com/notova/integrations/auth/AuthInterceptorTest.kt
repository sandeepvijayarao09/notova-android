package com.notova.integrations.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Verifies [AuthInterceptor] attaches `Authorization: Bearer <token>` when the store has a token,
 * omits it when signed out, and does not clobber a header the caller already set.
 */
class AuthInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWith(store: TokenStore) = OkHttpClient.Builder().addInterceptor(AuthInterceptor(store)).build()

    @Test
    fun `adds a bearer header when a token is present`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val client = clientWith(FakeTokenStore(AuthTokens("access-123", "refresh")))

        client.newCall(Request.Builder().url(server.url("/v1/auth/me")).build()).execute().close()

        assertEquals("Bearer access-123", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `omits the bearer header when signed out`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val client = clientWith(FakeTokenStore(initial = null))

        client.newCall(Request.Builder().url(server.url("/v1/auth/me")).build()).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `does not overwrite an Authorization header set by the caller`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val client = clientWith(FakeTokenStore(AuthTokens("store-token", "refresh")))

        client.newCall(
            Request.Builder()
                .url(server.url("/v1/auth/me"))
                .header("Authorization", "Bearer caller-token")
                .build(),
        ).execute().close()

        assertEquals("Bearer caller-token", server.takeRequest().getHeader("Authorization"))
    }
}
