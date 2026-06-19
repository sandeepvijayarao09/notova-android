package com.notova.core.transcribe

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Output-shape, determinism and id-derivation coverage for [StubTranscriber]. */
class StubTranscriberTest {
    @Test
    fun `derives recordingId from the file name without extension`() =
        runTest {
            val transcript = StubTranscriber().transcribe("/data/cache/recording-123.m4a")
            assertEquals("recording-123", transcript.recordingId)
        }

    @Test
    fun `derives recordingId from a bare file name`() =
        runTest {
            val transcript = StubTranscriber().transcribe("note42.wav")
            assertEquals("note42", transcript.recordingId)
        }

    @Test
    fun `handles a path with no extension`() =
        runTest {
            val transcript = StubTranscriber().transcribe("/cache/import_998877")
            assertEquals("import_998877", transcript.recordingId)
        }

    @Test
    fun `handles a path with multiple dots keeping everything before the last dot`() =
        runTest {
            val transcript = StubTranscriber().transcribe("/cache/my.note.final.m4a")
            assertEquals("my.note.final", transcript.recordingId)
        }

    @Test
    fun `language is english`() =
        runTest {
            assertEquals("en", StubTranscriber().transcribe("/a/b.m4a").language)
        }

    @Test
    fun `produces two non-blank speaker-attributed segments`() =
        runTest {
            val transcript = StubTranscriber().transcribe("/a/b.m4a")
            assertEquals(2, transcript.segments.size)
            transcript.segments.forEach { seg ->
                assertTrue(seg.text.isNotBlank())
                assertNotNull(seg.speaker)
                assertEquals("Speaker 1", seg.speaker)
            }
        }

    @Test
    fun `segment timestamps are contiguous and ordered`() =
        runTest {
            val segs = StubTranscriber().transcribe("/a/b.m4a").segments
            assertEquals(0L, segs[0].startMs)
            assertEquals(4000L, segs[0].endMs)
            assertEquals(4000L, segs[1].startMs)
            assertEquals(8000L, segs[1].endMs)
        }

    @Test
    fun `fullText is the space-joined concatenation of segment texts`() =
        runTest {
            val transcript = StubTranscriber().transcribe("/a/b.m4a")
            val expected = transcript.segments.joinToString(" ") { it.text }
            assertEquals(expected, transcript.fullText)
            assertTrue(transcript.fullText.isNotBlank())
        }

    @Test
    fun `output is deterministic across calls for the same path`() =
        runTest {
            val transcriber = StubTranscriber()
            val a = transcriber.transcribe("/a/b.m4a")
            val b = transcriber.transcribe("/a/b.m4a")
            assertEquals(a.fullText, b.fullText)
            assertEquals(a.segments, b.segments)
            assertEquals(a.language, b.language)
        }
}
