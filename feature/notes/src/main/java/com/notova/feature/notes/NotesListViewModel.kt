package com.notova.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notova.core.model.Recording
import com.notova.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NotesListViewModel
    @Inject
    constructor(
        repository: RecordingRepository,
    ) : ViewModel() {
        val recordings: StateFlow<List<Recording>> =
            repository.observeRecordings()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
