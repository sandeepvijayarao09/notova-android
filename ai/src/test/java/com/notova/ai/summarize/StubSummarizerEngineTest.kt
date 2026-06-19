package com.notova.ai.summarize

import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import com.notova.core.summarize.StubSummarizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stub engine adapter is always available and forwards to the core [StubSummarizer]. */
class StubSummarizerEngineTest {
    private fun transcript() =
        Transcript("rec-1", "en", "We should follow up.", listOf(TranscriptSegment(0, 1, "We should follow up.")))

    @Test
    fun `is always available`() =
        runTest {
            assertTrue(StubSummarizerEngine(StubSummarizer()).isAvailable())
        }

    @Test
    fun `has a readable engine name`() {
        assertEquals("Built-in (template)", StubSummarizerEngine(StubSummarizer()).engineName)
    }

    @Test
    fun `delegates to the core stub summarizer`() =
        runTest {
            val summary = StubSummarizerEngine(StubSummarizer()).summarize(transcript(), "concise")
            assertEquals("rec-1", summary.recordingId)
            assertEquals("stub-summarizer-v0", summary.model)
            assertTrue(summary.contentMarkdown.contains("Summary"))
        }
}
