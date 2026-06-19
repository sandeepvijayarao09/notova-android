package com.notova.ai.transcribe

import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import javax.inject.Inject

/**
 * On-device [TranscriberEngine] backed by Android's [android.speech.SpeechRecognizer] via the
 * [SpeechRecognitionEngine] seam.
 *
 * Availability defers entirely to the engine's runtime check (recognition service present,
 * on-device recognition supported, RECORD_AUDIO granted, offline data available). Maps the
 * recognizer's segments into the core [Transcript] model, preserving timing and language.
 */
class SpeechRecognizerTranscriber
    @Inject
    constructor(
        private val engine: SpeechRecognitionEngine,
    ) : TranscriberEngine {
        override val engineName: String = ENGINE_NAME

        override suspend fun isAvailable(): Boolean = engine.isAvailable()

        override suspend fun transcribe(audioPath: String): Transcript {
            val result = engine.recognize(audioPath)
            val recordingId = audioPath.substringAfterLast('/').substringBeforeLast('.')
            return Transcript(
                recordingId = recordingId,
                language = result.language,
                fullText = result.fullText,
                segments =
                    result.segments.map { seg ->
                        TranscriptSegment(startMs = seg.startMs, endMs = seg.endMs, text = seg.text)
                    },
            )
        }

        companion object {
            const val ENGINE_NAME = "Android SpeechRecognizer (on-device)"
        }
    }
