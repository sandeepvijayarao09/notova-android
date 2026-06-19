package com.notova.feature.notes

import app.cash.turbine.test
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotesListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun recording(
        id: String,
        createdAtMs: Long = 1_000,
    ) = Recording(
        id = id,
        title = "Note $id",
        createdAt = Instant.ofEpochMilli(createdAtMs),
        durationSec = 1.0,
        source = RecordingSource.MIC,
        localAudioPath = null,
        status = RecordingStatus.READY,
    )

    @Test
    fun `initial recordings value is empty`() {
        val repo = FakeRecordingRepository()
        val vm = NotesListViewModel(repo)
        assertTrue(vm.recordings.value.isEmpty())
    }

    @Test
    fun `recordings reflect the repository stream once collected`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository()
            repo.seedRecording(recording("a"))
            val vm = NotesListViewModel(repo)

            vm.recordings.test {
                // First emission is the initial empty value, then the seeded list.
                assertTrue(awaitItem().isEmpty())
                assertEquals(listOf("a"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `new recordings flow through to the state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository()
            val vm = NotesListViewModel(repo)

            vm.recordings.test {
                assertTrue(awaitItem().isEmpty())
                repo.seedRecording(recording("a"))
                assertEquals(listOf("a"), awaitItem().map { it.id })
                repo.seedRecording(recording("b"))
                assertEquals(setOf("a", "b"), awaitItem().map { it.id }.toSet())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recordings list mirrors repository ordering`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository()
            repo.recordings.value = listOf(recording("new", 3_000), recording("old", 1_000))
            val vm = NotesListViewModel(repo)

            vm.recordings.test {
                awaitItem() // initial empty
                assertEquals(listOf("new", "old"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
            advanceUntilIdle()
        }
}
