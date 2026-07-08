package com.notova.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.notova.core.pipeline.RecordingProcessingScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecordingProcessingScheduler] backed by WorkManager: enqueues [ProcessRecordingWorker] as unique
 * work keyed by the recording id, so processing runs (and retries) in the background regardless of
 * UI lifecycle and is never double-queued for the same recording.
 */
@Singleton
class WorkManagerRecordingScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RecordingProcessingScheduler {
        override fun schedule(recordingId: String) {
            val request =
                OneTimeWorkRequestBuilder<ProcessRecordingWorker>()
                    .setInputData(workDataOf(ProcessRecordingWorker.KEY_RECORDING_ID to recordingId))
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_WORK_PREFIX$recordingId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private companion object {
            const val UNIQUE_WORK_PREFIX = "process_recording_"
        }
    }
