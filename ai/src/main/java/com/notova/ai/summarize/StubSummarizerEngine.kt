package com.notova.ai.summarize

import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import com.notova.core.summarize.StubSummarizer
import javax.inject.Inject

/**
 * Always-available [SummarizerEngine] that delegates to the core [StubSummarizer]. This is the
 * guaranteed fallback at the end of the resolver chain, so the pipeline always produces a summary
 * even when no on-device model and no AICore feature are present.
 */
class StubSummarizerEngine
    @Inject
    constructor(
        private val delegate: StubSummarizer,
    ) : SummarizerEngine {
        override val engineName: String = ENGINE_NAME

        override suspend fun isAvailable(): Boolean = true

        override suspend fun summarize(
            transcript: Transcript,
            style: String,
        ): Summary = delegate.summarize(transcript, style)

        companion object {
            const val ENGINE_NAME = "Built-in (template)"
        }
    }
