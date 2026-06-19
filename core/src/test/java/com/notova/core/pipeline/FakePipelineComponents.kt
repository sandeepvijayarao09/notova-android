package com.notova.core.pipeline

import com.notova.core.model.ActionItem
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import com.notova.core.summarize.Summarizer
import com.notova.core.transcribe.Transcriber
import java.time.Instant

/**
 * A controllable [Transcriber] for pipeline tests. Records the audio path it was called with and
 * either returns a configurable [Transcript] or throws a configured error.
 */
class FakeTranscriber(
    private val language: String = "en",
    private val fullText: String = "fake transcript text",
    private val error: Throwable? = null,
) : Transcriber {
    var lastAudioPath: String? = null
        private set
    var callCount: Int = 0
        private set

    override suspend fun transcribe(audioPath: String): Transcript {
        callCount++
        lastAudioPath = audioPath
        error?.let { throw it }
        val recordingId = audioPath.substringAfterLast('/').substringBeforeLast('.')
        return Transcript(
            recordingId = recordingId,
            language = language,
            fullText = fullText,
            segments = listOf(TranscriptSegment(0, 1_000, fullText)),
        )
    }
}

/**
 * A controllable [Summarizer] for pipeline tests. Captures the transcript and style handed to it so
 * tests can assert the pipeline forwards the transcriber's output verbatim.
 */
class FakeSummarizer(
    private val error: Throwable? = null,
) : Summarizer {
    var lastTranscript: Transcript? = null
        private set
    var lastStyle: String? = null
        private set
    var callCount: Int = 0
        private set

    override suspend fun summarize(
        transcript: Transcript,
        style: String,
    ): Summary {
        callCount++
        lastTranscript = transcript
        lastStyle = style
        error?.let { throw it }
        return Summary(
            recordingId = transcript.recordingId,
            style = style,
            // Fixed action-item id keeps fake output deterministic for equality assertions.
            contentMarkdown = "summary of: ${transcript.fullText}",
            actionItems = listOf(ActionItem(id = "fake-action-1", text = "follow up")),
            model = "fake-summarizer",
            generatedAt = Instant.ofEpochMilli(0),
        )
    }
}
