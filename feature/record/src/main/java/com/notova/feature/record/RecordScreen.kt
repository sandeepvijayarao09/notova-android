package com.notova.feature.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Test tags for the Record screen controls, used by Compose UI tests. */
object RecordScreenTags {
    const val PHASE_LABEL = "record_phase_label"
    const val RECORD_BUTTON = "record_button"
    const val STOP_BUTTON = "record_stop_button"
    const val IMPORT_BUTTON = "record_import_button"
    const val PROGRESS = "record_progress"
}

/**
 * Record tab. Drives capture/import through [RecordViewModel] so the full on-device pipeline
 * (currently stubbed) runs and persists a finished note.
 */
@Composable
fun RecordScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.importFile(it.toString()) }
        }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Notova", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text(text = phaseLabel(state.phase), modifier = Modifier.testTag(RecordScreenTags.PHASE_LABEL))
        state.message?.let { Text(text = it) }

        when (state.phase) {
            RecordPhase.PROCESSING -> CircularProgressIndicator(modifier = Modifier.testTag(RecordScreenTags.PROGRESS))
            RecordPhase.RECORDING -> {
                Button(onClick = viewModel::stopRecording, modifier = Modifier.testTag(RecordScreenTags.STOP_BUTTON)) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("Stop")
                }
            }
            else -> {
                Button(
                    onClick = viewModel::startRecording,
                    modifier = Modifier.testTag(RecordScreenTags.RECORD_BUTTON),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Text("Record")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("audio/*")) },
                    modifier = Modifier.testTag(RecordScreenTags.IMPORT_BUTTON),
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Text("Import audio file")
                }
            }
        }
    }
}

private fun phaseLabel(phase: RecordPhase): String =
    when (phase) {
        RecordPhase.IDLE -> "Ready to capture"
        RecordPhase.RECORDING -> "Recording…"
        RecordPhase.PROCESSING -> "Transcribing & summarizing on-device…"
        RecordPhase.DONE -> "Saved to Notes"
        RecordPhase.ERROR -> "Error"
    }
