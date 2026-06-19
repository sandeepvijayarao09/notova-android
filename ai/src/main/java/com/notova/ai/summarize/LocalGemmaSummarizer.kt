package com.notova.ai.summarize

import com.notova.ai.model.ModelCapability
import com.notova.ai.model.ModelStore
import com.notova.core.model.ActionItem
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import java.time.Instant
import javax.inject.Inject

/**
 * On-device [SummarizerEngine] backed by a Gemma `.task` / `.litertlm` bundle run through MediaPipe
 * LLM Inference via [LlmEngine].
 *
 * Availability is fully guarded:
 *  - reports unavailable when no Gemma model is installed in the [ModelStore], and
 *  - reports unavailable when the native engine fails to load the present model.
 * On a clean emulator with no model this stays unavailable, so the resolver falls through and the
 * build/app remain green.
 */
class LocalGemmaSummarizer
    @Inject
    constructor(
        private val store: ModelStore,
        private val llm: LlmEngine,
    ) : SummarizerEngine {
        override val engineName: String = ENGINE_NAME

        override suspend fun isAvailable(): Boolean {
            val model = store.firstWith(ModelCapability.GEMMA_SUMMARIZER) ?: return false
            return llm.load(model.path)
        }

        override suspend fun summarize(
            transcript: Transcript,
            style: String,
        ): Summary {
            val model =
                store.firstWith(ModelCapability.GEMMA_SUMMARIZER)
                    ?: error("LocalGemmaSummarizer invoked with no Gemma model installed")
            check(llm.load(model.path)) { "failed to load Gemma model at ${model.path}" }

            val prompt = buildPrompt(transcript, style)
            val raw = llm.generate(prompt)

            return Summary(
                recordingId = transcript.recordingId,
                style = style,
                contentMarkdown = raw.trim(),
                actionItems = extractActionItems(raw),
                model = "$MODEL_PREFIX:${model.name}",
                generatedAt = Instant.now(),
            )
        }

        private fun buildPrompt(
            transcript: Transcript,
            style: String,
        ): String =
            buildString {
                appendLine("You are an on-device meeting-notes assistant. Summarize the transcript below.")
                appendLine("Style: $style.")
                appendLine("Return Markdown with a short summary, then a \"## Action items\" section")
                appendLine("listing each task on its own line prefixed with \"- [ ] \".")
                appendLine()
                appendLine("Transcript:")
                append(transcript.fullText)
            }

        /** Pulls `- [ ] ...` checkbox lines out of the model's Markdown into structured action items. */
        private fun extractActionItems(markdown: String): List<ActionItem> =
            markdown.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("- [ ]") || it.startsWith("- [x]", ignoreCase = true) }
                .map { line ->
                    val done = line.startsWith("- [x]", ignoreCase = true)
                    val text = line.removePrefix("- [ ]").removePrefix("- [x]").removePrefix("- [X]").trim()
                    ActionItem(text = text, done = done)
                }
                .filter { it.text.isNotEmpty() }
                .toList()

        companion object {
            const val ENGINE_NAME = "On-device Gemma (MediaPipe)"
            const val MODEL_PREFIX = "gemma-mediapipe"
        }
    }
