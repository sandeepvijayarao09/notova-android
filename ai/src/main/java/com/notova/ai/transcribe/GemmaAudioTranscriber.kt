package com.notova.ai.transcribe

import com.notova.ai.audio.AudioDecoder
import com.notova.ai.model.ModelCapability
import com.notova.ai.model.ModelStore
import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

/**
 * On-device [TranscriberEngine] that transcribes via Gemma 3n's audio modality, mirroring how Google
 * AI Edge Gallery handles audio: the recording is decoded to 16 kHz mono PCM and each ≤30 s window is
 * sent to the LiteRT-LM engine ([AudioTranscriptionEngine]) with a transcription instruction.
 *
 * Reuses the same installed Gemma `.litertlm` model as [com.notova.ai.summarize.LocalGemmaSummarizer]
 * (one model, two capabilities). Availability is fully guarded:
 *  - reports unavailable when no Gemma model is installed, and
 *  - reports unavailable when the native engine fails to load the model (e.g. on an emulator).
 * In either case the resolver falls through to the Android SpeechRecognizer / stub engines.
 */
class GemmaAudioTranscriber
    @Inject
    constructor(
        private val store: ModelStore,
        private val engine: AudioTranscriptionEngine,
        private val decoder: AudioDecoder,
    ) : TranscriberEngine {
        override val engineName: String = ENGINE_NAME

        override suspend fun isAvailable(): Boolean {
            val model = store.firstWith(ModelCapability.GEMMA_SUMMARIZER) ?: return false
            return engine.load(model.path)
        }

        override suspend fun transcribe(audioPath: String): Transcript {
            val model =
                store.firstWith(ModelCapability.GEMMA_SUMMARIZER)
                    ?: error("GemmaAudioTranscriber invoked with no Gemma model installed")
            check(engine.load(model.path)) { "failed to load Gemma model at ${model.path}" }

            // Transcribe each window as the decoder streams it out, so a long recording never holds
            // the whole file (or the whole transcript's worth of audio) in memory at once.
            val segments = mutableListOf<TranscriptSegment>()
            val fullText = StringBuilder()
            decoder.decodeToPcm16kMono(audioPath).collect { chunk ->
                val text = engine.transcribe(chunk.bytes, TRANSCRIBE_PROMPT).trim()
                if (text.isEmpty()) return@collect
                segments += TranscriptSegment(startMs = chunk.startMs, endMs = chunk.endMs, text = text)
                if (fullText.isNotEmpty()) fullText.append(' ')
                fullText.append(text)
            }

            val recordingId = recordingIdFromAudioPath(audioPath)
            return Transcript(
                recordingId = recordingId,
                language = LANGUAGE_AUTO,
                fullText = fullText.toString(),
                segments = segments,
            )
        }

        companion object {
            const val ENGINE_NAME = "On-device Gemma 3n audio (LiteRT-LM)"

            // Gemma 3n is multilingual; we don't force a language, so label the transcript "auto".
            private const val LANGUAGE_AUTO = "auto"

            private const val TRANSCRIBE_PROMPT =
                "Transcribe this audio recording to text verbatim. " +
                    "Output only the transcript text, with no preamble, labels, or commentary."
        }
    }
