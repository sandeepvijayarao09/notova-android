package com.notova.feature.record

import app.cash.turbine.test
import com.notova.core.audio.AudioCaptureResult
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.pipeline.PipelineUseCase
import com.notova.core.summarize.StubSummarizer
import com.notova.core.transcribe.StubTranscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * State-machine coverage for [RecordViewModel] using fake audio + repository and the real stub
 * pipeline. Asserts the full idle -> recording -> processing -> done sequence (and error paths)
 * via Turbine, plus the side effects persisted to the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun pipeline() = PipelineUseCase(StubTranscriber(), StubSummarizer())

    private fun viewModel(
        audioSource: FakeAudioSource = FakeAudioSource(),
        repository: FakeRecordingRepository = FakeRecordingRepository(),
        pipeline: PipelineUseCase = pipeline(),
    ) = RecordViewModel(audioSource, pipeline, repository)

    @Test
    fun `initial state is idle`() {
        val state = viewModel().uiState.value
        assertEquals(RecordPhase.IDLE, state.phase)
        assertNull(state.lastRecordingId)
        assertNull(state.message)
    }

    @Test
    fun `startRecording moves to RECORDING and starts the audio source`() =
        runTest(mainDispatcherRule.dispatcher) {
            val audio = FakeAudioSource()
            val vm = viewModel(audioSource = audio)

            vm.startRecording()
            advanceUntilIdle()

            assertEquals(RecordPhase.RECORDING, vm.uiState.value.phase)
            assertEquals(1, audio.startCalls)
        }

    @Test
    fun `startRecording failure transitions to ERROR with a message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val audio = FakeAudioSource(startError = IllegalStateException("mic busy"))
            val vm = viewModel(audioSource = audio)

            vm.startRecording()
            advanceUntilIdle()

            assertEquals(RecordPhase.ERROR, vm.uiState.value.phase)
            assertEquals("mic busy", vm.uiState.value.message)
        }

    @Test
    fun `full capture flow emits idle recording processing done`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository()
            val vm = viewModel(repository = repo)

            vm.uiState.test {
                assertEquals(RecordPhase.IDLE, awaitItem().phase)

                vm.startRecording()
                assertEquals(RecordPhase.RECORDING, awaitItem().phase)

                vm.stopRecording()
                assertEquals(RecordPhase.PROCESSING, awaitItem().phase)

                val done = awaitItem()
                assertEquals(RecordPhase.DONE, done.phase)
                assertNotNull(done.lastRecordingId)
                assertEquals("Note ready", done.message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `successful capture persists a PROCESSING then READY recording and a summary`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository()
            val vm = viewModel(repository = repo)

            vm.startRecording()
            advanceUntilIdle()
            vm.stopRecording()
            advanceUntilIdle()

            // First persisted recording is PROCESSING, last is READY.
            assertEquals(RecordingStatus.PROCESSING, repo.upsertedRecordings.first().status)
            assertEquals(RecordingStatus.READY, repo.upsertedRecordings.last().status)
            assertEquals(1, repo.upsertedSummaries.size)
        }

    @Test
    fun `stop failure before processing transitions to ERROR`() =
        runTest(mainDispatcherRule.dispatcher) {
            val audio = FakeAudioSource(stopError = RuntimeException("stop failed"))
            val vm = viewModel(audioSource = audio)

            vm.startRecording()
            advanceUntilIdle()
            vm.stopRecording()
            advanceUntilIdle()

            assertEquals(RecordPhase.ERROR, vm.uiState.value.phase)
            assertEquals("stop failed", vm.uiState.value.message)
        }

    @Test
    fun `pipeline failure marks the recording FAILED and surfaces ERROR`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository(upsertSummaryError = RuntimeException("summarize boom"))
            val vm = viewModel(repository = repo)

            vm.startRecording()
            advanceUntilIdle()
            vm.stopRecording()
            advanceUntilIdle()

            assertEquals(RecordPhase.ERROR, vm.uiState.value.phase)
            assertEquals("summarize boom", vm.uiState.value.message)
            // The recording was marked FAILED.
            assertEquals(RecordingStatus.FAILED, repo.upsertedRecordings.last().status)
        }

    @Test
    fun `importFile triggers the pipeline and reaches DONE`() =
        runTest(mainDispatcherRule.dispatcher) {
            val audio = FakeAudioSource()
            val repo = FakeRecordingRepository()
            val vm = viewModel(audioSource = audio, repository = repo)

            vm.uiState.test {
                assertEquals(RecordPhase.IDLE, awaitItem().phase)

                vm.importFile("content://audio/123")
                assertEquals(RecordPhase.PROCESSING, awaitItem().phase)

                val done = awaitItem()
                assertEquals(RecordPhase.DONE, done.phase)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals("content://audio/123", audio.lastImportedUri)
            assertEquals(1, repo.upsertedSummaries.size)
        }

    @Test
    fun `imported recording is persisted with FILE source`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeRecordingRepository()
            val vm = viewModel(repository = repo)

            vm.importFile("content://audio/123")
            advanceUntilIdle()

            assertTrue(repo.upsertedRecordings.any { it.source == RecordingSource.FILE })
        }

    @Test
    fun `import failure transitions to ERROR`() =
        runTest(mainDispatcherRule.dispatcher) {
            val audio = FakeAudioSource(importError = IllegalArgumentException("cannot open uri"))
            val vm = viewModel(audioSource = audio)

            vm.importFile("content://bad")
            advanceUntilIdle()

            assertEquals(RecordPhase.ERROR, vm.uiState.value.phase)
            assertEquals("cannot open uri", vm.uiState.value.message)
        }

    @Test
    fun `reset returns to a fresh idle state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()
            vm.startRecording()
            advanceUntilIdle()
            assertEquals(RecordPhase.RECORDING, vm.uiState.value.phase)

            vm.reset()

            assertEquals(RecordPhase.IDLE, vm.uiState.value.phase)
            assertNull(vm.uiState.value.lastRecordingId)
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun `capture duration and source are carried into the persisted recording`() =
        runTest(mainDispatcherRule.dispatcher) {
            val audio =
                FakeAudioSource(
                    captureResult =
                        AudioCaptureResult("/cache/x.m4a", durationSec = 9.5, source = RecordingSource.BLUETOOTH),
                )
            val repo = FakeRecordingRepository()
            val vm = viewModel(audioSource = audio, repository = repo)

            vm.startRecording()
            advanceUntilIdle()
            vm.stopRecording()
            advanceUntilIdle()

            val persisted = repo.upsertedRecordings.first()
            assertEquals(9.5, persisted.durationSec, 0.0)
            assertEquals(RecordingSource.BLUETOOTH, persisted.source)
            assertEquals("/cache/x.m4a", persisted.localAudioPath)
        }
}
