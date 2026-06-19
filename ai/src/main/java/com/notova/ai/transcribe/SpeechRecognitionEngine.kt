package com.notova.ai.transcribe

/** One recognized chunk of speech with optional timing, mapped to a core TranscriptSegment later. */
data class RecognizedSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** Outcome of an on-device recognition pass over an audio file. */
data class RecognitionResult(
    val language: String,
    val segments: List<RecognizedSegment>,
) {
    val fullText: String get() = segments.joinToString(" ") { it.text }.trim()
}

/**
 * Thin seam over Android's on-device [android.speech.SpeechRecognizer]. Wrapping it keeps
 * [SpeechRecognizerTranscriber]'s availability gating, permission handling and result mapping
 * unit-testable on the JVM with a fake — the real recognizer needs a device, the RECORD_AUDIO
 * permission, and downloaded offline language data.
 */
interface SpeechRecognitionEngine {
    /**
     * Whether on-device recognition can run right now: recognition service present, on-device
     * recognition supported (API 31+), RECORD_AUDIO granted, and offline data available.
     * MUST NOT throw.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Recognizes speech for the audio at [audioPath], returning segments. May throw on recognition
     * error (no offline data, recognizer busy) — the caller maps failures to a graceful fallback.
     */
    suspend fun recognize(audioPath: String): RecognitionResult
}
