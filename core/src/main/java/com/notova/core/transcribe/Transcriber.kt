package com.notova.core.transcribe

import com.notova.core.model.Transcript

/**
 * Turns an on-disk audio file into a [Transcript], fully on-device.
 *
 * The production implementation will wrap a Whisper model (e.g. whisper.cpp / LiteRT).
 * Until then [StubTranscriber] returns placeholder output so the pipeline runs end-to-end.
 */
interface Transcriber {
    suspend fun transcribe(audioPath: String): Transcript
}
