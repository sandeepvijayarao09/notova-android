package com.notova.ai.summarize

/**
 * Runtime status of the ML Kit GenAI summarization feature, mirroring
 * `com.google.mlkit.genai.common.FeatureStatus` but as a Kotlin enum so the mapping logic in
 * [GeminiNanoSummarizer] is unit-testable without the real ML Kit / AICore stack.
 */
enum class GenAiFeatureStatus {
    /** Not supported on this device (e.g. emulator, no AICore). */
    UNAVAILABLE,

    /** Supported but the Gemini Nano weights still need downloading via AICore. */
    DOWNLOADABLE,

    /** Currently downloading. */
    DOWNLOADING,

    /** Ready to run inference now. */
    AVAILABLE,
}

/**
 * Thin seam over ML Kit GenAI Summarization (`com.google.mlkit:genai-summarization`, backed by
 * Gemini Nano / AICore). Wrapping it lets [GeminiNanoSummarizer]'s availability gating and summary
 * mapping be unit-tested with a fake — no AICore, no supported device required.
 */
interface MlKitSummarizationEngine {
    /** Queries the current feature status (UNAVAILABLE on unsupported devices). MUST NOT throw. */
    suspend fun checkFeatureStatus(): GenAiFeatureStatus

    /** Triggers the AICore feature download if [GenAiFeatureStatus.DOWNLOADABLE]. MUST NOT throw. */
    suspend fun ensureDownloaded(): Boolean

    /** Runs summarization for [text]; returns the summary string. May throw on inference error. */
    suspend fun summarize(text: String): String
}
