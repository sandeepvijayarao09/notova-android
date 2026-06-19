package com.notova.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notova.core.audio.AudioCaptureResult
import com.notova.core.audio.AudioSource
import com.notova.core.model.Recording
import com.notova.core.model.RecordingStatus
import com.notova.core.pipeline.PipelineUseCase
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

enum class RecordPhase { IDLE, RECORDING, PROCESSING, DONE, ERROR }

data class RecordUiState(
    val phase: RecordPhase = RecordPhase.IDLE,
    val lastRecordingId: String? = null,
    val message: String? = null,
)

/**
 * Drives the Record screen end-to-end with the current stub pipeline:
 * capture -> persist (PROCESSING) -> [PipelineUseCase] -> persist summary + mark READY.
 */
@HiltViewModel
class RecordViewModel
    @Inject
    constructor(
        private val audioSource: AudioSource,
        private val pipeline: PipelineUseCase,
        private val repository: RecordingRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecordUiState())
        val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

        fun startRecording() {
            viewModelScope.launch {
                runCatching { audioSource.start() }
                    .onSuccess { _uiState.update { it.copy(phase = RecordPhase.RECORDING, message = null) } }
                    .onFailure { e -> fail(e) }
            }
        }

        fun stopRecording() {
            viewModelScope.launch {
                runCatching { audioSource.stop() }
                    .onSuccess { result -> processCapture(result) }
                    .onFailure { e -> fail(e) }
            }
        }

        fun importFile(uri: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(phase = RecordPhase.PROCESSING, message = null) }
                runCatching { audioSource.loadFromUri(uri) }
                    .onSuccess { result -> processCapture(result) }
                    .onFailure { e -> fail(e) }
            }
        }

        private suspend fun processCapture(capture: AudioCaptureResult) {
            _uiState.update { it.copy(phase = RecordPhase.PROCESSING) }
            val id = UUID.randomUUID().toString()
            val now = Instant.now()
            val recording =
                Recording(
                    id = id,
                    title = "Note ${now.epochSecond}",
                    createdAt = now,
                    durationSec = capture.durationSec,
                    source = capture.source,
                    localAudioPath = capture.outputFilePath,
                    status = RecordingStatus.PROCESSING,
                )
            repository.upsertRecording(recording)

            runCatching {
                val finished = pipeline.process(capture.outputFilePath)
                repository.upsertSummary(finished.summary)
                repository.upsertRecording(recording.copy(status = RecordingStatus.READY))
            }.onSuccess {
                _uiState.update {
                    it.copy(phase = RecordPhase.DONE, lastRecordingId = id, message = "Note ready")
                }
            }.onFailure { e ->
                repository.upsertRecording(recording.copy(status = RecordingStatus.FAILED))
                fail(e)
            }
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
