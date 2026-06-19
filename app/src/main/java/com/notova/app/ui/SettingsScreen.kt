package com.notova.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notova.ai.model.InstalledModel
import com.notova.design.component.NotovaCard

/** Test tags for the Settings screen controls, used by Compose UI tests. */
object SettingsScreenTags {
    const val SUMMARIZER_ENGINE = "settings_summarizer_engine"
    const val TRANSCRIBER_ENGINE = "settings_transcriber_engine"
    const val GEMINI_NANO_STATUS = "settings_gemini_nano_status"
    const val IMPORT_MODEL_BUTTON = "settings_import_model_button"
    const val DOWNLOAD_MODEL_BUTTON = "settings_download_model_button"
    const val DOWNLOAD_PROGRESS = "settings_download_progress"
    const val MODELS_LIST = "settings_models_list"
    const val NO_MODELS = "settings_no_models"

    fun deleteModel(name: String): String = "settings_delete_model_$name"
}

/**
 * A small Gemma model offered for one-tap download when only the stub summarizer is available.
 * Deliberately a modest CPU `.task` bundle (a few hundred MB), not a multi-GB asset.
 */
private const val SAMPLE_MODEL_URL =
    "https://storage.googleapis.com/notova-models/gemma-2b-it-cpu-int4.task"
private const val SAMPLE_MODEL_NAME = "gemma-2b-it-cpu-int4.task"

/**
 * Settings tab. Surfaces the currently active on-device engines (and why), plus model management:
 * import via the Storage Access Framework, download with progress, list and delete.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                val name = it.lastPathSegment?.substringAfterLast('/') ?: "imported.task"
                context.contentResolver.openInputStream(it)?.let { stream ->
                    viewModel.importModel(name, stream)
                }
            }
        }

    SettingsContent(
        state = state,
        modifier = modifier,
        onImport = { importLauncher.launch(arrayOf("*/*")) },
        onDownload = { viewModel.downloadModel(SAMPLE_MODEL_URL, SAMPLE_MODEL_NAME) },
        onDelete = viewModel::deleteModel,
    )
}

/**
 * Stateless Settings content. Split out so it renders without a Hilt graph (Robolectric/preview).
 */
@Composable
fun SettingsContent(
    state: SettingsUiState = SettingsUiState(),
    modifier: Modifier = Modifier,
    onImport: () -> Unit = {},
    onDownload: () -> Unit = {},
    onDelete: (String) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
        Text(text = "Account, integrations, and billing live here.")
        Text(text = "All transcription and summarization runs fully on-device.")

        Text(text = "On-device AI", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Summarizer: ${state.summarizerEngine}",
            modifier = Modifier.testTag(SettingsScreenTags.SUMMARIZER_ENGINE),
        )
        Text(
            text = "Transcriber: ${state.transcriberEngine}",
            modifier = Modifier.testTag(SettingsScreenTags.TRANSCRIBER_ENGINE),
        )
        Text(
            text = "Gemini Nano: ${state.geminiNanoStatus}",
            modifier = Modifier.testTag(SettingsScreenTags.GEMINI_NANO_STATUS),
        )

        Text(text = "Models", style = MaterialTheme.typography.titleMedium)
        Text(text = "Add a Gemma model to enable higher-quality on-device summaries.")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.testTag(SettingsScreenTags.IMPORT_MODEL_BUTTON),
            ) {
                Text("Import model")
            }
            Button(
                onClick = onDownload,
                modifier = Modifier.testTag(SettingsScreenTags.DOWNLOAD_MODEL_BUTTON),
            ) {
                Text("Download Gemma")
            }
        }

        state.download?.let { dl ->
            if (dl.inProgress) {
                LinearProgressIndicator(
                    progress = { dl.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().testTag(SettingsScreenTags.DOWNLOAD_PROGRESS),
                )
                Text(text = "Downloading ${dl.fileName}: ${(dl.fraction * 100).toInt()}%")
            } else if (dl.message != null) {
                Text(text = "${dl.fileName}: ${dl.message}")
            }
        }

        if (state.models.isEmpty()) {
            Text(
                text = "No models installed. Using the built-in engines.",
                modifier = Modifier.testTag(SettingsScreenTags.NO_MODELS),
            )
        } else {
            Column(
                modifier = Modifier.testTag(SettingsScreenTags.MODELS_LIST),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.models.forEach { model -> ModelRow(model = model, onDelete = onDelete) }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: InstalledModel,
    onDelete: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotovaCard(
            title = model.name,
            subtitle = "${model.capability.name.lowercase()} • ${model.sizeBytes / BYTES_PER_MB} MB",
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onDelete(model.name) },
            modifier = Modifier.testTag(SettingsScreenTags.deleteModel(model.name)),
        ) {
            Text("Delete")
        }
    }
}

private const val BYTES_PER_MB = 1_000_000L
