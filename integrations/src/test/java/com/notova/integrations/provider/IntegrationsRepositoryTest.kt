package com.notova.integrations.provider

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notova.integrations.api.NotovaBackendApi
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
 * Drives [IntegrationsRepository] against a [MockWebServer]: list decodes connected status, connect
 * returns the authorize URL + state, the dev-safe "provider not configured" error is surfaced
 * distinctly, and disconnect maps the boolean result.
 */
class IntegrationsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: IntegrationsRepository

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
        val api =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(NotovaBackendApi::class.java)
        repository = IntegrationsRepository(api)
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
    fun `list decodes providers and their connected status`() =
        runBlocking {
            enqueue(
                200,
                """[{"provider":"notion","connected":true},{"provider":"todoist","connected":false}]""",
            )

            val result = repository.list()

            assertTrue(result is IntegrationsListResult.Success)
            val providers = (result as IntegrationsListResult.Success).providers
            assertEquals(2, providers.size)
            assertEquals(IntegrationProvider("notion", true), providers[0])
            assertEquals(IntegrationProvider("todoist", false), providers[1])
            assertEquals("/v1/integrations", server.takeRequest().path)
        }

    @Test
    fun `connect returns the authorize url and state`() =
        runBlocking {
            enqueue(200, """{"authorizeUrl":"https://notion.so/oauth?x=1","state":"abc"}""")

            val result = repository.connect("notion")

            assertTrue(result is ConnectResult.Authorize)
            val authorize = result as ConnectResult.Authorize
            assertEquals("notion", authorize.provider)
            assertEquals("https://notion.so/oauth?x=1", authorize.authorizeUrl)
            assertEquals("abc", authorize.state)
            assertEquals("/v1/integrations/notion/connect", server.takeRequest().path)
        }

    @Test
    fun `connect surfaces the dev-safe provider-not-configured error distinctly`() =
        runBlocking {
            enqueue(
                501,
                """{"error":{"code":"provider_not_configured","message":"slack is not configured"}}""",
            )

            val result = repository.connect("slack")

            assertTrue(result is ConnectResult.NotConfigured)
            val notConfigured = result as ConnectResult.NotConfigured
            assertEquals("slack", notConfigured.provider)
            assertTrue(notConfigured.message.contains("slack"))
            assertTrue(notConfigured.message.contains("configured", ignoreCase = true))
        }

    @Test
    fun `connect detects not-configured from the error body even on a 400`() =
        runBlocking {
            enqueue(400, """{"error":{"code":"bad_request","message":"provider not configured"}}""")

            val result = repository.connect("salesforce")

            assertTrue(result is ConnectResult.NotConfigured)
        }

    @Test
    fun `connect maps other errors to a generic failure`() =
        runBlocking {
            enqueue(500, """{"error":{"code":"server_error","message":"boom"}}""")

            val result = repository.connect("notion")

            assertTrue(result is ConnectResult.Failure)
            assertTrue((result as ConnectResult.Failure).message.contains("500"))
        }

    @Test
    fun `disconnect maps a true result to success`() =
        runBlocking {
            enqueue(200, """{"disconnected":true}""")

            val result = repository.disconnect("notion")

            assertEquals(DisconnectResult.Success, result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/v1/integrations/notion", request.path)
        }

    @Test
    fun `disconnect maps a false result to a failure`() =
        runBlocking {
            enqueue(200, """{"disconnected":false}""")

            val result = repository.disconnect("notion")

            assertTrue(result is DisconnectResult.Failure)
        }

    @Test
    fun `list maps a network failure to a friendly failure`() =
        runBlocking {
            server.shutdown() // force a connection failure

            val result = repository.list()

            assertTrue(result is IntegrationsListResult.Failure)
        }
}
