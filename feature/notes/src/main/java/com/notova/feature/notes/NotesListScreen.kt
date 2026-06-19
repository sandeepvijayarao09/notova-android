package com.notova.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notova.core.model.Recording
import com.notova.design.component.NotovaCard

/** Test tags for the Notes list screen, used by Compose UI tests. */
object NotesListScreenTags {
    const val EMPTY_STATE = "notes_empty_state"
    const val LIST = "notes_list"

    fun item(id: String): String = "note_item_$id"
}

@Composable
fun NotesListScreen(
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()

    if (recordings.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No notes yet. Record one from the Record tab.",
                modifier = Modifier.testTag(NotesListScreenTags.EMPTY_STATE),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp).testTag(NotesListScreenTags.LIST),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(recordings, key = { it.id }) { recording ->
            NotovaCard(
                title = recording.title,
                subtitle = subtitleFor(recording),
                onClick = { onOpenNote(recording.id) },
                modifier = Modifier.testTag(NotesListScreenTags.item(recording.id)),
            )
        }
    }
}

private fun subtitleFor(recording: Recording): String =
    "${recording.status.name.lowercase()} • ${recording.source.name.lowercase()} • " +
        "${recording.durationSec.toInt()}s"
