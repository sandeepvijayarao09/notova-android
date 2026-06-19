package com.notova.feature.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.notova.core.model.ActionItem
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Compose UI coverage for [NoteDetailScreen] under Robolectric: loaded title + markdown + action
 * items, the no-summary state, and the not-found state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun recording(id: String) =
        Recording(
            id = id,
            title = "Quarterly review",
            createdAt = Instant.ofEpochMilli(1_000),
            durationSec = 1.0,
            source = RecordingSource.MIC,
            localAudioPath = null,
            status = RecordingStatus.READY,
        )

    private fun summary(id: String) =
        Summary(
            recordingId = id,
            style = "concise",
            contentMarkdown = "## Summary body text",
            actionItems = listOf(ActionItem(id = "x", text = "Send the deck", done = false)),
            model = "stub",
            generatedAt = Instant.ofEpochMilli(0),
        )

    @Test
    fun `renders title markdown and action items for a loaded note`() {
        val repo = FakeRecordingRepository()
        repo.seedRecording(recording("a"))
        repo.seedSummary(summary("a"))
        val vm = NoteDetailViewModel(repo)

        composeRule.setContent { NoteDetailScreen(recordingId = "a", viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Quarterly review").assertIsDisplayed()
        composeRule.onNodeWithText("## Summary body text").assertIsDisplayed()
        composeRule.onNodeWithText("Action items").assertIsDisplayed()
        composeRule.onNodeWithText("[ ] Send the deck").assertIsDisplayed()
    }

    @Test
    fun `renders the no-summary state when only a recording exists`() {
        val repo = FakeRecordingRepository()
        repo.seedRecording(recording("a"))
        val vm = NoteDetailViewModel(repo)

        composeRule.setContent { NoteDetailScreen(recordingId = "a", viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Quarterly review").assertIsDisplayed()
        composeRule.onNodeWithText("No summary yet.").assertIsDisplayed()
    }

    @Test
    fun `renders the not-found state for an unknown recording`() {
        val vm = NoteDetailViewModel(FakeRecordingRepository())

        composeRule.setContent { NoteDetailScreen(recordingId = "missing", viewModel = vm) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Note not found.").assertIsDisplayed()
    }
}
