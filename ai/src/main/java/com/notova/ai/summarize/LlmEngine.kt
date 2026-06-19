package com.notova.ai.summarize

/**
 * Thin seam over a local large-language-model runtime (MediaPipe LLM Inference in production).
 *
 * Wrapping the native engine behind this interface keeps [LocalGemmaSummarizer]'s prompt-building,
 * availability mapping and error handling fully unit-testable on the JVM with a fake — no `.task`
 * model, no native `.so`, no device required.
 */
interface LlmEngine {
    /**
     * Loads the model at [modelPath] (if not already loaded) and returns true on success.
     * MUST NOT throw: a missing file or failed native init returns false.
     */
    suspend fun load(modelPath: String): Boolean

    /** Whether a model is currently loaded and ready to generate. */
    fun isReady(): Boolean

    /** Runs inference for [prompt] and returns the raw model output. May throw on inference error. */
    suspend fun generate(prompt: String): String

    /** Releases native resources. */
    fun close()
}
