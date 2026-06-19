package com.notova.core.pipeline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Behavioural coverage of [PipelineUseCase] using fully controllable fakes so each seam
 * (transcriber output, summarizer input, error propagation, cancellation) can be asserted in
 * isolation — independent of the stub implementations.
 */
class PipelineUseCaseFakeTest {
    @Test
    fun `success path returns the transcript and summary together`() =
        runTest {
            val transcriber = FakeTranscriber(fullText = "hello world")
            val summarizer = FakeSummarizer()
            val useCase = PipelineUseCase(transcriber, summarizer)

            val result = useCase.process("/audio/note-7.m4a")

            assertEquals("note-7", result.transcript.recordingId)
            assertEquals("hello world", result.transcript.fullText)
            assertEquals("note-7", result.summary.recordingId)
            assertTrue(result.summary.contentMarkdown.contains("hello world"))
        }

    @Test
    fun `default style is concise and is forwarded to the summarizer`() =
        runTest {
            val summarizer = FakeSummarizer()
            val useCase = PipelineUseCase(FakeTranscriber(), summarizer)

            useCase.process("/audio/x.m4a")

            assertEquals(PipelineUseCase.DEFAULT_STYLE, summarizer.lastStyle)
            assertEquals("concise", summarizer.lastStyle)
        }

    @Test
    fun `explicit style overrides the default`() =
        runTest {
            val summarizer = FakeSummarizer()
            val useCase = PipelineUseCase(FakeTranscriber(), summarizer)

            useCase.process("/audio/x.m4a", style = "detailed")

            assertEquals("detailed", summarizer.lastStyle)
        }

    @Test
    fun `audio path is forwarded verbatim to the transcriber`() =
        runTest {
            val transcriber = FakeTranscriber()
            val useCase = PipelineUseCase(transcriber, FakeSummarizer())

            useCase.process("/some/deep/path/recording.m4a")

            assertEquals("/some/deep/path/recording.m4a", transcriber.lastAudioPath)
        }

    @Test
    fun `summarizer receives exactly the transcriber's output instance`() =
        runTest {
            val transcriber = FakeTranscriber(fullText = "the exact transcript")
            val summarizer = FakeSummarizer()
            val useCase = PipelineUseCase(transcriber, summarizer)

            val result = useCase.process("/audio/x.m4a")

            // The transcript handed to the summarizer is the same object returned in the result.
            assertSame(result.transcript, summarizer.lastTranscript)
            assertEquals("the exact transcript", summarizer.lastTranscript?.fullText)
        }

    @Test
    fun `transcriber throwing propagates and the summarizer is never invoked`() =
        runTest {
            val boom = IllegalStateException("transcription failed")
            val transcriber = FakeTranscriber(error = boom)
            val summarizer = FakeSummarizer()
            val useCase = PipelineUseCase(transcriber, summarizer)

            val thrown =
                try {
                    useCase.process("/audio/x.m4a")
                    fail("Expected IllegalStateException")
                    null
                } catch (e: IllegalStateException) {
                    e
                }
            assertEquals("transcription failed", thrown?.message)
            assertEquals(0, summarizer.callCount)
        }

    @Test
    fun `summarizer throwing propagates after the transcriber ran`() =
        runTest {
            val transcriber = FakeTranscriber()
            val summarizer = FakeSummarizer(error = RuntimeException("summarize failed"))
            val useCase = PipelineUseCase(transcriber, summarizer)

            val thrown =
                try {
                    useCase.process("/audio/x.m4a")
                    fail("Expected RuntimeException")
                    null
                } catch (e: RuntimeException) {
                    e
                }
            assertEquals("summarize failed", thrown?.message)
            assertEquals(1, transcriber.callCount)
        }

    @Test
    fun `empty transcript still flows through to a summary`() =
        runTest {
            val transcriber = FakeTranscriber(fullText = "")
            val summarizer = FakeSummarizer()
            val useCase = PipelineUseCase(transcriber, summarizer)

            val result = useCase.process("/audio/empty.m4a")

            assertEquals("", result.transcript.fullText)
            assertEquals("", summarizer.lastTranscript?.fullText)
        }

    @Test
    fun `whitespace-only transcript is forwarded unchanged`() =
        runTest {
            val transcriber = FakeTranscriber(fullText = "   \n\t  ")
            val summarizer = FakeSummarizer()
            val useCase = PipelineUseCase(transcriber, summarizer)

            useCase.process("/audio/ws.m4a")

            assertEquals("   \n\t  ", summarizer.lastTranscript?.fullText)
        }

    @Test
    fun `cancellation in the transcriber aborts before summarizing`() =
        runTest {
            val transcriber = FakeTranscriber(error = CancellationException("cancelled"))
            val summarizer = FakeSummarizer()
            val useCase = PipelineUseCase(transcriber, summarizer)

            var cancelledMessage: String? = null
            try {
                useCase.process("/audio/x.m4a")
            } catch (e: CancellationException) {
                cancelledMessage = e.message
            }
            assertEquals("cancelled", cancelledMessage)
            assertEquals(0, summarizer.callCount)
        }

    @Test
    fun `each call invokes the transcriber and summarizer exactly once`() =
        runTest {
            val transcriber = FakeTranscriber()
            val summarizer = FakeSummarizer()
            val useCase = PipelineUseCase(transcriber, summarizer)

            useCase.process("/audio/x.m4a")

            assertEquals(1, transcriber.callCount)
            assertEquals(1, summarizer.callCount)
        }

    @Test
    fun `language from the transcriber is preserved in the finished note`() =
        runTest {
            val transcriber = FakeTranscriber(language = "fr")
            val useCase = PipelineUseCase(transcriber, FakeSummarizer())

            val result = useCase.process("/audio/x.m4a")

            assertEquals("fr", result.transcript.language)
        }

    @Test
    fun `FinishedNote equality is structural`() =
        runTest {
            val useCase = PipelineUseCase(FakeTranscriber(), FakeSummarizer())
            val a = useCase.process("/audio/same.m4a")
            val b = useCase.process("/audio/same.m4a")
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }
}
