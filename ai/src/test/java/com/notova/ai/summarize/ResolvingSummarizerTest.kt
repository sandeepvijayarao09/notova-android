package com.notova.ai.summarize

import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Selection logic of [ResolvingSummarizer] across availability permutations using fake engines:
 * the first available engine in priority order handles the request, the choice is recorded, and a
 * blowing-up availability check is treated as "unavailable" (the resolver falls through).
 */
class ResolvingSummarizerTest {
    private fun transcript() =
        Transcript(
            recordingId = "rec-1",
            language = "en",
            fullText = "hello world",
            segments = listOf(TranscriptSegment(0, 1_000, "hello world")),
        )

    private fun resolver(vararg engines: SummarizerEngine) = ResolvingSummarizer(engines.toList())

    @Test
    fun `picks the first engine when it is available`() =
        runTest {
            val gemma = FakeSummarizerEngine("gemma", available = true)
            val nano = FakeSummarizerEngine("nano", available = true)
            val stub = FakeSummarizerEngine("stub", available = true)
            val resolver = resolver(gemma, nano, stub)

            val summary = resolver.summarize(transcript(), "concise")

            assertEquals("gemma", summary.model)
            assertEquals(1, gemma.summarizeCount)
            assertEquals(0, nano.summarizeCount)
            assertEquals(0, stub.summarizeCount)
        }

    @Test
    fun `falls through to the second engine when the first is unavailable`() =
        runTest {
            val gemma = FakeSummarizerEngine("gemma", available = false)
            val nano = FakeSummarizerEngine("nano", available = true)
            val stub = FakeSummarizerEngine("stub", available = true)
            val resolver = resolver(gemma, nano, stub)

            val summary = resolver.summarize(transcript(), "concise")

            assertEquals("nano", summary.model)
            assertEquals(0, gemma.summarizeCount)
            assertEquals(1, nano.summarizeCount)
        }

    @Test
    fun `falls all the way through to the stub when nothing else is available`() =
        runTest {
            val gemma = FakeSummarizerEngine("gemma", available = false)
            val nano = FakeSummarizerEngine("nano", available = false)
            val stub = FakeSummarizerEngine("stub", available = true)
            val resolver = resolver(gemma, nano, stub)

            val summary = resolver.summarize(transcript(), "concise")

            assertEquals("stub", summary.model)
            assertEquals(1, stub.summarizeCount)
        }

    @Test
    fun `records the active engine name after summarizing`() =
        runTest {
            val resolver =
                resolver(
                    FakeSummarizerEngine("gemma", available = false),
                    FakeSummarizerEngine("nano", available = true),
                    FakeSummarizerEngine("stub", available = true),
                )

            assertNull(resolver.activeEngine.value)
            resolver.summarize(transcript(), "concise")
            assertEquals("nano", resolver.activeEngine.value)
        }

    @Test
    fun `resolve does not invoke summarize`() =
        runTest {
            val nano = FakeSummarizerEngine("nano", available = true)
            val resolver = resolver(FakeSummarizerEngine("gemma", available = false), nano)

            val chosen = resolver.resolve()

            assertEquals("nano", chosen.engineName)
            assertEquals(0, nano.summarizeCount)
        }

    @Test
    fun `an availability check that throws is treated as unavailable`() =
        runTest {
            val gemma = FakeSummarizerEngine("gemma", available = true, availabilityThrows = true)
            val stub = FakeSummarizerEngine("stub", available = true)
            val resolver = resolver(gemma, stub)

            val summary = resolver.summarize(transcript(), "concise")

            assertEquals("stub", summary.model)
            assertEquals(0, gemma.summarizeCount)
        }

    @Test
    fun `falls back to the last engine when none report available`() =
        runTest {
            val gemma = FakeSummarizerEngine("gemma", available = false)
            val stub = FakeSummarizerEngine("stub", available = false)
            val resolver = resolver(gemma, stub)

            val summary = resolver.summarize(transcript(), "concise")

            // No engine is available, so the resolver defends by using the last (fallback) engine.
            assertEquals("stub", summary.model)
        }

    @Test
    fun `re-resolves on every call so a newly available engine wins next time`() =
        runTest {
            val gemma = FakeSummarizerEngine("gemma", available = false)
            val stub = FakeSummarizerEngine("stub", available = true)
            val resolver = resolver(gemma, stub)

            assertEquals("stub", resolver.summarize(transcript(), "concise").model)
            gemma.setAvailable(true)
            assertEquals("gemma", resolver.summarize(transcript(), "concise").model)
        }
}
