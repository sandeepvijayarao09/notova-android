package com.notova.data.repository

import com.notova.core.model.Recording
import com.notova.core.model.Summary
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for recordings and their summaries. */
interface RecordingRepository {
    fun observeRecordings(): Flow<List<Recording>>

    suspend fun getRecording(id: String): Recording?

    suspend fun upsertRecording(recording: Recording)

    suspend fun deleteRecording(id: String)

    suspend fun getSummary(recordingId: String): Summary?

    suspend fun upsertSummary(summary: Summary)
}
