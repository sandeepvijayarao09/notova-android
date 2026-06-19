package com.notova.ai.summarize

import com.notova.core.summarize.Summarizer

/**
 * A [Summarizer] that can report at runtime whether it is currently usable on this device, plus a
 * human-readable [engineName]. The [com.notova.ai.summarize.ResolvingSummarizer] picks the first
 * available engine in a fixed priority order and remembers which one handled each request.
 */
interface SummarizerEngine : Summarizer {
    /** Stable, user-facing name shown in Settings (e.g. "On-device Gemma (MediaPipe)"). */
    val engineName: String

    /**
     * Whether this engine can serve a request right now. Implementations MUST NOT throw — a missing
     * model, unsupported device, or absent AICore feature must surface as `false` so the resolver
     * can fall through to the next engine.
     */
    suspend fun isAvailable(): Boolean
}
