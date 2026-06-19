package com.notova.feature.record

import com.notova.core.audio.AudioCaptureResult
import com.notova.core.audio.AudioSource
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.Summary
import com.notova.data.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Controllable [AudioSource] for ViewModel tests — no MediaRecorder or microphone involved. */
class FakeAudioSource(
    private val captureResult: AudioCaptureResult =
        AudioCaptureResult("/cache/rec.m4a", durationSec = 4.0, source = RecordingSource.MIC),
    private val startError: Throwable? = null,
    private val stopError: Throwable? = null,
    private val importError: Throwable? = null,
) : AudioSource {
    var startCalls = 0
        private set
    var stopCalls = 0
        private set
    var lastImportedUri: String? = null
        private set

    override suspend fun start() {
        startCalls++
        startError?.let { throw it }
    }

    override suspend fun stop(): AudioCaptureResult {
        stopCalls++
        stopError?.let { throw it }
        return captureResult
    }

    override suspend fun loadFromUri(uri: String): AudioCaptureResult {
        lastImportedUri = uri
        importError?.let { throw it }
        return captureResult.copy(source = RecordingSource.FILE)
    }
}

/** In-memory [RecordingRepository] backed by maps, with a hook to inject summary failures. */
class FakeRecordingRepository(
    private val upsertSummaryError: Throwable? = null,
) : RecordingRepository {
    val recordings = MutableStateFlow<List<Recording>>(emptyList())
    private val recordingsById = linkedMapOf<String, Recording>()
    private val summariesById = linkedMapOf<String, Summary>()

    val upsertedRecordings = mutableListOf<Recording>()
    val upsertedSummaries = mutableListOf<Summary>()

    override fun observeRecordings(): Flow<List<Recording>> = recordings

    override suspend fun getRecording(id: String): Recording? = recordingsById[id]

    override suspend fun upsertRecording(recording: Recording) {
        upsertedRecordings.add(recording)
        recordingsById[recording.id] = recording
        recordings.value = recordingsById.values.toList()
    }

    override suspend fun deleteRecording(id: String) {
        recordingsById.remove(id)
        recordings.value = recordingsById.values.toList()
    }

    override suspend fun getSummary(recordingId: String): Summary? = summariesById[recordingId]

    override suspend fun upsertSummary(summary: Summary) {
        upsertSummaryError?.let { throw it }
        upsertedSummaries.add(summary)
        summariesById[summary.recordingId] = summary
    }
}
