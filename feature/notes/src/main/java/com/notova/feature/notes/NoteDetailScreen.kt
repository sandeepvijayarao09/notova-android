package com.notova.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun NoteDetailScreen(
    recordingId: String,
    modifier: Modifier = Modifier,
    viewModel: NoteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(recordingId) {
        viewModel.load(recordingId)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.loading -> Text("Loading…")
            state.recording == null -> Text("Note not found.")
            else -> {
                Text(
                    text = state.recording?.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                )
                val summary = state.summary
                if (summary != null) {
                    // Markdown rendered as plain text for now; swap for a Markdown renderer later.
                    Text(text = summary.contentMarkdown)
                    if (summary.actionItems.isNotEmpty()) {
                        Text(text = "Action items", style = MaterialTheme.typography.titleLarge)
                        summary.actionItems.forEach { item ->
                            Text(text = "${if (item.done) "[x]" else "[ ]"} ${item.text}")
                        }
                    }
                } else {
                    Text("No summary yet.")
                }
            }
        }
    }
}
