package com.notova.app.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notova.feature.notes.NoteDetailScreen
import com.notova.integrations.provider.IntegrationProvider

/** Test tags for the note-detail export controls, used by Compose UI tests. */
object NoteExportTags {
    const val EXPORT_ACTION = "note_export_action"
    const val PICKER = "note_export_picker"
    const val PICKER_LOADING = "note_export_picker_loading"
    const val PICKER_MESSAGE = "note_export_picker_message"
    const val OUTCOME = "note_export_outcome"

    fun provider(provider: String): String = "note_export_provider_$provider"
}

/**
 * Hosts the feature [NoteDetailScreen] and overlays the "Export to…" action. Lives in `:app` (not
 * `:feature:notes`) because export depends on `:integrations`, which the feature module doesn't.
 * Tapping export opens a provider picker (connected providers only); choosing one exports the note.
 */
@Composable
fun NoteDetailWithExport(
    recordingId: String,
    modifier: Modifier = Modifier,
    exportViewModel: ExportViewModel = hiltViewModel(),
) {
    val state by exportViewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        NoteDetailScreen(recordingId = recordingId)

        ExtendedFloatingActionButton(
            onClick = { exportViewModel.openPicker() },
            icon = { Icon(Icons.Filled.Share, contentDescription = null) },
            text = { Text("Export to…") },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag(NoteExportTags.EXPORT_ACTION),
        )
    }

    if (state.pickerOpen) {
        ExportPickerDialog(
            state = state,
            onDismiss = { exportViewModel.dismissPicker() },
            onPick = { provider -> exportViewModel.export(recordingId, provider) },
        )
    }

    state.outcome?.let { outcome ->
        ExportOutcomeDialog(outcome = outcome, onDismiss = { exportViewModel.consumeOutcome() })
    }
}

@Composable
private fun ExportPickerDialog(
    state: ExportUiState,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(NoteExportTags.PICKER),
        title = { Text("Export to…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    state.loadingProviders ->
                        Text(
                            text = "Loading providers…",
                            modifier = Modifier.testTag(NoteExportTags.PICKER_LOADING),
                        )
                    state.pickerMessage != null ->
                        Text(
                            text = state.pickerMessage,
                            modifier = Modifier.testTag(NoteExportTags.PICKER_MESSAGE),
                        )
                    else ->
                        state.connectedProviders.forEach { provider ->
                            ProviderChoice(
                                provider = provider,
                                exporting = state.exportingProvider == provider.provider,
                                onPick = onPick,
                            )
                        }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ProviderChoice(
    provider: IntegrationProvider,
    exporting: Boolean,
    onPick: (String) -> Unit,
) {
    Button(
        onClick = { onPick(provider.provider) },
        enabled = !exporting,
        modifier = Modifier.fillMaxWidth().testTag(NoteExportTags.provider(provider.provider)),
    ) {
        if (exporting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text(provider.provider)
        }
    }
}

@Composable
private fun ExportOutcomeDialog(
    outcome: ExportOutcome,
    onDismiss: () -> Unit,
) {
    val (title, body) =
        when (outcome) {
            is ExportOutcome.Success ->
                "Exported to ${outcome.provider}" to
                    buildString {
                        append("Saved as ${outcome.externalId}.")
                        outcome.url?.let { append("\n$it") }
                    }
            is ExportOutcome.Error -> "Export failed" to outcome.message
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(NoteExportTags.OUTCOME),
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
