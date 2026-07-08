package com.notova.ai.transcribe

import com.notova.ai.audio.AudioDecoder
import com.notova.ai.audio.PcmChunk
import com.notova.ai.model.ModelStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Availability-guard + transcript-assembly logic for [GemmaAudioTranscriber], driven with a fake
 * audio engine and a fake decoder so no `.litertlm` model, no native LiteRT-LM engine, and no
 * `MediaCodec` are needed.
 */
class GemmaAudioTranscriberTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: ModelStore

    @Before
    fun setUp() {
        store = ModelStore(File(temp.root, "models"))
    }

    private suspend fun installGemma() =
        store.import("gemma-3n-E4B-it-int4.litertlm", ByteArrayInputStream("weights".toByteArray()))

    private fun chunk(
        text: String,
        startMs: Long,
        endMs: Long,
    ) = PcmChunk(bytes = text.toByteArray(), startMs = startMs, endMs = endMs)

    @Test
    fun `unavailable when no Gemma model is installed`() =
        runTest {
            val transcriber = GemmaAudioTranscriber(store, FakeAudioEngine(), FakeDecoder())
            assertFalse(transcriber.isAvailable())
        }

    @Test
    fun `unavailable when a model is present but the native engine fails to load`() =
        runTest {
            installGemma()
            val transcriber = GemmaAudioTranscriber(store, FakeAudioEngine(loadSucceeds = false), FakeDecoder())
            assertFalse(transcriber.isAvailable())
        }

    @Test
    fun `available when a model is present and the engine loads`() =
        runTest {
            installGemma()
            val transcriber = GemmaAudioTranscriber(store, FakeAudioEngine(), FakeDecoder())
            assertTrue(transcriber.isAvailable())
        }

    @Test
    fun `joins per-chunk transcripts and preserves timing`() =
        runTest {
            installGemma()
            val decoder = FakeDecoder(chunk("hello world", 0, 30_000), chunk("second part", 30_000, 45_000))
            // The fake engine echoes the chunk bytes (which the fake decoder set to the chunk text).
            val transcriber = GemmaAudioTranscriber(store, FakeAudioEngine(), decoder)

            val transcript = transcriber.transcribe("/tmp/rec-7.m4a")

            assertEquals("rec-7", transcript.recordingId)
            assertEquals("hello world second part", transcript.fullText)
            assertEquals(2, transcript.segments.size)
            assertEquals(30_000L, transcript.segments[1].startMs)
            assertEquals(45_000L, transcript.segments[1].endMs)
            assertEquals("second part", transcript.segments[1].text)
        }

    @Test
    fun `skips chunks the model returns empty for`() =
        runTest {
            installGemma()
            val decoder = FakeDecoder(chunk("kept", 0, 1_000), chunk("   ", 1_000, 2_000))
            val transcriber = GemmaAudioTranscriber(store, FakeAudioEngine(), decoder)

            val transcript = transcriber.transcribe("/tmp/rec-8.m4a")

            assertEquals("kept", transcript.fullText)
            assertEquals(1, transcript.segments.size)
        }

    /** Echoes the chunk bytes back as the transcript text, so tests control output via the decoder. */
    private class FakeAudioEngine(
        private val loadSucceeds: Boolean = true,
    ) : AudioTranscriptionEngine {
        override suspend fun load(modelPath: String): Boolean = loadSucceeds

        override suspend fun transcribe(
            pcm16kMono: ByteArray,
            prompt: String,
        ): String = String(pcm16kMono)
    }

    private class FakeDecoder(
        private vararg val chunks: PcmChunk,
    ) : AudioDecoder {
        override fun decodeToPcm16kMono(audioPath: String): Flow<PcmChunk> = flowOf(*chunks)
    }
}
