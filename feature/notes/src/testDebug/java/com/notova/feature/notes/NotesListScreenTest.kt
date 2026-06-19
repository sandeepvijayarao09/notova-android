package com.notova.feature.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Compose UI coverage for [NotesListScreen] under Robolectric: empty state, item rendering, and the
 * tap-to-open callback wiring.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotesListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun recording(
        id: String,
        title: String = "Note $id",
    ) = Recording(
        id = id,
        title = title,
        createdAt = Instant.ofEpochMilli(1_000),
        durationSec = 7.0,
        source = RecordingSource.MIC,
        localAudioPath = null,
        status = RecordingStatus.READY,
    )

    @Test
    fun `empty repository shows the empty state`() {
        val vm = NotesListViewModel(FakeRecordingRepository())
        composeRule.setContent { NotesListScreen(onOpenNote = {}, viewModel = vm) }

        composeRule.onNodeWithTag(NotesListScreenTags.EMPTY_STATE).assertIsDisplayed()
        composeRule.onNodeWithText("No notes yet. Record one from the Record tab.").assertIsDisplayed()
    }

    @Test
    fun `recordings render as titled cards`() {
        val repo = FakeRecordingRepository()
        repo.seedRecording(recording("a", title = "Standup"))
        repo.seedRecording(recording("b", title = "Retro"))
        val vm = NotesListViewModel(repo)

        composeRule.setContent { NotesListScreen(onOpenNote = {}, viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Standup").assertIsDisplayed()
        composeRule.onNodeWithText("Retro").assertIsDisplayed()
        composeRule.onNodeWithTag(NotesListScreenTags.item("a")).assertIsDisplayed()
    }

    @Test
    fun `tapping a note invokes onOpenNote with its id`() {
        val repo = FakeRecordingRepository()
        repo.seedRecording(recording("a", title = "Standup"))
        val vm = NotesListViewModel(repo)
        var opened: String? = null

        composeRule.setContent { NotesListScreen(onOpenNote = { opened = it }, viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(NotesListScreenTags.item("a")).performClick()
        composeRule.waitForIdle()

        assertEquals("a", opened)
    }
}
