package com.notova.core.summarize

import com.notova.core.model.ActionItem
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import java.time.Instant
import javax.inject.Inject

/**
 * Basic extractive [Summarizer], used when no on-device AI model is available. It never invents
 * content: it quotes the start of the real transcript and lists transcript sentences that contain
 * action-ish keywords, and it says plainly that no AI model produced it.
 */
class StubSummarizer
    @Inject
    constructor() : Summarizer {
        override suspend fun summarize(
            transcript: Transcript,
            style: String,
        ): Summary {
            val actionItems = extractActionItems(transcript.fullText)
            val markdown =
                buildString {
                    appendLine("## Summary ($style)")
                    appendLine()
                    appendLine(
                        "_Basic summary: no on-device AI model was available, so this is the start of " +
                            "the transcript plus sentences that look like action items._",
                    )
                    appendLine()
                    appendLine("**Transcript preview:** ${transcript.fullText.take(PREVIEW_CHARS)}")
                    if (actionItems.isNotEmpty()) {
                        appendLine()
                        appendLine("### Action items")
                        actionItems.forEach { appendLine("- [ ] ${it.text}") }
                    }
                }.trimEnd()

            return Summary(
                recordingId = transcript.recordingId,
                style = style,
                contentMarkdown = markdown,
                actionItems = actionItems,
                model = MODEL_NAME,
                generatedAt = Instant.now(),
            )
        }

        private fun extractActionItems(text: String): List<ActionItem> =
            text.split('.', '!', '?')
                .map { it.trim() }
                .filter { sentence ->
                    ACTION_KEYWORDS.any { sentence.contains(it, ignoreCase = true) }
                }
                .map { ActionItem(text = it) }

        private companion object {
            const val PREVIEW_CHARS = 160
            const val MODEL_NAME = "basic-extractive-v1"
            val ACTION_KEYWORDS = listOf("todo", "follow up", "need to", "should", "action", "remember to")
        }
    }
