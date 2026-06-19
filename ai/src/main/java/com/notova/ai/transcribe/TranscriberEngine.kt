package com.notova.ai.transcribe

import com.notova.core.transcribe.Transcriber

/**
 * A [Transcriber] that can report at runtime whether it is currently usable on this device, plus a
 * human-readable [engineName]. The [com.notova.ai.transcribe.ResolvingTranscriber] picks the first
 * available engine in a fixed priority order and remembers which one handled each request.
 */
interface TranscriberEngine : Transcriber {
    /** Stable, user-facing name shown in Settings (e.g. "Android SpeechRecognizer"). */
    val engineName: String

    /**
     * Whether this engine can serve a request right now. Implementations MUST NOT throw — missing
     * offline recognition data or a denied RECORD_AUDIO permission must surface as `false` so the
     * resolver can fall through to the next engine.
     */
    suspend fun isAvailable(): Boolean
}
