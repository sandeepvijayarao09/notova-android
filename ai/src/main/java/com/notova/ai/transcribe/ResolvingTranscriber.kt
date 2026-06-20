package com.notova.ai.transcribe

import com.notova.core.model.Transcript
import com.notova.core.transcribe.Transcriber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * [Transcriber] that, at call time, picks the first [TranscriberEngine] reporting itself available,
 * in a fixed priority order, then delegates to it. Records the chosen engine in [activeEngine] for
 * the Settings UI.
 *
 * Priority (highest first):
 *  1. [SpeechRecognizerTranscriber] — Android on-device speech recognition, when available.
 *  2. [StubTranscriberEngine]       — always available placeholder fallback.
 *
 * Local Whisper is intentionally out of scope; a `WhisperTranscriberEngine` slots in at the front
 * of this list with no other change. The list is injected so tests can supply fakes.
 */
@Singleton
class ResolvingTranscriber
    @Inject
    constructor(
        @Named("transcriberEngines") private val engines: List<@JvmSuppressWildcards TranscriberEngine>,
    ) : Transcriber {
        private val _activeEngine = MutableStateFlow<String?>(null)

        /** Name of the engine that handled the most recent request; null until the first transcribe. */
        val activeEngine: StateFlow<String?> = _activeEngine.asStateFlow()

        /** Resolves (without transcribing) the engine that would currently handle a request. */
        suspend fun resolve(): TranscriberEngine {
            val chosen = engines.firstOrNull { runCatching { it.isAvailable() }.getOrDefault(false) }
            return chosen ?: engines.last()
        }

        // Intentionally catches Throwable: a fallback resolver must survive ANY engine
        // failure and try the next one (CancellationException is rethrown for coroutine safety).
        @Suppress("TooGenericExceptionCaught")
        override suspend fun transcribe(audioPath: String): Transcript {
            val candidates =
                engines
                    .filter { runCatching { it.isAvailable() }.getOrDefault(false) }
                    .ifEmpty { listOf(engines.last()) }

            // Try each available engine in order; if one reports available but throws
            // at runtime (e.g. SpeechRecognizer with no offline data / a file source it
            // can't consume), fall back to the next. The stub never throws.
            var lastError: Throwable? = null
            for (engine in candidates) {
                try {
                    val transcript = engine.transcribe(audioPath)
                    _activeEngine.value = engine.engineName
                    return transcript
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    lastError = e
                }
            }
            _activeEngine.value = null
            throw IllegalStateException("All transcription engines failed", lastError)
        }
    }
