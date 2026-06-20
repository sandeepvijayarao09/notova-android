package com.notova.integrations.provider

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notova.core.model.ActionItem
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import com.notova.integrations.api.NotovaBackendApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.time.Instant

/**
 * Drives [ExportRepository] against a [MockWebServer]: it maps on-device models onto the `/v1`
 * contract (ms→sec offsets, ISO-8601 dates, lowercase source/status), posts to the right path, and
 * maps the response/error onto [ExportResult]. No raw audio is ever sent.
 */
class ExportRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ExportRepository

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
        repository = ExportRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun recording() =
        Recording(
            id = "rec-1",
            title = "Quarterly review",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            durationSec = 12.5,
            source = RecordingSource.MIC,
            localAudioPath = "/audio/rec-1.m4a",
            status = RecordingStatus.READY,
        )

    private fun summary() =
        Summary(
            recordingId = "rec-1",
            style = "concise",
            contentMarkdown = "## Summary\n- point",
            actionItems = listOf(ActionItem(id = "a1", text = "Follow up", done = false)),
            model = "stub",
            generatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    private fun transcript() =
        Transcript(
            recordingId = "rec-1",
            language = "en",
            fullText = "hello world",
            segments = listOf(TranscriptSegment(startMs = 0, endMs = 1500, text = "hi")),
        )

    @Test
    fun `export posts the mapped contract body and decodes a success`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"externalId":"ext-1","url":"https://notion.so/page","status":"exported"}""",
                ),
            )

            val result = repository.export("notion", recording(), summary(), transcript())

            assertTrue(result is ExportResult.Success)
            val success = result as ExportResult.Success
            assertEquals("ext-1", success.externalId)
            assertEquals("https://notion.so/page", success.url)
            assertEquals("exported", success.status)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/integrations/notion/export", request.path)
            val body = request.body.readUtf8()
            // Mapped recording metadata (lowercase source/status, ISO date).
            assertTrue(body.contains("\"id\":\"rec-1\""))
            assertTrue(body.contains("\"source\":\"mic\""))
            assertTrue(body.contains("\"status\":\"ready\""))
            assertTrue(body.contains("\"createdAt\":\"2026-01-01T00:00:00Z\""))
            // Segment offsets converted ms -> sec (1500ms -> 1.5s).
            assertTrue(body.contains("\"endSec\":1.5"))
            assertTrue(body.contains("\"text\":\"hello world\""))
            // No raw audio path leaks into the export body.
            assertTrue(!body.contains("localAudioPath"))
            assertTrue(!body.contains("rec-1.m4a"))
        }

    @Test
    fun `export tolerates a missing url on success`() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("""{"externalId":"ext-2","status":"queued"}"""),
            )

            val result = repository.export("todoist", recording(), summary(), transcript())

            assertTrue(result is ExportResult.Success)
            val success = result as ExportResult.Success
            assertNull(success.url)
            assertEquals("queued", success.status)
        }

    @Test
    fun `a 404 maps to a connect-first failure`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not_connected"}"""))

            val result = repository.export("notion", recording(), summary(), transcript())

            assertTrue(result is ExportResult.Failure)
            assertTrue((result as ExportResult.Failure).message.contains("connect", ignoreCase = true))
        }

    @Test
    fun `a server error maps to a try-again failure`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

            val result = repository.export("notion", recording(), summary(), transcript())

            assertTrue(result is ExportResult.Failure)
            assertTrue((result as ExportResult.Failure).message.contains("500"))
        }
}
