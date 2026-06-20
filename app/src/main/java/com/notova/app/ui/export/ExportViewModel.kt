package com.notova.app.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notova.core.model.Recording
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import com.notova.data.repository.RecordingRepository
import com.notova.integrations.provider.ExportRepository
import com.notova.integrations.provider.ExportResult
import com.notova.integrations.provider.IntegrationProvider
import com.notova.integrations.provider.IntegrationsListResult
import com.notova.integrations.provider.IntegrationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Outcome of an export attempt, surfaced to the NoteDetail UI. */
sealed interface ExportOutcome {
    data class Success(
        val provider: String,
        val externalId: String,
        val url: String?,
    ) : ExportOutcome

    data class Error(val message: String) : ExportOutcome
}

/** State for the "Export to…" flow on NoteDetail. */
data class ExportUiState(
    val pickerOpen: Boolean = false,
    val loadingProviders: Boolean = false,
    /** Connected providers the note can be exported to. */
    val connectedProviders: List<IntegrationProvider> = emptyList(),
    /** Provider currently being exported to (shows progress / disables the picker). */
    val exportingProvider: String? = null,
    val outcome: ExportOutcome? = null,
    /** Set when the provider list couldn't be loaded or none are connected. */
    val pickerMessage: String? = null,
)

/**
 * Backs the "Export to…" action on NoteDetail. Opens a provider picker (filtered to connected
 * providers from the backend), then exports the on-device [Recording] + [Summary] to the chosen
 * provider via [ExportRepository], mapping the result/error for display. Transcripts aren't
 * persisted on-device, so an empty [Transcript] keyed to the recording is sent (mirroring the
 * existing exporter) — the summary markdown + action items are the exported payload.
 */
@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        private val recordingRepository: RecordingRepository,
        private val integrationsRepository: IntegrationsRepository,
        private val exportRepository: ExportRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ExportUiState())
        val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

        /** Opens the provider picker and loads the connected providers. */
        fun openPicker() {
            _uiState.update {
                it.copy(pickerOpen = true, loadingProviders = true, pickerMessage = null, outcome = null)
            }
            viewModelScope.launch {
                when (val result = integrationsRepository.list()) {
                    is IntegrationsListResult.Success -> {
                        val connected = result.providers.filter { p -> p.connected }
                        _uiState.update {
                            it.copy(
                                loadingProviders = false,
                                connectedProviders = connected,
                                pickerMessage =
                                    if (connected.isEmpty()) {
                                        "No connected providers. Connect one in Settings → Integrations first."
                                    } else {
                                        null
                                    },
                            )
                        }
                    }
                    is IntegrationsListResult.Failure ->
                        _uiState.update {
                            it.copy(loadingProviders = false, pickerMessage = result.message)
                        }
                }
            }
        }

        fun dismissPicker() {
            _uiState.update { it.copy(pickerOpen = false) }
        }

        fun consumeOutcome() {
            _uiState.update { it.copy(outcome = null) }
        }

        /** Exports the note identified by [recordingId] to [provider]. */
        fun export(
            recordingId: String,
            provider: String,
        ) {
            _uiState.update { it.copy(exportingProvider = provider, outcome = null) }
            viewModelScope.launch {
                val recording = recordingRepository.getRecording(recordingId)
                val summary = recordingRepository.getSummary(recordingId)
                if (recording == null || summary == null) {
                    _uiState.update {
                        it.copy(
                            exportingProvider = null,
                            pickerOpen = false,
                            outcome = ExportOutcome.Error("This note isn't ready to export yet."),
                        )
                    }
                    return@launch
                }
                val result =
                    exportRepository.export(
                        provider = provider,
                        recording = recording,
                        summary = summary,
                        transcript = emptyTranscript(recordingId),
                    )
                _uiState.update {
                    it.copy(
                        exportingProvider = null,
                        pickerOpen = false,
                        outcome = result.toOutcome(),
                    )
                }
            }
        }

        private fun ExportResult.toOutcome(): ExportOutcome =
            when (this) {
                is ExportResult.Success ->
                    ExportOutcome.Success(provider = provider, externalId = externalId, url = url)
                is ExportResult.Failure -> ExportOutcome.Error(message)
            }

        /** Transcripts aren't persisted; export the summary with an empty transcript shell. */
        private fun emptyTranscript(recordingId: String): Transcript =
            Transcript(
                recordingId = recordingId,
                language = "",
                fullText = "",
                segments = emptyList(),
            )
    }
