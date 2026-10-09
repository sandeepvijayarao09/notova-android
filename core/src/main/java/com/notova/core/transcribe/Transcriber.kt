package com.notova.core.transcribe

import com.notova.core.model.Transcript

/**
 * Turns an on-disk audio file into a [Transcript], fully on-device.
 *
 * The app binds this to `ResolvingTranscriber` in `:ai`, which uses the first available real
 * engine. There is no placeholder fallback: when nothing can run it throws
 * [TranscriptionUnavailableException].
 */
interface Transcriber {
    suspend fun transcribe(audioPath: String): Transcript
}

/**
 * No transcription engine can run on this device right now (for example, no Gemma model is
 * installed). Callers keep the audio and tell the user; they must never invent a transcript.
 */
class TranscriptionUnavailableException(
    message: String = DEFAULT_MESSAGE,
) : IllegalStateException(message) {
    companion object {
        const val DEFAULT_MESSAGE =
            "Transcription unavailable on this device. Install a Gemma 3n model in Settings to " +
                "transcribe. The audio is saved."
    }
}
