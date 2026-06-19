package com.notova.ai.summarize

import com.notova.ai.model.ModelStore
import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Availability-guard + summary-mapping logic for [LocalGemmaSummarizer], driven with a fake
 * [LlmEngine] and a temp-dir [ModelStore] so no `.task` model and no native engine are needed.
 */
class LocalGemmaSummarizerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: ModelStore

    @Before
    fun setUp() {
        store = ModelStore(File(temp.root, "models"))
    }

    private fun transcript(text: String = "We met and decided things.") =
        Transcript(
            recordingId = "rec-1",
            language = "en",
            fullText = text,
            segments = listOf(TranscriptSegment(0, 1_000, text)),
        )

    private suspend fun installGemma() = store.import("gemma.task", ByteArrayInputStream("weights".toByteArray()))

    @Test
    fun `unavailable when no Gemma model is installed`() =
        runTest {
            val summarizer = LocalGemmaSummarizer(store, FakeLlmEngine())
            assertFalse(summarizer.isAvailable())
        }

    @Test
    fun `unavailable when a model is present but the native engine fails to load`() =
        runTest {
            installGemma()
            val summarizer = LocalGemmaSummarizer(store, FakeLlmEngine(loadSucceeds = false))
            assertFalse(summarizer.isAvailable())
        }

    @Test
    fun `available when a model is present and the engine loads`() =
        runTest {
            installGemma()
            val summarizer = LocalGemmaSummarizer(store, FakeLlmEngine(loadSucceeds = true))
            assertTrue(summarizer.isAvailable())
        }

    @Test
    fun `summarize loads the installed model path before generating`() =
        runTest {
            val installed = installGemma()
            val llm = FakeLlmEngine()
            val summarizer = LocalGemmaSummarizer(store, llm)

            summarizer.summarize(transcript(), "concise")

            assertEquals(installed.path, llm.loadedPath)
        }

    @Test
    fun `summarize includes the transcript text and style in the prompt`() =
        runTest {
            installGemma()
            val llm = FakeLlmEngine()
            val summarizer = LocalGemmaSummarizer(store, llm)

            summarizer.summarize(transcript("the exact transcript"), "detailed")

            assertTrue(llm.lastPrompt!!.contains("the exact transcript"))
            assertTrue(llm.lastPrompt!!.contains("detailed"))
        }

    @Test
    fun `summary carries the recording id style and a model name derived from the file`() =
        runTest {
            installGemma()
            val summarizer = LocalGemmaSummarizer(store, FakeLlmEngine())

            val summary = summarizer.summarize(transcript(), "bullet")

            assertEquals("rec-1", summary.recordingId)
            assertEquals("bullet", summary.style)
            assertEquals("gemma-mediapipe:gemma.task", summary.model)
        }

    @Test
    fun `action items are parsed out of the model's checkbox lines`() =
        runTest {
            installGemma()
            val response =
                "## Summary\nWe synced.\n\n## Action items\n- [ ] Email finance\n- [x] Book the room"
            val summarizer = LocalGemmaSummarizer(store, FakeLlmEngine(response = response))

            val summary = summarizer.summarize(transcript(), "concise")

            assertEquals(2, summary.actionItems.size)
            assertEquals("Email finance", summary.actionItems[0].text)
            assertFalse(summary.actionItems[0].done)
            assertEquals("Book the room", summary.actionItems[1].text)
            assertTrue(summary.actionItems[1].done)
        }

    @Test
    fun `no action items when the model emits none`() =
        runTest {
            installGemma()
            val summarizer = LocalGemmaSummarizer(store, FakeLlmEngine(response = "Just a plain summary."))
            assertTrue(summarizer.summarize(transcript(), "concise").actionItems.isEmpty())
        }
}
