package com.notova.feature.notes

import app.cash.turbine.test
import com.notova.core.model.ActionItem
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NoteDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun recording(id: String) =
        Recording(
            id = id,
            title = "Note $id",
            createdAt = Instant.ofEpochMilli(1_000),
            durationSec = 1.0,
            source = RecordingSource.MIC,
            localAudioPath = null,
            status = RecordingStatus.READY,
        )

    private fun summary(recordingId: String) =
        Summary(
            recordingId = recordingId,
            style = "concise",
            contentMarkdown = "## Summary",
            actionItems = listOf(ActionItem(id = "x", text = "do it", done = false)),
            model = "stub",
            generatedAt = Instant.ofEpochMilli(0),
        )

    @Test
    fun `initial state is loading with no data`() {
        val vm = NoteDetailViewModel(FakeRecordingRepository())
        val state = vm.uiState.value
        assertTrue(state.loading)
        assertNull(state.recording)
        assertNull(state.summary)
    }

    @Test
    fun `load populates recording and summary and clears loading`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository()
            repo.seedRecording(recording("a"))
            repo.seedSummary(summary("a"))
            val vm = NoteDetailViewModel(repo)

            vm.load("a")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.loading)
            assertEquals("a", state.recording?.id)
            assertEquals("## Summary", state.summary?.contentMarkdown)
            assertEquals(1, state.summary?.actionItems?.size)
        }

    @Test
    fun `load emits the loaded state via the flow`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository()
            repo.seedRecording(recording("a"))
            val vm = NoteDetailViewModel(repo)

            vm.uiState.test {
                assertTrue(awaitItem().loading) // initial
                vm.load("a")
                val loaded = awaitItem()
                assertFalse(loaded.loading)
                assertEquals("a", loaded.recording?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `load for an unknown id clears loading with null recording`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = NoteDetailViewModel(FakeRecordingRepository())

            vm.load("missing")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.loading)
            assertNull(state.recording)
            assertNull(state.summary)
        }

    @Test
    fun `load with a recording but no summary keeps summary null`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository()
            repo.seedRecording(recording("a"))
            val vm = NoteDetailViewModel(repo)

            vm.load("a")
            advanceUntilIdle()

            assertEquals("a", vm.uiState.value.recording?.id)
            assertNull(vm.uiState.value.summary)
        }
}
