package com.notova.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notova.ai.model.DownloadProgress
import com.notova.ai.model.InstalledModel
import com.notova.ai.model.ModelDownloader
import com.notova.ai.model.ModelStore
import com.notova.ai.summarize.GeminiNanoSummarizer
import com.notova.ai.summarize.ResolvingSummarizer
import com.notova.ai.transcribe.ResolvingTranscriber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

/** Snapshot of an in-flight model download for the Settings UI. */
data class DownloadUiState(
    val fileName: String,
    val fraction: Float,
    val inProgress: Boolean,
    val message: String? = null,
)

/** Everything the Settings screen renders about on-device AI. */
data class SettingsUiState(
    val summarizerEngine: String = "Resolving…",
    val transcriberEngine: String = "Resolving…",
    val geminiNanoStatus: String = "Unknown",
    val models: List<InstalledModel> = emptyList(),
    val download: DownloadUiState? = null,
)

/**
 * Backs the Settings screen: surfaces which summarizer/transcriber engine is currently active (and
 * why), the installed on-device models, and drives import / download / delete via [ModelStore] and
 * [ModelDownloader]. Resolving the active engine never throws — engines self-report availability.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val resolvingSummarizer: ResolvingSummarizer,
        private val resolvingTranscriber: ResolvingTranscriber,
        private val geminiNano: GeminiNanoSummarizer,
        private val modelStore: ModelStore,
        private val modelDownloader: ModelDownloader,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        /** Re-resolves active engines and reloads the installed-models list. */
        fun refresh() {
            viewModelScope.launch {
                val summarizer = runCatching { resolvingSummarizer.resolve().engineName }.getOrDefault(UNKNOWN)
                val transcriber = runCatching { resolvingTranscriber.resolve().engineName }.getOrDefault(UNKNOWN)
                val nanoStatus = runCatching { geminiNano.featureStatus().name }.getOrDefault(UNKNOWN)
                val models = runCatching { modelStore.list() }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        summarizerEngine = summarizer,
                        transcriberEngine = transcriber,
                        geminiNanoStatus = nanoStatus,
                        models = models,
                    )
                }
            }
        }

        /** Imports a picked model file (Storage Access Framework) by copying its stream in. */
        fun importModel(
            fileName: String,
            stream: InputStream,
        ) {
            viewModelScope.launch {
                runCatching { modelStore.import(fileName, stream) }
                    .onSuccess { refresh() }
                    .onFailure {
                            e ->
                        _uiState.update { it.copy(download = DownloadUiState(fileName, 0f, false, e.message)) }
                    }
            }
        }

        /** Downloads a model from [url] into the models dir, streaming progress into the UI. */
        fun downloadModel(
            url: String,
            fileName: String,
        ) {
            viewModelScope.launch {
                modelDownloader.download(url, fileName).collect { progress ->
                    when (progress) {
                        is DownloadProgress.Started ->
                            _uiState.update { it.copy(download = DownloadUiState(fileName, 0f, true)) }
                        is DownloadProgress.Running ->
                            _uiState.update {
                                it.copy(download = DownloadUiState(fileName, progress.fraction.coerceAtLeast(0f), true))
                            }
                        is DownloadProgress.Completed -> {
                            _uiState.update { it.copy(download = DownloadUiState(fileName, 1f, false, "Downloaded")) }
                            refresh()
                        }
                        is DownloadProgress.Failed ->
                            _uiState.update {
                                it.copy(
                                    download = DownloadUiState(fileName, 0f, false, progress.error.message ?: "Failed"),
                                )
                            }
                    }
                }
            }
        }

        /** Deletes an installed model by name and refreshes the list + active engines. */
        fun deleteModel(fileName: String) {
            viewModelScope.launch {
                runCatching { modelStore.delete(fileName) }
                refresh()
            }
        }

        private companion object {
            const val UNKNOWN = "Unknown"
        }
    }
