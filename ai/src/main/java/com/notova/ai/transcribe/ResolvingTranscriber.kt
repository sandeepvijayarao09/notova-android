package com.notova.ai.transcribe

import com.notova.core.model.Transcript
import com.notova.core.transcribe.Transcriber
import com.notova.core.transcribe.TranscriptionUnavailableException
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
 *  1. [GemmaAudioTranscriber]       — Gemma 3n audio modality via LiteRT-LM, when a model is installed.
 *  2. [SpeechRecognizerTranscriber] — Android on-device speech recognition, when available.
 *
 * There is deliberately no always-available fallback. If no engine is available, [transcribe]
 * throws [TranscriptionUnavailableException] and the UI says so instead of showing invented text.
 *
 * A dedicated Whisper engine could slot in at the front of this list with no other change. The list
 * is injected so tests can supply fakes.
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

        /**
         * Resolves (without transcribing) the engine that would currently handle a request, or null
         * when no engine can run on this device.
         */
        suspend fun resolve(): TranscriberEngine? =
            engines.firstOrNull { runCatching { it.isAvailable() }.getOrDefault(false) }

        // Intentionally catches Throwable: a fallback resolver must survive ANY engine
        // failure and try the next one (CancellationException is rethrown for coroutine safety).
        @Suppress("TooGenericExceptionCaught")
        override suspend fun transcribe(audioPath: String): Transcript {
            val candidates = engines.filter { runCatching { it.isAvailable() }.getOrDefault(false) }
            if (candidates.isEmpty()) {
                _activeEngine.value = null
                throw TranscriptionUnavailableException()
            }

            // Try each available engine in order; if one reports available but throws
            // at runtime (e.g. SpeechRecognizer with no offline data / a file source it
            // can't consume), fall back to the next.
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
