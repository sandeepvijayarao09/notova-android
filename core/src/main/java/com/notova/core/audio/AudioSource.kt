package com.notova.core.audio

import com.notova.core.model.RecordingSource

/** Result of a completed capture or import: where the audio landed and how long it is. */
data class AudioCaptureResult(
    val outputFilePath: String,
    val durationSec: Double,
    val source: RecordingSource,
)

/**
 * Captures audio from any input device (phone mic, Bluetooth, etc.) OR loads it from a
 * content URI (file import via the Storage Access Framework).
 *
 * Implementations write a single audio file and report its path + duration. Everything
 * downstream ([com.notova.core.transcribe.Transcriber]) consumes only that path, so the
 * capture mechanism is fully swappable.
 */
interface AudioSource {
    /** Begin capturing from the active input device. */
    suspend fun start(): Unit

    /** Stop capturing and return the resulting file. */
    suspend fun stop(): AudioCaptureResult

    /** Import an already-recorded file referenced by a content/file URI string. */
    suspend fun loadFromUri(uri: String): AudioCaptureResult
}
