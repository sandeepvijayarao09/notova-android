package com.notova.feature.record

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.notova.core.pipeline.PipelineUseCase
import com.notova.core.summarize.StubSummarizer
import com.notova.core.transcribe.StubTranscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI coverage for [RecordScreen] running on the JVM via Robolectric. Verifies the idle
 * control set, the record/stop transition driven by [RecordViewModel] state, and the import button.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(audio: FakeAudioSource = FakeAudioSource()) =
        RecordViewModel(
            audio,
            PipelineUseCase(StubTranscriber(), StubSummarizer()),
            FakeRecordingRepository(),
        )

    @Test
    fun `idle state shows record control and import button`() {
        composeRule.setContent { RecordScreen(viewModel = viewModel()) }

        composeRule.onNodeWithTag(RecordScreenTags.RECORD_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(RecordScreenTags.IMPORT_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithText("Ready to capture").assertIsDisplayed()
    }

    @Test
    fun `tapping record transitions the control to stop`() {
        val vm = viewModel()
        composeRule.setContent { RecordScreen(viewModel = vm) }

        composeRule.onNodeWithTag(RecordScreenTags.RECORD_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RecordScreenTags.STOP_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithText("Recording…").assertIsDisplayed()
    }

    @Test
    fun `the brand title is shown`() {
        composeRule.setContent { RecordScreen(viewModel = viewModel()) }
        composeRule.onNodeWithText("Notova").assertIsDisplayed()
    }
}
