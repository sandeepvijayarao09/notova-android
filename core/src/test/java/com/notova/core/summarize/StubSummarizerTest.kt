package com.notova.core.summarize

import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage of [StubSummarizer]: markdown shape, model/style metadata, determinism, and the naive
 * action-item extractor across many phrasings. The extractor flags any sentence containing one of
 * its keywords (todo / follow up / need to / should / action / remember to), case-insensitively,
 * splitting on `.`/`!`/`?`. These tests pin both the positive matches and the negatives that must
 * NOT be flagged given that keyword set.
 */
class StubSummarizerTest {
    private fun transcriptOf(text: String): Transcript =
        Transcript(
            recordingId = "rec-1",
            language = "en",
            fullText = text,
            segments = listOf(TranscriptSegment(0, 1_000, text)),
        )

    private suspend fun summarize(
        text: String,
        style: String = "concise",
    ) = StubSummarizer().summarize(transcriptOf(text), style)

    // ---- Output shape & metadata ----

    @Test
    fun `markdown opens with a styled summary heading`() =
        runTest {
            val summary = summarize("Anything here.", style = "detailed")
            assertTrue(summary.contentMarkdown.startsWith("## Summary (detailed)"))
        }

    @Test
    fun `markdown embeds the recording id`() =
        runTest {
            val summary = summarize("Body text.")
            assertTrue(summary.contentMarkdown.contains("`rec-1`"))
        }

    @Test
    fun `markdown includes a transcript preview`() =
        runTest {
            val summary = summarize("This sentence should appear in the preview.")
            assertTrue(summary.contentMarkdown.contains("**Transcript preview:**"))
            assertTrue(summary.contentMarkdown.contains("This sentence"))
        }

    @Test
    fun `preview is truncated to 160 characters of the transcript`() =
        runTest {
            // 300 'a' chars, no action keywords so the action section stays out of the way.
            val longText = "a".repeat(300)
            val summary = summarize(longText)
            val previewLine =
                summary.contentMarkdown.lineSequence().first { it.startsWith("**Transcript preview:**") }
            val preview = previewLine.removePrefix("**Transcript preview:** ")
            assertEquals(160, preview.length)
        }

    @Test
    fun `summary carries the model name and style through`() =
        runTest {
            val summary = summarize("x.", style = "bullet")
            assertEquals("stub-summarizer-v0", summary.model)
            assertEquals("bullet", summary.style)
            assertEquals("rec-1", summary.recordingId)
        }

    @Test
    fun `markdown is trimmed of trailing whitespace`() =
        runTest {
            val summary = summarize("Nothing actionable here.")
            assertEquals(summary.contentMarkdown.trimEnd(), summary.contentMarkdown)
        }

    @Test
    fun `output is deterministic for identical input`() =
        runTest {
            val a = summarize("We should ship this.")
            val b = summarize("We should ship this.")
            assertEquals(a.contentMarkdown, b.contentMarkdown)
            assertEquals(a.actionItems.map { it.text }, b.actionItems.map { it.text })
        }

    // ---- Action-item extraction: positive matches ----

    @Test
    fun `flags a sentence containing 'should'`() =
        runTest {
            val items = summarize("We should send the report.").actionItems
            assertEquals(1, items.size)
            assertEquals("We should send the report", items[0].text)
        }

    @Test
    fun `flags a sentence containing 'need to'`() =
        runTest {
            val items = summarize("I need to call the vendor.").actionItems
            assertEquals(1, items.size)
            assertTrue(items[0].text.contains("need to"))
        }

    @Test
    fun `flags a sentence containing 'follow up'`() =
        runTest {
            val items = summarize("Please follow up with finance.").actionItems
            assertEquals(1, items.size)
        }

    @Test
    fun `flags a sentence containing 'remember to'`() =
        runTest {
            val items = summarize("Remember to book the room.").actionItems
            assertEquals(1, items.size)
        }

    @Test
    fun `flags a sentence containing 'todo' case-insensitively`() =
        runTest {
            assertEquals(1, summarize("TODO update the slides.").actionItems.size)
            assertEquals(1, summarize("todo update the slides.").actionItems.size)
            assertEquals(1, summarize("ToDo update the slides.").actionItems.size)
        }

    @Test
    fun `flags a sentence containing the word 'action'`() =
        runTest {
            val items = summarize("The action item is owned by Dana.").actionItems
            assertEquals(1, items.size)
        }

    @Test
    fun `extracts multiple action items from multiple sentences`() =
        runTest {
            val text = "We should finalize the budget. I need to email the client. The weather is nice."
            val items = summarize(text).actionItems
            assertEquals(2, items.size)
            assertTrue(items.any { it.text.contains("finalize the budget") })
            assertTrue(items.any { it.text.contains("email the client") })
            assertTrue(items.none { it.text.contains("weather") })
        }

    @Test
    fun `splits on exclamation and question marks too`() =
        runTest {
            val items = summarize("Should we ship? We should ship! All done.").actionItems
            // Both the question and the exclamation contain "should".
            assertEquals(2, items.size)
        }

    @Test
    fun `extracted action item text is trimmed`() =
        runTest {
            val items = summarize("   We should trim this.   ").actionItems
            assertEquals("We should trim this", items[0].text)
        }

    @Test
    fun `action items appear as markdown checkboxes`() =
        runTest {
            val summary = summarize("We should ship the build.")
            assertTrue(summary.contentMarkdown.contains("### Action items"))
            assertTrue(summary.contentMarkdown.contains("- [ ] We should ship the build"))
        }

    @Test
    fun `each extracted action item starts undone with a fresh id`() =
        runTest {
            val items = summarize("We should A. We need to B.").actionItems
            assertEquals(2, items.size)
            assertTrue(items.all { !it.done })
            assertEquals(items.size, items.map { it.id }.distinct().size)
        }

    @Test
    fun `keyword matching is substring-based across word boundaries`() =
        runTest {
            // "shoulder" contains "should" — documents the naive substring behaviour.
            val items = summarize("He tapped my shoulder.").actionItems
            assertEquals(1, items.size)
        }

    // ---- Action-item extraction: negatives ----

    @Test
    fun `no action items and no action section for plain prose`() =
        runTest {
            val summary = summarize("The sky is blue. Birds were singing. It was calm.")
            assertTrue(summary.actionItems.isEmpty())
            assertFalse(summary.contentMarkdown.contains("### Action items"))
        }

    @Test
    fun `does not flag 'let us' style imperatives without a keyword`() =
        runTest {
            // "let's" is not in the keyword set, so this must NOT be flagged.
            val items = summarize("Let's grab lunch.").actionItems
            assertTrue(items.isEmpty())
        }

    @Test
    fun `does not flag a bare imperative without a keyword`() =
        runTest {
            val items = summarize("Email the client.").actionItems
            assertTrue(items.isEmpty())
        }

    @Test
    fun `does not flag bullet markers on their own`() =
        runTest {
            val items = summarize("- buy milk - walk the dog - water plants").actionItems
            assertTrue(items.isEmpty())
        }

    @Test
    fun `empty transcript yields no action items`() =
        runTest {
            val items = summarize("").actionItems
            assertTrue(items.isEmpty())
        }

    @Test
    fun `whitespace-only transcript yields no action items`() =
        runTest {
            val items = summarize("   \n\t  ").actionItems
            assertTrue(items.isEmpty())
        }

    @Test
    fun `mixed bullet list flags only the keyword-bearing lines`() =
        runTest {
            // No sentence terminators, so the whole blob is one sentence containing "need to".
            val items = summarize("Tasks: buy milk, we need to call Sam, water plants").actionItems
            assertEquals(1, items.size)
            assertTrue(items[0].text.contains("need to call Sam"))
        }
}
