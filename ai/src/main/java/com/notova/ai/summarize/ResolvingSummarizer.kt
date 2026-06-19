package com.notova.ai.summarize

import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import com.notova.core.summarize.Summarizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * [Summarizer] that, at call time, picks the first [SummarizerEngine] reporting itself available,
 * in a fixed priority order, then delegates to it. Records the chosen engine in [activeEngine] so
 * the Settings UI can surface which engine is live and why.
 *
 * Priority (highest first):
 *  1. [LocalGemmaSummarizer]  — a local Gemma model is installed (best on-device quality).
 *  2. [GeminiNanoSummarizer]  — Gemini Nano / AICore is AVAILABLE on this device.
 *  3. [StubSummarizerEngine]  — always available templated fallback.
 *
 * The list is injected, so tests can supply fakes with arbitrary availability permutations.
 */
@Singleton
class ResolvingSummarizer
    @Inject
    constructor(
        @Named("summarizerEngines") private val engines: List<@JvmSuppressWildcards SummarizerEngine>,
    ) : Summarizer {
        private val _activeEngine = MutableStateFlow<String?>(null)

        /** Name of the engine that handled the most recent request; null until the first summarize. */
        val activeEngine: StateFlow<String?> = _activeEngine.asStateFlow()

        /** Resolves (without summarizing) the engine that would currently handle a request. */
        suspend fun resolve(): SummarizerEngine {
            val chosen = engines.firstOrNull { runCatching { it.isAvailable() }.getOrDefault(false) }
            return chosen ?: engines.last()
        }

        override suspend fun summarize(
            transcript: Transcript,
            style: String,
        ): Summary {
            val engine = resolve()
            _activeEngine.value = engine.engineName
            return engine.summarize(transcript, style)
        }
    }
