package com.notova.ai.summarize

import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import java.time.Instant

/**
 * Controllable [SummarizerEngine] for resolver tests: configurable availability + name, records
 * whether it was invoked, and can simulate an availability check that throws.
 */
class FakeSummarizerEngine(
    override val engineName: String,
    private var available: Boolean,
    private val availabilityThrows: Boolean = false,
) : SummarizerEngine {
    var summarizeCount: Int = 0
        private set

    fun setAvailable(value: Boolean) {
        available = value
    }

    override suspend fun isAvailable(): Boolean {
        if (availabilityThrows) error("availability check blew up for $engineName")
        return available
    }

    override suspend fun summarize(
        transcript: Transcript,
        style: String,
    ): Summary {
        summarizeCount++
        return Summary(
            recordingId = transcript.recordingId,
            style = style,
            contentMarkdown = "[$engineName] ${transcript.fullText}",
            actionItems = emptyList(),
            model = engineName,
            generatedAt = Instant.ofEpochMilli(0),
        )
    }
}
