package com.notova.integrations.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies the 401 handling of [TokenAuthenticator] wired into a real OkHttp client against a
 * [MockWebServer]:
 *  - a 401 triggers exactly one refresh, then the request is replayed with the new bearer and
 *    succeeds;
 *  - the refresh runs only once even if the replay also 401s (no infinite loop), and sign-out fires;
 *  - a failed refresh signs out and gives up.
 */
class TokenAuthenticatorTest {
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

    private fun client(
        store: TokenStore,
        refresh: suspend (String) -> String?,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(TokenAuthenticator(store, refresh))
            .build()

    @Test
    fun `401 triggers a single refresh and replays the request with the new bearer`() {
        // First call (with stale token) -> 401; replay (with refreshed token) -> 200.
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val store = FakeTokenStore(AuthTokens(accessToken = "stale", refreshToken = "rt"))
        val refreshCalls = AtomicInteger(0)
        val httpClient =
            client(store) {
                refreshCalls.incrementAndGet()
                "fresh-token"
            }

        val response =
            httpClient.newCall(Request.Builder().url(server.url("/v1/auth/me")).build()).execute()
        response.close()

        assertEquals(200, response.code)
        assertEquals(1, refreshCalls.get())
        assertEquals("fresh-token", store.accessTokenBlocking())

        // The first request carried the stale token...
        assertEquals("Bearer stale", server.takeRequest().getHeader("Authorization"))
        // ...the replay carried the refreshed token.
        assertEquals("Bearer fresh-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `refresh runs once then signs out when the replay also 401s`() {
        // Always 401: original + one replay. Authenticator must stop after one refresh.
        server.enqueue(MockResponse().setResponseCode(401).setBody("no"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("no"))

        val store = FakeTokenStore(AuthTokens("stale", "rt"))
        val refreshCalls = AtomicInteger(0)
        val httpClient =
            client(store) {
                refreshCalls.incrementAndGet()
                "fresh-token"
            }

        val response =
            httpClient.newCall(Request.Builder().url(server.url("/v1/auth/me")).build()).execute()
        response.close()

        assertEquals(401, response.code)
        assertEquals(1, refreshCalls.get())
        // Sign-out fired on the second (looping) 401.
        assertTrue(store.clearCount >= 1)
        assertEquals(null, store.accessTokenBlocking())
    }

    @Test
    fun `a failed refresh signs out and gives up`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("no"))

        val store = FakeTokenStore(AuthTokens("stale", "rt"))
        val refreshCalls = AtomicInteger(0)
        val httpClient =
            client(store) {
                refreshCalls.incrementAndGet()
                null // refresh fails
            }

        val response =
            httpClient.newCall(Request.Builder().url(server.url("/v1/auth/me")).build()).execute()
        response.close()

        assertEquals(401, response.code)
        assertEquals(1, refreshCalls.get())
        assertEquals(1, store.clearCount)
        assertEquals(null, store.accessTokenBlocking())
    }

    @Test
    fun `no refresh token signs out immediately`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("no"))

        // Access token present but no refresh token (e.g. partially-cleared state).
        val store = FakeTokenStore(AuthTokens(accessToken = "stale", refreshToken = ""))
        val refreshCalls = AtomicInteger(0)
        val httpClient =
            client(store) {
                refreshCalls.incrementAndGet()
                "x"
            }

        val response =
            httpClient.newCall(Request.Builder().url(server.url("/v1/auth/me")).build()).execute()
        response.close()

        assertEquals(401, response.code)
        assertEquals(0, refreshCalls.get())
        assertEquals(1, store.clearCount)
    }
}
