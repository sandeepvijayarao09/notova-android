package com.notova.app.work

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import com.notova.core.pipeline.PipelineUseCase
import com.notova.core.summarize.StubSummarizer
import com.notova.core.summarize.Summarizer
import com.notova.core.transcribe.StubTranscriber
import com.notova.core.transcribe.Transcriber
import com.notova.data.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * Robolectric coverage of [ProcessRecordingWorker]. Builds the worker via [TestListenableWorkerBuilder]
 * with hand-injected pipeline + repository (the production HiltWorkerFactory is not exercised here)
 * and asserts the success / retry / failure outcomes and persisted side effects.
 */
@RunWith(RobolectricTestRunner::class)
class ProcessRecordingWorkerTest {
    private lateinit var repository: FakeRepository

    @Before
    fun setUp() {
        repository = FakeRepository()
    }

    private fun pipeline(
        transcriber: Transcriber = StubTranscriber(),
        summarizer: Summarizer = StubSummarizer(),
    ) = PipelineUseCase(transcriber, summarizer)

    private fun buildWorker(
        recordingId: String?,
        pipeline: PipelineUseCase = pipeline(),
    ): ProcessRecordingWorker {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inputData =
            if (recordingId == null) {
                workDataOf()
            } else {
                workDataOf(ProcessRecordingWorker.KEY_RECORDING_ID to recordingId)
            }
        return TestListenableWorkerBuilder<ProcessRecordingWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker {
                        return ProcessRecordingWorker(appContext, workerParameters, pipeline, repository)
                    }
                },
            )
            .build()
    }

    private fun recording(
        id: String,
        path: String? = "/cache/$id.m4a",
        status: RecordingStatus = RecordingStatus.PROCESSING,
    ) = Recording(
        id = id,
        title = "Note $id",
        createdAt = Instant.ofEpochMilli(1_000),
        durationSec = 5.0,
        source = RecordingSource.MIC,
        localAudioPath = path,
        status = status,
    )

    @Test
    fun `missing recording id fails`() =
        runBlocking {
            val result = buildWorker(recordingId = null).doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `unknown recording id fails`() =
        runBlocking {
            val result = buildWorker(recordingId = "ghost").doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `recording without an audio path fails`() =
        runBlocking {
            repository.seed(recording("a", path = null))
            val result = buildWorker(recordingId = "a").doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `success persists a summary and marks the recording READY`() =
        runBlocking {
            repository.seed(recording("a"))
            val result = buildWorker(recordingId = "a").doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, repository.upsertedSummaries.size)
            assertEquals(RecordingStatus.READY, repository.getRecording("a")?.status)
        }

    @Test
    fun `pipeline failure retries and marks the recording FAILED`() =
        runBlocking {
            repository.seed(recording("a"))
            val result = buildWorker("a", pipeline(transcriber = FailingTranscriber())).doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            assertEquals(RecordingStatus.FAILED, repository.getRecording("a")?.status)
            assertTrue(repository.upsertedSummaries.isEmpty())
        }

    /** Transcriber that always throws, to drive the worker's failure branch. */
    private class FailingTranscriber : Transcriber {
        override suspend fun transcribe(audioPath: String): com.notova.core.model.Transcript = error("model crashed")
    }

    private class FakeRepository : RecordingRepository {
        private val byId = linkedMapOf<String, Recording>()
        private val summaries = linkedMapOf<String, Summary>()
        val upsertedSummaries = mutableListOf<Summary>()
        private val flow = MutableStateFlow<List<Recording>>(emptyList())

        fun seed(recording: Recording) {
            byId[recording.id] = recording
            flow.value = byId.values.toList()
        }

        override fun observeRecordings(): Flow<List<Recording>> = flow

        override suspend fun getRecording(id: String): Recording? = byId[id]

        override suspend fun upsertRecording(recording: Recording) = seed(recording)

        override suspend fun deleteRecording(id: String) {
            byId.remove(id)
        }

        override suspend fun getSummary(recordingId: String): Summary? = summaries[recordingId]

        override suspend fun upsertSummary(summary: Summary) {
            upsertedSummaries.add(summary)
            summaries[summary.recordingId] = summary
        }
    }
}
