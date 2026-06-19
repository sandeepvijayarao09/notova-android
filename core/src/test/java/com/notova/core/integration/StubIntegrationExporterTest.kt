package com.notova.core.integration

import com.notova.core.model.IntegrationExportStatus
import com.notova.core.model.Summary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StubIntegrationExporterTest {
    private fun summary(recordingId: String = "rec-1"): Summary =
        Summary(
            recordingId = recordingId,
            style = "concise",
            contentMarkdown = "## Summary",
            actionItems = emptyList(),
            model = "stub",
            generatedAt = Instant.ofEpochMilli(0),
        )

    @Test
    fun `supports notion todoist and google_tasks`() {
        val providers = StubIntegrationExporter().supportedProviders
        assertEquals(setOf("notion", "todoist", "google_tasks"), providers)
    }

    @Test
    fun `export returns PENDING with no external linkage for every supported provider`() =
        runTest {
            val exporter = StubIntegrationExporter()
            exporter.supportedProviders.forEach { provider ->
                val export = exporter.export(provider, summary())
                assertEquals(provider, export.provider)
                assertEquals("rec-1", export.recordingId)
                assertEquals(IntegrationExportStatus.PENDING, export.status)
                assertNull(export.externalId)
                assertNull(export.url)
            }
        }

    @Test
    fun `export echoes the recording id from the summary`() =
        runTest {
            val export = StubIntegrationExporter().export("notion", summary(recordingId = "abc-999"))
            assertEquals("abc-999", export.recordingId)
        }

    @Test
    fun `stub does not validate the provider so unknown providers still produce PENDING`() =
        runTest {
            // The stub is a pure no-op; provider validation lives in BackendIntegrationExporter.
            val export = StubIntegrationExporter().export("unknown", summary())
            assertEquals("unknown", export.provider)
            assertEquals(IntegrationExportStatus.PENDING, export.status)
        }

    @Test
    fun `supported providers set is non-empty`() {
        assertTrue(StubIntegrationExporter().supportedProviders.isNotEmpty())
    }
}
