package com.notova.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notova.core.audio.AudioCaptureResult
import com.notova.core.audio.AudioSource
import com.notova.core.audio.RecordingForegroundController
import com.notova.core.model.Recording
import com.notova.core.model.RecordingStatus
import com.notova.core.pipeline.PipelineUseCase
import com.notova.core.pipeline.RecordingProcessingScheduler
import com.notova.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

enum class RecordPhase { IDLE, RECORDING, PROCESSING, QUEUED, DONE, ERROR }

data class RecordUiState(
    val phase: RecordPhase = RecordPhase.IDLE,
    val lastRecordingId: String? = null,
    val message: String? = null,
)

/**
 * Drives the Record screen end-to-end:
 * capture -> persist (PROCESSING) -> [PipelineUseCase] -> persist summary + mark READY.
 * If no transcription engine is available the recording is kept, marked FAILED, and the user is
 * told why; nothing is invented.
 */
@HiltViewModel
class RecordViewModel
    @Inject
    constructor(
        private val audioSource: AudioSource,
        private val pipeline: PipelineUseCase,
        private val repository: RecordingRepository,
        private val scheduler: RecordingProcessingScheduler,
        private val foregroundController: RecordingForegroundController,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecordUiState())
        val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

        fun startRecording() {
            viewModelScope.launch {
                // Bring up the microphone foreground service first so capture keeps running (with an
                // ongoing notification) if the app is backgrounded; tear it down again if start fails.
                foregroundController.start()
                runCatching { audioSource.start() }
                    .onSuccess { _uiState.update { it.copy(phase = RecordPhase.RECORDING, message = null) } }
                    .onFailure { e ->
                        foregroundController.stop()
                        fail(e)
                    }
            }
        }

        fun stopRecording() {
            viewModelScope.launch {
                val outcome = runCatching { audioSource.stop() }
                foregroundController.stop()
                outcome
                    .onSuccess { result -> processCapture(result) }
                    .onFailure { e -> fail(e) }
            }
        }

        /**
         * Imports a file (potentially a long one) and hands processing to a background worker via
         * [scheduler]. Unlike live capture, the transcription is not run inline: a long import can
         * take minutes, so it must survive the user navigating away. The note appears in Notes (as
         * PROCESSING, then READY) once the worker finishes.
         */
        fun importFile(uri: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(phase = RecordPhase.PROCESSING, message = null) }
                runCatching { audioSource.loadFromUri(uri) }
                    .onSuccess { result -> queueForProcessing(result) }
                    .onFailure { e -> fail(e) }
            }
        }

        private suspend fun queueForProcessing(capture: AudioCaptureResult) {
            val recording = newProcessingRecording(capture)
            repository.upsertRecording(recording)
            scheduler.schedule(recording.id)
            _uiState.update {
                it.copy(
                    phase = RecordPhase.QUEUED,
                    lastRecordingId = recording.id,
                    message = "Importing in the background — it'll appear in Notes when ready",
                )
            }
        }

        private suspend fun processCapture(capture: AudioCaptureResult) {
            _uiState.update { it.copy(phase = RecordPhase.PROCESSING) }
            val recording = newProcessingRecording(capture)
            repository.upsertRecording(recording)

            runCatching {
                val finished = pipeline.process(capture.outputFilePath)
                repository.upsertSummary(finished.summary)
                repository.upsertRecording(recording.copy(status = RecordingStatus.READY))
            }.onSuccess {
                _uiState.update {
                    it.copy(phase = RecordPhase.DONE, lastRecordingId = recording.id, message = "Note ready")
                }
            }.onFailure { e ->
                repository.upsertRecording(recording.copy(status = RecordingStatus.FAILED))
                fail(e)
            }
        }

        private fun newProcessingRecording(capture: AudioCaptureResult): Recording {
            val now = Instant.now()
            return Recording(
                id = UUID.randomUUID().toString(),
                title = "Note ${now.epochSecond}",
                createdAt = now,
                durationSec = capture.durationSec,
                source = capture.source,
                localAudioPath = capture.outputFilePath,
                status = RecordingStatus.PROCESSING,
            )
        }

        private fun fail(e: Throwable) {
            _uiState.update {
                it.copy(phase = RecordPhase.ERROR, message = e.message ?: "Something went wrong")
            }
        }

        fun reset() {
            _uiState.update { RecordUiState() }
        }
    }
