package com.notova.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notova.core.model.Recording
import com.notova.core.model.Summary
import com.notova.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteDetailUiState(
    val recording: Recording? = null,
    val summary: Summary? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class NoteDetailViewModel
    @Inject
    constructor(
        private val repository: RecordingRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(NoteDetailUiState())
        val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

        fun load(recordingId: String) {
            viewModelScope.launch {
                val recording = repository.getRecording(recordingId)
                val summary = repository.getSummary(recordingId)
                _uiState.update {
                    it.copy(recording = recording, summary = summary, loading = false)
                }
            }
        }
    }
