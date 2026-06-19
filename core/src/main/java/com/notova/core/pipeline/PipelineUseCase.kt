package com.notova.core.pipeline

import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import com.notova.core.summarize.Summarizer
import com.notova.core.transcribe.Transcriber
import javax.inject.Inject

/** The finished output of the on-device pipeline for one recording. */
data class FinishedNote(
    val transcript: Transcript,
    val summary: Summary,
)

/**
 * Composes [Transcriber] and [Summarizer] into a single on-device pipeline:
 * audio file -> transcript -> summary. This is the seam Whisper/Gemma slot into; callers
 * only depend on this use case, not on the concrete models.
 */
class PipelineUseCase
    @Inject
    constructor(
        private val transcriber: Transcriber,
        private val summarizer: Summarizer,
    ) {
        suspend fun process(
            audioPath: String,
            style: String = DEFAULT_STYLE,
        ): FinishedNote {
            val transcript = transcriber.transcribe(audioPath)
            val summary = summarizer.summarize(transcript, style)
            return FinishedNote(transcript = transcript, summary = summary)
        }

        companion object {
            const val DEFAULT_STYLE = "concise"
        }
    }
