package com.notova.ai.transcribe

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Availability gating + segment mapping for [SpeechRecognizerTranscriber] via a fake
 * [SpeechRecognitionEngine] — no device, permission, or offline data required.
 */
class SpeechRecognizerTranscriberTest {
    private class FakeSpeech(
        private val available: Boolean,
        private val result: RecognitionResult =
            RecognitionResult(
                language = "en",
                segments =
                    listOf(
                        RecognizedSegment(0, 1_500, "hello there"),
                        RecognizedSegment(1_500, 3_000, "general kenobi"),
                    ),
            ),
        private val recognizeThrows: Boolean = false,
    ) : SpeechRecognitionEngine {
        override suspend fun isAvailable(): Boolean = available

        override suspend fun recognize(audioPath: String): RecognitionResult {
            if (recognizeThrows) error("no offline data")
            return result
        }
    }

    @Test
    fun `availability defers to the engine`() =
        runTest {
            assertTrue(SpeechRecognizerTranscriber(FakeSpeech(available = true)).isAvailable())
            assertFalse(SpeechRecognizerTranscriber(FakeSpeech(available = false)).isAvailable())
        }

    @Test
    fun `transcribe maps recognized segments preserving timing`() =
        runTest {
            val transcriber = SpeechRecognizerTranscriber(FakeSpeech(available = true))

            val transcript = transcriber.transcribe("/audio/meeting-9.m4a")

            assertEquals("meeting-9", transcript.recordingId)
            assertEquals("en", transcript.language)
            assertEquals("hello there general kenobi", transcript.fullText)
            assertEquals(2, transcript.segments.size)
            assertEquals(0L, transcript.segments[0].startMs)
            assertEquals(1_500L, transcript.segments[0].endMs)
            assertEquals("general kenobi", transcript.segments[1].text)
        }

    @Test
    fun `recognition errors propagate so the resolver can fall through`() =
        runTest {
            val transcriber = SpeechRecognizerTranscriber(FakeSpeech(available = true, recognizeThrows = true))
            var message: String? = null
            try {
                transcriber.transcribe("/audio/x.m4a")
            } catch (e: IllegalStateException) {
                message = e.message
            }
            assertEquals("no offline data", message)
        }
}
