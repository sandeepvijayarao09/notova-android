package com.notova.core.summarize

import com.notova.core.model.ActionItem
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import kotlinx.coroutines.delay
import java.time.Instant
import javax.inject.Inject

/**
 * Placeholder [Summarizer]. Emits templated markdown plus naive action items extracted from
 * sentences containing action-ish keywords. Replace with a Gemma 3n E4B implementation later.
 */
class StubSummarizer
    @Inject
    constructor() : Summarizer {
        override suspend fun summarize(
            transcript: Transcript,
            style: String,
        ): Summary {
            delay(SIMULATED_LATENCY_MS)

            val actionItems = extractActionItems(transcript.fullText)
            val markdown =
                buildString {
                    appendLine("## Summary ($style)")
                    appendLine()
                    appendLine(
                        "This is a placeholder summary produced by StubSummarizer for recording " +
                            "`${transcript.recordingId}`.",
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
            const val SIMULATED_LATENCY_MS = 300L
            const val PREVIEW_CHARS = 160
            const val MODEL_NAME = "stub-summarizer-v0"
            val ACTION_KEYWORDS = listOf("todo", "follow up", "need to", "should", "action", "remember to")
        }
    }
