package com.notova.integrations.export

import com.notova.core.model.ActionItem
import com.notova.core.model.IntegrationExportStatus
import com.notova.core.model.Summary
import com.notova.integrations.api.AuthResponse
import com.notova.integrations.api.CheckoutRequest
import com.notova.integrations.api.CheckoutResponse
import com.notova.integrations.api.ConnectResponse
import com.notova.integrations.api.DisconnectResponse
import com.notova.integrations.api.ExportRequest
import com.notova.integrations.api.ExportResponse
import com.notova.integrations.api.IntegrationDto
import com.notova.integrations.api.LoginRequest
import com.notova.integrations.api.MeResponse
import com.notova.integrations.api.NotovaBackendApi
import com.notova.integrations.api.RefreshRequest
import com.notova.integrations.api.RefreshResponse
import com.notova.integrations.api.RegisterRequest
import com.notova.integrations.api.SubscriptionResponse
import com.notova.integrations.api.SyncRecordingDto
import com.notova.integrations.api.SyncRecordingUpsertRequest
import com.notova.integrations.api.SyncUpsertResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class BackendIntegrationExporterTest {
    private fun summary(
        recordingId: String = "rec-1",
        actionItems: List<ActionItem> = emptyList(),
    ): Summary =
        Summary(
            recordingId = recordingId,
            style = "concise",
            contentMarkdown = "## Summary\n- point",
            actionItems = actionItems,
            model = "stub",
            generatedAt = Instant.ofEpochMilli(0),
        )

    /** Fake API capturing the export call and returning a configurable response or error. */
    private class FakeApi(
        private val response: ExportResponse? = null,
        private val error: Throwable? = null,
    ) : NotovaBackendApi {
        var lastProvider: String? = null
        var lastRequest: ExportRequest? = null

        override suspend fun exportToIntegration(
            provider: String,
            body: ExportRequest,
        ): ExportResponse {
            lastProvider = provider
            lastRequest = body
            error?.let { throw it }
            return response ?: error("no response configured")
        }

        // Unused endpoints for this exporter — not exercised here.
        override suspend fun register(body: RegisterRequest): AuthResponse = error("unused")

        override suspend fun login(body: LoginRequest): AuthResponse = error("unused")

        override suspend fun refresh(body: RefreshRequest): RefreshResponse = error("unused")

        override suspend fun me(): MeResponse = error("unused")

        override suspend fun listIntegrations(): List<IntegrationDto> = error("unused")

        override suspend fun connectIntegration(provider: String): ConnectResponse = error("unused")

        override suspend fun disconnectIntegration(provider: String): DisconnectResponse = error("unused")

        override suspend fun getRecordings(since: String?): List<SyncRecordingDto> = error("unused")

        override suspend fun upsertRecording(
            id: String,
            body: SyncRecordingUpsertRequest,
        ): SyncUpsertResponse = error("unused")

        override suspend fun getSubscription(): SubscriptionResponse = error("unused")

        override suspend fun checkout(body: CheckoutRequest): CheckoutResponse = error("unused")
    }

    @Test
    fun `supports notion todoist and google_tasks`() {
        assertEquals(
            setOf("notion", "todoist", "google_tasks"),
            BackendIntegrationExporter(FakeApi()).supportedProviders,
        )
    }

    @Test
    fun `rejects an unsupported provider before calling the api`() =
        runBlocking {
            val api = FakeApi()
            try {
                BackendIntegrationExporter(api).export("dropbox", summary())
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("Unsupported provider: dropbox"))
            }
            assertNull(api.lastProvider)
        }

    @Test
    fun `forwards the summary text and recording id to the api`() =
        runBlocking {
            val api = FakeApi(response = ExportResponse(externalId = "x", url = "u", status = "exported"))
            BackendIntegrationExporter(api).export("notion", summary("rec-77"))

            assertEquals("notion", api.lastProvider)
            assertEquals("rec-77", api.lastRequest?.recording?.id)
            assertEquals("## Summary\n- point", api.lastRequest?.summary?.text)
        }

    @Test
    fun `carries on-device action items into the summary dto`() =
        runBlocking {
            val api = FakeApi(response = ExportResponse(externalId = "x", status = "exported"))
            BackendIntegrationExporter(api).export(
                "notion",
                summary(actionItems = listOf(ActionItem(id = "a1", text = "Do it", done = true))),
            )

            val items = api.lastRequest?.summary?.actionItems
            assertEquals(1, items?.size)
            assertEquals("a1", items?.first()?.id)
            assertEquals("Do it", items?.first()?.text)
            assertEquals(true, items?.first()?.done)
        }

    @Test
    fun `maps exported status to DONE and carries external linkage`() =
        runBlocking {
            val api =
                FakeApi(response = ExportResponse(externalId = "ext-1", url = "https://x", status = "exported"))
            val result = BackendIntegrationExporter(api).export("notion", summary())

            assertEquals(IntegrationExportStatus.DONE, result.status)
            assertEquals("ext-1", result.externalId)
            assertEquals("https://x", result.url)
            assertEquals("notion", result.provider)
            assertEquals("rec-1", result.recordingId)
        }

    @Test
    fun `maps success synonyms to DONE`() =
        runBlocking {
            listOf("exported", "done", "ok", "success", "completed", "Exported", "DONE").forEach { status ->
                val api = FakeApi(response = ExportResponse(externalId = "e", status = status))
                val result = BackendIntegrationExporter(api).export("notion", summary())
                assertEquals("status=$status", IntegrationExportStatus.DONE, result.status)
            }
        }

    @Test
    fun `maps queued and pending synonyms to PENDING`() =
        runBlocking {
            listOf("queued", "pending", "processing", "QUEUED").forEach { status ->
                val api = FakeApi(response = ExportResponse(externalId = "e", status = status))
                val result = BackendIntegrationExporter(api).export("todoist", summary())
                assertEquals("status=$status", IntegrationExportStatus.PENDING, result.status)
            }
        }

    @Test
    fun `maps skipped and unknown status strings to FAILED`() =
        runBlocking {
            listOf("skipped", "rejected", "error", "weird", "").forEach { status ->
                val api = FakeApi(response = ExportResponse(externalId = "e", status = status))
                val result = BackendIntegrationExporter(api).export("notion", summary())
                assertEquals("status=$status", IntegrationExportStatus.FAILED, result.status)
            }
        }

    @Test
    fun `maps a thrown network error to FAILED with no external linkage`() =
        runBlocking {
            val api = FakeApi(error = RuntimeException("network down"))
            val result = BackendIntegrationExporter(api).export("notion", summary("rec-5"))

            assertEquals(IntegrationExportStatus.FAILED, result.status)
            assertEquals("rec-5", result.recordingId)
            assertEquals("notion", result.provider)
            assertNull(result.externalId)
            assertNull(result.url)
        }
}
