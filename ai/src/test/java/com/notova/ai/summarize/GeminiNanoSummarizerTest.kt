package com.notova.ai.summarize

import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Availability gating + mapping for [GeminiNanoSummarizer] via a fake [MlKitSummarizationEngine] —
 * no AICore / Gemini Nano required. Only [GenAiFeatureStatus.AVAILABLE] counts as available.
 */
class GeminiNanoSummarizerTest {
    private class FakeMlKit(
        private val status: GenAiFeatureStatus,
        private val summary: String = "## Summary\nNano said hi.\n\n## Action items\n- [ ] Ship it",
        private val downloadResult: Boolean = true,
    ) : MlKitSummarizationEngine {
        var summarizeCount = 0
            private set
        var downloadCount = 0
            private set

        override suspend fun checkFeatureStatus(): GenAiFeatureStatus = status

        override suspend fun ensureDownloaded(): Boolean {
            downloadCount++
            return downloadResult
        }

        override suspend fun summarize(text: String): String {
            summarizeCount++
            return summary
        }
    }

    private fun transcript() = Transcript("rec-1", "en", "hello", listOf(TranscriptSegment(0, 1, "hello")))

    @Test
    fun `available only when the feature status is AVAILABLE`() =
        runTest {
            assertTrue(GeminiNanoSummarizer(FakeMlKit(GenAiFeatureStatus.AVAILABLE)).isAvailable())
            assertFalse(GeminiNanoSummarizer(FakeMlKit(GenAiFeatureStatus.DOWNLOADABLE)).isAvailable())
            assertFalse(GeminiNanoSummarizer(FakeMlKit(GenAiFeatureStatus.DOWNLOADING)).isAvailable())
            assertFalse(GeminiNanoSummarizer(FakeMlKit(GenAiFeatureStatus.UNAVAILABLE)).isAvailable())
        }

    @Test
    fun `featureStatus surfaces the underlying status`() =
        runTest {
            val nano = GeminiNanoSummarizer(FakeMlKit(GenAiFeatureStatus.DOWNLOADABLE))
            assertEquals(GenAiFeatureStatus.DOWNLOADABLE, nano.featureStatus())
        }

    @Test
    fun `ensureFeatureDownloaded delegates to the engine download`() =
        runTest {
            val fake = FakeMlKit(GenAiFeatureStatus.DOWNLOADABLE, downloadResult = true)
            val nano = GeminiNanoSummarizer(fake)
            assertTrue(nano.ensureFeatureDownloaded())
            assertEquals(1, fake.downloadCount)
        }

    @Test
    fun `summarize maps the ML Kit summary into a Summary with the nano model name`() =
        runTest {
            val nano = GeminiNanoSummarizer(FakeMlKit(GenAiFeatureStatus.AVAILABLE))
            val summary = nano.summarize(transcript(), "concise")

            assertEquals("rec-1", summary.recordingId)
            assertEquals("concise", summary.style)
            assertEquals("gemini-nano-mlkit", summary.model)
            assertTrue(summary.contentMarkdown.contains("Nano said hi"))
        }

    @Test
    fun `summarize parses checkbox action items`() =
        runTest {
            val nano =
                GeminiNanoSummarizer(
                    FakeMlKit(
                        GenAiFeatureStatus.AVAILABLE,
                        summary = "## Action items\n- [ ] One\n- [x] Two",
                    ),
                )
            val items = nano.summarize(transcript(), "concise").actionItems
            assertEquals(2, items.size)
            assertFalse(items[0].done)
            assertTrue(items[1].done)
        }
}
