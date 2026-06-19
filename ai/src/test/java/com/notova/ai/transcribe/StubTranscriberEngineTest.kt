package com.notova.ai.transcribe

import com.notova.core.transcribe.StubTranscriber
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stub engine adapter is always available and forwards to the core [StubTranscriber]. */
class StubTranscriberEngineTest {
    @Test
    fun `is always available`() =
        runTest {
            assertTrue(StubTranscriberEngine(StubTranscriber()).isAvailable())
        }

    @Test
    fun `has a readable engine name`() {
        assertEquals("Built-in (placeholder)", StubTranscriberEngine(StubTranscriber()).engineName)
    }

    @Test
    fun `delegates to the core stub transcriber`() =
        runTest {
            val transcript = StubTranscriberEngine(StubTranscriber()).transcribe("/tmp/note-7.m4a")
            assertEquals("note-7", transcript.recordingId)
            assertTrue(transcript.fullText.isNotBlank())
        }
}
