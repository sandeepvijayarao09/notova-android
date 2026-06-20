package com.notova.app.ui.export

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notova.app.ui.RealMainDispatcherRule
import com.notova.app.ui.waitForState
import com.notova.core.model.ActionItem
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import com.notova.integrations.api.NotovaBackendApi
import com.notova.integrations.provider.ExportRepository
import com.notova.integrations.provider.IntegrationsRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import java.time.Instant

/**
 * Coverage for [ExportViewModel]: opening the picker filters to connected providers; export calls
 * the backend with the on-device note and maps the result/error. Repositories are wired to a
 * [MockWebServer]; the recording/summary come from a fake [FakeRecordingRepository].
 *
 * Uses a REAL Main dispatcher + StateFlow polling because the suspend Retrofit calls resume on
 * OkHttp's background threads (virtual-time advancement can't await them).
 */
class ExportViewModelTest {
    @get:Rule
    val mainDispatcherRule = RealMainDispatcherRule()

    private lateinit var server: MockWebServer
    private lateinit var recordings: FakeRecordingRepository
    private lateinit var viewModel: ExportViewModel

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
        recordings = FakeRecordingRepository()
        recordings.seedRecording(
            Recording(
                id = "rec-1",
                title = "Quarterly review",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                durationSec = 12.5,
                source = RecordingSource.MIC,
                localAudioPath = "/audio/rec-1.m4a",
                status = RecordingStatus.READY,
            ),
        )
        recordings.seedSummary(
            Summary(
                recordingId = "rec-1",
                style = "concise",
                contentMarkdown = "## Summary\n- point",
                actionItems = listOf(ActionItem(id = "a1", text = "Follow up", done = false)),
                model = "stub",
                generatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )
        viewModel = ExportViewModel(recordings, IntegrationsRepository(api), ExportRepository(api))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(
        code: Int,
        body: String,
    ) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test
    fun `opening the picker filters to connected providers`() {
        enqueue(200, """[{"provider":"notion","connected":true},{"provider":"todoist","connected":false}]""")

        viewModel.openPicker()
        val state = waitForState(supplier = { viewModel.uiState.value }) { !it.loadingProviders }

        assertTrue(state.pickerOpen)
        assertEquals(1, state.connectedProviders.size)
        assertEquals("notion", state.connectedProviders.first().provider)
    }

    @Test
    fun `picker shows a message when no providers are connected`() {
        enqueue(200, """[{"provider":"notion","connected":false}]""")

        viewModel.openPicker()
        val state = waitForState(supplier = { viewModel.uiState.value }) { !it.loadingProviders }

        assertTrue(state.connectedProviders.isEmpty())
        assertTrue(state.pickerMessage?.contains("Connect one") == true)
    }

    @Test
    fun `export posts the note and maps a success outcome`() {
        enqueue(200, """{"externalId":"ext-1","url":"https://notion.so/page","status":"exported"}""")

        viewModel.export("rec-1", "notion")
        val state = waitForState(supplier = { viewModel.uiState.value }) { it.outcome != null }

        val outcome = state.outcome
        assertTrue(outcome is ExportOutcome.Success)
        val success = outcome as ExportOutcome.Success
        assertEquals("notion", success.provider)
        assertEquals("ext-1", success.externalId)
        assertEquals("https://notion.so/page", success.url)
        assertEquals(false, state.pickerOpen)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/integrations/notion/export", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"source\":\"mic\""))
        assertTrue(body.contains("\"text\":\"## Summary\\n- point\""))
        // No raw audio path leaks.
        assertTrue(!body.contains("rec-1.m4a"))
    }

    @Test
    fun `export maps a backend error to an error outcome`() {
        enqueue(500, """{"error":"boom"}""")

        viewModel.export("rec-1", "notion")
        val state = waitForState(supplier = { viewModel.uiState.value }) { it.outcome != null }

        val outcome = state.outcome
        assertTrue(outcome is ExportOutcome.Error)
        assertTrue((outcome as ExportOutcome.Error).message.contains("500"))
    }

    @Test
    fun `export of a missing note yields a not-ready error without a network call`() {
        viewModel.export("does-not-exist", "notion")
        val state = waitForState(supplier = { viewModel.uiState.value }) { it.outcome != null }

        val outcome = state.outcome
        assertTrue(outcome is ExportOutcome.Error)
        assertTrue((outcome as ExportOutcome.Error).message.contains("isn't ready"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `consumeOutcome clears the outcome`() {
        enqueue(500, """{"error":"boom"}""")
        viewModel.export("rec-1", "notion")
        waitForState(supplier = { viewModel.uiState.value }) { it.outcome != null }

        viewModel.consumeOutcome()

        assertEquals(null, viewModel.uiState.value.outcome)
    }
}
