package com.notova.ai.transcribe

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Selection logic of [ResolvingTranscriber] across availability permutations using fake engines.
 */
class ResolvingTranscriberTest {
    private fun resolver(vararg engines: TranscriberEngine) = ResolvingTranscriber(engines.toList())

    @Test
    fun `picks the first available engine`() =
        runTest {
            val speech = FakeTranscriberEngine("speech", available = true)
            val stub = FakeTranscriberEngine("stub", available = true)
            val resolver = resolver(speech, stub)

            val transcript = resolver.transcribe("/audio/note.m4a")

            assertTrue(transcript.fullText.contains("speech"))
            assertEquals(1, speech.transcribeCount)
            assertEquals(0, stub.transcribeCount)
        }

    @Test
    fun `falls through to the stub when speech recognition is unavailable`() =
        runTest {
            val speech = FakeTranscriberEngine("speech", available = false)
            val stub = FakeTranscriberEngine("stub", available = true)
            val resolver = resolver(speech, stub)

            val transcript = resolver.transcribe("/audio/note.m4a")

            assertTrue(transcript.fullText.contains("stub"))
            assertEquals(0, speech.transcribeCount)
            assertEquals(1, stub.transcribeCount)
        }

    @Test
    fun `records the active engine name`() =
        runTest {
            val resolver =
                resolver(
                    FakeTranscriberEngine("speech", available = false),
                    FakeTranscriberEngine("stub", available = true),
                )

            assertNull(resolver.activeEngine.value)
            resolver.transcribe("/audio/x.m4a")
            assertEquals("stub", resolver.activeEngine.value)
        }

    @Test
    fun `preserves the recording id derived from the audio path`() =
        runTest {
            val resolver = resolver(FakeTranscriberEngine("stub", available = true))
            val transcript = resolver.transcribe("/deep/path/recording-42.m4a")
            assertEquals("recording-42", transcript.recordingId)
        }

    @Test
    fun `a throwing availability check is treated as unavailable`() =
        runTest {
            val speech = FakeTranscriberEngine("speech", available = true, availabilityThrows = true)
            val stub = FakeTranscriberEngine("stub", available = true)
            val resolver = resolver(speech, stub)

            resolver.transcribe("/audio/x.m4a")

            assertEquals(0, speech.transcribeCount)
            assertEquals(1, stub.transcribeCount)
        }
}
