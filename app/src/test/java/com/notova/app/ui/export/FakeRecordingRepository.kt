package com.notova.app.ui.export

import com.notova.core.model.Recording
import com.notova.core.model.Summary
import com.notova.data.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [RecordingRepository] for the export ViewModel test. */
class FakeRecordingRepository : RecordingRepository {
    private val recordingsById = linkedMapOf<String, Recording>()
    private val summariesById = linkedMapOf<String, Summary>()
    private val recordings = MutableStateFlow<List<Recording>>(emptyList())

    fun seedRecording(recording: Recording) {
        recordingsById[recording.id] = recording
        recordings.value = recordingsById.values.toList()
    }

    fun seedSummary(summary: Summary) {
        summariesById[summary.recordingId] = summary
    }

    override fun observeRecordings(): Flow<List<Recording>> = recordings

    override suspend fun getRecording(id: String): Recording? = recordingsById[id]

    override suspend fun upsertRecording(recording: Recording) = seedRecording(recording)

    override suspend fun deleteRecording(id: String) {
        recordingsById.remove(id)
    }

    override suspend fun getSummary(recordingId: String): Summary? = summariesById[recordingId]

    override suspend fun upsertSummary(summary: Summary) = seedSummary(summary)
}
