package com.notova.app.ui

import com.notova.ai.model.ModelDownloader
import com.notova.ai.model.ModelStore
import com.notova.ai.summarize.GeminiNanoSummarizer
import com.notova.ai.summarize.GenAiFeatureStatus
import com.notova.ai.summarize.MlKitSummarizationEngine
import com.notova.ai.summarize.ResolvingSummarizer
import com.notova.ai.summarize.StubSummarizerEngine
import com.notova.ai.summarize.SummarizerEngine
import com.notova.ai.transcribe.ResolvingTranscriber
import com.notova.ai.transcribe.StubTranscriberEngine
import com.notova.core.summarize.StubSummarizer
import com.notova.core.transcribe.StubTranscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Coverage for [SettingsViewModel]: it surfaces the resolved active engines + Gemini Nano status,
 * lists installed models, and imports / deletes models via a temp-dir [ModelStore]. Uses real
 * resolvers with controllable fake engines so engine selection is asserted end-to-end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeMlKit(private val status: GenAiFeatureStatus) : MlKitSummarizationEngine {
        override suspend fun checkFeatureStatus() = status

        override suspend fun ensureDownloaded() = false

        override suspend fun summarize(text: String) = "summary"
    }

    private fun viewModel(
        nanoStatus: GenAiFeatureStatus = GenAiFeatureStatus.UNAVAILABLE,
    ): Pair<SettingsViewModel, ModelStore> {
        // Drive the store/downloader on the test dispatcher so advanceUntilIdle() awaits their work.
        val dispatcher = mainDispatcherRule.dispatcher
        val store = ModelStore(File(temp.root, "models"), dispatcher)
        val nano = GeminiNanoSummarizer(FakeMlKit(nanoStatus))
        // No Gemma model installed + nano unavailable -> resolver should pick the stub engine.
        val summarizer =
            ResolvingSummarizer(
                listOf<SummarizerEngine>(nano, StubSummarizerEngine(StubSummarizer())),
            )
        val transcriber =
            ResolvingTranscriber(listOf(StubTranscriberEngine(StubTranscriber())))
        val downloader = ModelDownloader(OkHttpClient.Builder().build(), store, dispatcher)
        return SettingsViewModel(summarizer, transcriber, nano, store, downloader) to store
    }

    @Test
    fun `initial refresh resolves the stub engines when nothing else is available`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (vm, _) = viewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(StubSummarizerEngine.ENGINE_NAME, state.summarizerEngine)
            assertEquals(StubTranscriberEngine.ENGINE_NAME, state.transcriberEngine)
        }

    @Test
    fun `surfaces the Gemini Nano feature status`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (vm, _) = viewModel(nanoStatus = GenAiFeatureStatus.DOWNLOADABLE)
            advanceUntilIdle()
            assertEquals("DOWNLOADABLE", vm.uiState.value.geminiNanoStatus)
        }

    @Test
    fun `lists no models for a fresh store`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (vm, _) = viewModel()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.models.isEmpty())
        }

    @Test
    fun `importModel adds the model to the listed state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (vm, _) = viewModel()
            advanceUntilIdle()

            vm.importModel("gemma.task", ByteArrayInputStream("weights".toByteArray()))
            advanceUntilIdle()

            assertEquals(1, vm.uiState.value.models.size)
            assertEquals("gemma.task", vm.uiState.value.models.first().name)
        }

    @Test
    fun `deleteModel removes the model from the listed state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (vm, store) = viewModel()
            store.import("gemma.task", ByteArrayInputStream("w".toByteArray()))
            vm.refresh()
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.models.size)

            vm.deleteModel("gemma.task")
            advanceUntilIdle()

            assertTrue(vm.uiState.value.models.isEmpty())
        }
}
