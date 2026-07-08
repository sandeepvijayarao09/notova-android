package com.notova.ai.audio

import kotlinx.coroutines.flow.Flow

/** A window of decoded PCM audio with its position in the source recording. */
class PcmChunk(
    /** 16 kHz, mono, 16-bit little-endian PCM samples. */
    val bytes: ByteArray,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Decodes an on-disk recording (Notova captures `.m4a` / AAC) into chunks of 16 kHz, mono, 16-bit
 * PCM — the format Gemma 3n's audio modality expects — split into windows no longer than the model's
 * per-clip limit.
 *
 * Returns a cold [Flow] that emits chunks **as they are decoded** rather than materializing the whole
 * file, so memory stays bounded no matter how long the recording is (a one-hour import decodes the
 * same as a one-minute one). Behind an interface so [com.notova.ai.transcribe.GemmaAudioTranscriber]
 * is unit-testable on the JVM with a fake, without `MediaCodec` / a device.
 */
interface AudioDecoder {
    /**
     * Streams [audioPath] as [PcmChunk]s in playback order. Collection runs the decode; the flow may
     * throw on a malformed file (the transcriber's resolver falls through) and emits nothing for an
     * audio file with no decodable content.
     */
    fun decodeToPcm16kMono(audioPath: String): Flow<PcmChunk>
}
