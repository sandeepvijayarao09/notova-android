package com.notova.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notova.core.model.RecordingStatus
import com.notova.core.pipeline.PipelineUseCase
import com.notova.data.repository.RecordingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that runs the on-device pipeline for a recording and persists the result.
 * Lets capture and processing be decoupled (e.g. queue long imports for later).
 */
@HiltWorker
class ProcessRecordingWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val pipeline: PipelineUseCase,
        private val repository: RecordingRepository,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val recordingId = inputData.getString(KEY_RECORDING_ID) ?: return Result.failure()
            val recording = repository.getRecording(recordingId) ?: return Result.failure()
            val audioPath = recording.localAudioPath ?: return Result.failure()

            return runCatching {
                val finished = pipeline.process(audioPath)
                repository.upsertSummary(finished.summary)
                repository.upsertRecording(recording.copy(status = RecordingStatus.READY))
            }.fold(
                onSuccess = { Result.success() },
                onFailure = {
                    repository.upsertRecording(recording.copy(status = RecordingStatus.FAILED))
                    Result.retry()
                },
            )
        }

        companion object {
            const val KEY_RECORDING_ID = "recording_id"
        }
    }
