package com.notova.core

import com.notova.core.pipeline.PipelineUseCase
import com.notova.core.summarize.StubSummarizer
import com.notova.core.transcribe.StubTranscriber
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineUseCaseTest {
    @Test
    fun `pipeline produces transcript and summary from stubs`() =
        runTest {
            val useCase = PipelineUseCase(StubTranscriber(), StubSummarizer())

            val result = useCase.process("/tmp/recording-123.m4a")

            assertEquals("recording-123", result.transcript.recordingId)
            assertTrue(result.transcript.fullText.isNotBlank())
            assertTrue(result.summary.contentMarkdown.contains("Summary"))
            assertEquals(result.transcript.recordingId, result.summary.recordingId)
        }
}
