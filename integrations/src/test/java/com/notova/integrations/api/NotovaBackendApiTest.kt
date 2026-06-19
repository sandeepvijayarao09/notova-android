package com.notova.integrations.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit

/**
 * Drives every endpoint of [NotovaBackendApi] against a [MockWebServer] using the same Retrofit +
 * kotlinx.serialization wiring as production. Asserts request path/method/body, response decoding,
 * and error behaviour against the exact backend `/v1` contract. Also pins the privacy invariant:
 * the metadata sync upsert body carries only recording metadata — never audio bytes, transcript
 * text, or summary content.
 */
class NotovaBackendApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: NotovaBackendApi

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
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(
        code: Int,
        body: String,
    ) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    // ----------------------------------------------------------------------------------------- Auth

    @Test
    fun `register posts credentials and decodes the user and token pair`() =
        runBlocking {
            enqueueJson(
                201,
                """{"user":{"id":"u1","email":"a@b.com","createdAt":"2026-01-01T00:00:00Z"},""" +
                    """"accessToken":"at","refreshToken":"rt"}""",
            )

            val response = api.register(RegisterRequest(email = "a@b.com", password = "pw"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/auth/register", request.path)
            val sentBody = request.body.readUtf8()
            assertTrue(sentBody.contains("\"email\":\"a@b.com\""))
            assertTrue(sentBody.contains("\"password\":\"pw\""))
            assertEquals("at", response.accessToken)
            assertEquals("rt", response.refreshToken)
            assertEquals("u1", response.user.id)
            assertEquals("a@b.com", response.user.email)
            assertEquals("2026-01-01T00:00:00Z", response.user.createdAt)
        }

    @Test
    fun `login posts credentials and decodes the user and token pair`() =
        runBlocking {
            enqueueJson(
                200,
                """{"user":{"id":"u2","email":"u@x.com","createdAt":"2026-02-02T00:00:00Z"},""" +
                    """"accessToken":"a","refreshToken":"r"}""",
            )

            val response = api.login(LoginRequest("u@x.com", "secret"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/auth/login", request.path)
            assertTrue(request.body.readUtf8().contains("\"email\":\"u@x.com\""))
            assertEquals("a", response.accessToken)
            assertEquals("u2", response.user.id)
        }

    @Test
    fun `refresh sends camelCase refreshToken and decodes only a new accessToken`() =
        runBlocking {
            enqueueJson(200, """{"accessToken":"a2"}""")

            val response = api.refresh(RefreshRequest(refreshToken = "old-token"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/auth/refresh", request.path)
            assertTrue(request.body.readUtf8().contains("\"refreshToken\":\"old-token\""))
            assertEquals("a2", response.accessToken)
        }

    @Test
    fun `me is a GET and decodes the wrapped user`() =
        runBlocking {
            enqueueJson(
                200,
                """{"user":{"id":"u1","email":"u@x.com","createdAt":"2026-03-03T00:00:00Z"}}""",
            )

            val response = api.me()

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/auth/me", request.path)
            assertEquals("u1", response.user.id)
            assertEquals("u@x.com", response.user.email)
            assertEquals("2026-03-03T00:00:00Z", response.user.createdAt)
        }

    @Test
    fun `me does not transmit an Authorization header until an interceptor is wired`() =
        runBlocking {
            // Documents current behaviour: the API declares no @Header and no auth interceptor is
            // installed in IntegrationsModule yet, so no Authorization header is sent.
            enqueueJson(
                200,
                """{"user":{"id":"u1","email":"u@x.com","createdAt":"2026-03-03T00:00:00Z"}}""",
            )
            api.me()
            assertNull(server.takeRequest().getHeader("Authorization"))
        }

    // --------------------------------------------------------------------------------- Integrations

    @Test
    fun `listIntegrations decodes a bare provider array`() =
        runBlocking {
            enqueueJson(
                200,
                """[{"provider":"google","connected":true},""" +
                    """{"provider":"notion","connected":false},""" +
                    """{"provider":"slack","connected":false},""" +
                    """{"provider":"salesforce","connected":true}]""",
            )

            val response = api.listIntegrations()

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/integrations", request.path)
            assertEquals(4, response.size)
            assertEquals("google", response[0].provider)
            assertTrue(response[0].connected)
            assertEquals("notion", response[1].provider)
            assertTrue(!response[1].connected)
        }

    @Test
    fun `connectIntegration is a GET decoding authorizeUrl and state`() =
        runBlocking {
            enqueueJson(
                200,
                """{"authorizeUrl":"https://notion.so/oauth","state":"xyz"}""",
            )

            val response = api.connectIntegration("notion")

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/integrations/notion/connect", request.path)
            assertEquals("https://notion.so/oauth", response.authorizeUrl)
            assertEquals("xyz", response.state)
        }

    @Test
    fun `exportToIntegration posts recording summary transcript and decodes the export response`() =
        runBlocking {
            enqueueJson(
                200,
                """{"externalId":"ext-1","url":"https://notion.so/page","status":"exported"}""",
            )

            val response =
                api.exportToIntegration(
                    "notion",
                    ExportRequest(
                        recording =
                            RecordingDto(
                                id = "rec-1",
                                title = "My note",
                                createdAt = "2026-01-01T00:00:00Z",
                                durationSec = 12.5,
                                source = "mic",
                                status = "ready",
                            ),
                        summary =
                            SummaryDto(
                                text = "## Summary\n- point",
                                actionItems =
                                    listOf(ActionItemDto(id = "a1", text = "Follow up", done = false)),
                            ),
                        transcript =
                            TranscriptDto(
                                text = "hello world",
                                segments =
                                    listOf(TranscriptSegmentDto(startSec = 0.0, endSec = 1.5, text = "hi")),
                                language = "en",
                            ),
                    ),
                )

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/integrations/notion/export", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"recording\""))
            assertTrue(body.contains("\"id\":\"rec-1\""))
            assertTrue(body.contains("\"source\":\"mic\""))
            assertTrue(body.contains("\"summary\""))
            assertTrue(body.contains("\"text\":\"## Summary\\n- point\""))
            assertTrue(body.contains("\"transcript\""))
            assertTrue(body.contains("\"language\":\"en\""))
            assertEquals("ext-1", response.externalId)
            assertEquals("https://notion.so/page", response.url)
            assertEquals("exported", response.status)
        }

    @Test
    fun `exportResponse tolerates an absent optional url`() =
        runBlocking {
            enqueueJson(200, """{"externalId":"ext-2","status":"queued"}""")
            val response =
                api.exportToIntegration(
                    "notion",
                    ExportRequest(
                        recording =
                            RecordingDto("r", "t", "2026-01-01T00:00:00Z", 1.0, "other", "ready"),
                        summary = SummaryDto(text = "md"),
                        transcript = TranscriptDto(text = ""),
                    ),
                )
            server.takeRequest()
            assertNull(response.url)
            assertEquals("ext-2", response.externalId)
            assertEquals("queued", response.status)
        }

    @Test
    fun `disconnectIntegration is a DELETE on the provider path and decodes disconnected`() =
        runBlocking {
            enqueueJson(200, """{"disconnected":true}""")

            val response = api.disconnectIntegration("notion")

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/v1/integrations/notion", request.path)
            assertTrue(response.disconnected)
        }

    @Test
    fun `there is no integration callback endpoint on the client`() {
        // The browser-redirect callback (v1/integrations/{provider}/callback) is server-side only
        // and was removed from the client. Guard against it being re-added.
        val methods = NotovaBackendApi::class.java.declaredMethods.map { it.name }
        assertTrue(
            "integrationCallback must not exist on the client",
            methods.none { it.contains("allback", ignoreCase = true) },
        )
    }

    // ----------------------------------------------------------------------------------------- Sync

    @Test
    fun `getRecordings is a GET with a since query decoding a bare metadata array`() =
        runBlocking {
            enqueueJson(
                200,
                """[{"id":"r1","title":"Sync","createdAt":"2026-01-01T00:00:00Z",""" +
                    """"durationSec":12.5,"source":"mic","status":"ready"}]""",
            )

            val response = api.getRecordings(since = "2026-01-01T00:00:00Z")

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/sync/recordings?since=2026-01-01T00%3A00%3A00Z", request.path)
            assertEquals(1, response.size)
            val dto = response.first()
            assertEquals("r1", dto.id)
            assertEquals("Sync", dto.title)
            assertEquals("2026-01-01T00:00:00Z", dto.createdAt)
            assertEquals(12.5, dto.durationSec, 0.0)
            assertEquals("mic", dto.source)
            assertEquals("ready", dto.status)
        }

    @Test
    fun `upsertRecording puts to the id path sending metadata and never audio or content`() =
        runBlocking {
            enqueueJson(200, """{"ok":true}""")

            val response =
                api.upsertRecording(
                    id = "r1",
                    body =
                        SyncRecordingUpsertRequest(
                            title = "Sync",
                            createdAt = "2026-01-01T00:00:00Z",
                            durationSec = 5.0,
                            source = "mic",
                            status = "ready",
                        ),
                )

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/v1/sync/recordings/r1", request.path)
            assertTrue(response.ok)
            val body = request.body.readUtf8()
            // Metadata fields present...
            assertTrue(body.contains("\"title\":\"Sync\""))
            assertTrue(body.contains("\"durationSec\":5.0"))
            assertTrue(body.contains("\"source\":\"mic\""))
            assertTrue(body.contains("\"status\":\"ready\""))
            // ...the id lives in the path, never the body...
            assertTrue("body must not carry an id field", !body.contains("\"id\""))
            // ...and crucially NO audio bytes, transcript text, or summary content.
            assertTrue(!body.contains("audio"))
            assertTrue(!body.contains("transcript"))
            assertTrue(!body.contains("fullText"))
            assertTrue(!body.contains("summary"))
            assertTrue(!body.contains("contentMarkdown"))
            assertTrue(!body.contains("base64"))
        }

    // -------------------------------------------------------------------------------------- Billing

    @Test
    fun `getSubscription decodes tier and renewsAt`() =
        runBlocking {
            enqueueJson(
                200,
                """{"tier":"pro","renewsAt":"2026-12-31T00:00:00Z"}""",
            )

            val response = api.getSubscription()

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/billing/subscription", request.path)
            assertEquals("pro", response.tier)
            assertEquals("2026-12-31T00:00:00Z", response.renewsAt)
        }

    @Test
    fun `getSubscription tolerates a missing renewsAt`() =
        runBlocking {
            enqueueJson(200, """{"tier":"free"}""")
            assertNull(api.getSubscription().renewsAt)
        }

    @Test
    fun `checkout posts the plan and decodes the checkout url`() =
        runBlocking {
            enqueueJson(200, """{"checkoutUrl":"https://pay.notova.app/abc"}""")

            val response = api.checkout(CheckoutRequest(plan = "pro_monthly"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/billing/checkout", request.path)
            assertTrue(request.body.readUtf8().contains("\"plan\":\"pro_monthly\""))
            assertEquals("https://pay.notova.app/abc", response.checkoutUrl)
        }

    // -------------------------------------------------------------------------------- Error handling

    @Test
    fun `400 surfaces as an HttpException with the code`() =
        runBlocking {
            enqueueJson(400, """{"error":{"code":"bad_request","message":"bad"}}""")
            val ex =
                try {
                    api.login(LoginRequest("a", "b"))
                    null
                } catch (e: HttpException) {
                    e
                }
            assertEquals(400, ex?.code())
        }

    @Test
    fun `401 surfaces as an HttpException with the code`() =
        runBlocking {
            enqueueJson(401, """{"error":{"code":"unauthorized","message":"no"}}""")
            val ex =
                try {
                    api.me()
                    null
                } catch (e: HttpException) {
                    e
                }
            assertEquals(401, ex?.code())
        }

    @Test
    fun `500 surfaces as an HttpException with the code`() =
        runBlocking {
            enqueueJson(500, """{"error":{"code":"server_error","message":"boom"}}""")
            val ex =
                try {
                    api.getSubscription()
                    null
                } catch (e: HttpException) {
                    e
                }
            assertEquals(500, ex?.code())
        }

    @Test
    fun `malformed JSON on a 200 surfaces as an exception`() =
        runBlocking {
            enqueueJson(200, """{ this is not valid json """)
            var caught: Exception? = null
            try {
                api.me()
            } catch (e: Exception) {
                caught = e
            }
            assertNotNull("expected decoding to fail on malformed JSON", caught)
        }
}
