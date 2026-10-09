package com.notova.core.summarize

import com.notova.core.model.Summary
import com.notova.core.model.Transcript

/**
 * Produces a [Summary] from a [Transcript], fully on-device.
 *
 * The production implementation will wrap Gemma 3n E4B via MediaPipe LLM Inference / LiteRT.
 * With no model available, [StubSummarizer] produces a basic, clearly labelled extractive summary.
 */
interface Summarizer {
    suspend fun summarize(
        transcript: Transcript,
        style: String,
    ): Summary
}
