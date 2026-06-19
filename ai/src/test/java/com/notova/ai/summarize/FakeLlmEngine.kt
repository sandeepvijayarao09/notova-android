package com.notova.ai.summarize

/**
 * Controllable [LlmEngine] for [LocalGemmaSummarizer] tests: configurable load success and a fixed
 * generated response, recording what it was asked to load / generate.
 */
class FakeLlmEngine(
    private val loadSucceeds: Boolean = true,
    private val response: String = "## Summary\nDid stuff.\n\n## Action items\n- [ ] Follow up with Sam",
) : LlmEngine {
    var loadedPath: String? = null
        private set
    var lastPrompt: String? = null
        private set
    private var ready = false

    override suspend fun load(modelPath: String): Boolean {
        loadedPath = modelPath
        ready = loadSucceeds
        return loadSucceeds
    }

    override fun isReady(): Boolean = ready

    override suspend fun generate(prompt: String): String {
        lastPrompt = prompt
        return response
    }

    override fun close() {
        ready = false
    }
}
