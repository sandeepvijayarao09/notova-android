package com.notova.ai.transcribe

import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment

/**
 * Controllable [TranscriberEngine] for resolver tests: configurable availability + name, records
 * invocation, and can simulate an availability check that throws.
 */
class FakeTranscriberEngine(
    override val engineName: String,
    private var available: Boolean,
    private val availabilityThrows: Boolean = false,
) : TranscriberEngine {
    var transcribeCount: Int = 0
        private set

    fun setAvailable(value: Boolean) {
        available = value
    }

    override suspend fun isAvailable(): Boolean {
        if (availabilityThrows) error("availability check blew up for $engineName")
        return available
    }

    override suspend fun transcribe(audioPath: String): Transcript {
        transcribeCount++
        return Transcript(
            recordingId = audioPath.substringAfterLast('/').substringBeforeLast('.'),
            language = "en",
            fullText = "[$engineName] $audioPath",
            segments = listOf(TranscriptSegment(0, 1_000, engineName)),
        )
    }
}
