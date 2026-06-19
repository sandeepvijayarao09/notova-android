package com.notova.ai.summarize

import com.notova.core.model.ActionItem
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import java.time.Instant
import javax.inject.Inject

/**
 * On-device [SummarizerEngine] backed by ML Kit GenAI Summarization (Gemini Nano / AICore) via
 * [MlKitSummarizationEngine].
 *
 * Availability is gated on the live feature status: only [GenAiFeatureStatus.AVAILABLE] counts.
 * On unsupported hardware / emulators the status is UNAVAILABLE so the resolver falls through. The
 * weights download (when DOWNLOADABLE) is offered as an explicit Settings action via
 * [ensureFeatureDownloaded] rather than triggered implicitly during a resolve.
 */
class GeminiNanoSummarizer
    @Inject
    constructor(
        private val engine: MlKitSummarizationEngine,
    ) : SummarizerEngine {
        override val engineName: String = ENGINE_NAME

        override suspend fun isAvailable(): Boolean = engine.checkFeatureStatus() == GenAiFeatureStatus.AVAILABLE

        /** Current ML Kit feature status, surfaced so Settings can show "downloadable" / "downloading". */
        suspend fun featureStatus(): GenAiFeatureStatus = engine.checkFeatureStatus()

        /** Kicks off the AICore weights download; returns true on success. For the Settings action. */
        suspend fun ensureFeatureDownloaded(): Boolean = engine.ensureDownloaded()

        override suspend fun summarize(
            transcript: Transcript,
            style: String,
        ): Summary {
            val raw = engine.summarize(transcript.fullText)
            return Summary(
                recordingId = transcript.recordingId,
                style = style,
                contentMarkdown = raw.trim(),
                actionItems = extractActionItems(raw),
                model = MODEL_NAME,
                generatedAt = Instant.now(),
            )
        }

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
            const val ENGINE_NAME = "Gemini Nano (ML Kit / AICore)"
            const val MODEL_NAME = "gemini-nano-mlkit"
        }
    }
