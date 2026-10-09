package com.notova.core.transcribe

import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import javax.inject.Inject

/**
 * Test fixture. Returns a fixed, obviously synthetic transcript so pipeline, view-model and worker
 * tests have deterministic input. It is NOT bound in the app and is not part of any engine chain:
 * when no real engine can run, `ResolvingTranscriber` throws [TranscriptionUnavailableException].
 */
class StubTranscriber
    @Inject
    constructor() : Transcriber {
        override suspend fun transcribe(audioPath: String): Transcript {
            val segments =
                listOf(
                    TranscriptSegment(
                        startMs = 0,
                        endMs = 4000,
                        text = "Test fixture transcript, first segment.",
                        speaker = "Speaker 1",
                    ),
                    TranscriptSegment(
                        startMs = 4000,
                        endMs = 8000,
                        text = "Test fixture transcript, second segment.",
                        speaker = "Speaker 1",
                    ),
                )
            return Transcript(
                recordingId = audioPath.substringAfterLast('/').substringBeforeLast('.'),
                language = "en",
                fullText = segments.joinToString(" ") { it.text },
                segments = segments,
            )
        }
    }
