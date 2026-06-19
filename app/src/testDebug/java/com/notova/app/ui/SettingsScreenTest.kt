package com.notova.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.notova.ai.model.InstalledModel
import com.notova.ai.model.ModelCapability
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI coverage for the self-contained [SettingsContent] under Robolectric. The Hilt-backed
 * [SettingsScreen] wrapper that resolves its ViewModel via `hiltViewModel()` is exercised by
 * instrumented tests; the stateless content is tested here with hand-built [SettingsUiState].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the settings heading`() {
        composeRule.setContent { SettingsContent() }
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun `states that account integrations and billing live here`() {
        composeRule.setContent { SettingsContent() }
        composeRule.onNodeWithText("Account, integrations, and billing live here.").assertIsDisplayed()
    }

    @Test
    fun `reassures that AI runs on-device`() {
        composeRule.setContent { SettingsContent() }
        composeRule
            .onNodeWithText("All transcription and summarization runs fully on-device.")
            .assertIsDisplayed()
    }

    @Test
    fun `shows the active summarizer and transcriber engines`() {
        composeRule.setContent {
            SettingsContent(
                state =
                    SettingsUiState(
                        summarizerEngine = "Built-in (template)",
                        transcriberEngine = "Built-in (placeholder)",
                        geminiNanoStatus = "UNAVAILABLE",
                    ),
            )
        }
        composeRule.onNodeWithTag(SettingsScreenTags.SUMMARIZER_ENGINE).assertIsDisplayed()
        composeRule.onNodeWithText("Summarizer: Built-in (template)").assertIsDisplayed()
        composeRule.onNodeWithText("Transcriber: Built-in (placeholder)").assertIsDisplayed()
        composeRule.onNodeWithText("Gemini Nano: UNAVAILABLE").assertIsDisplayed()
    }

    @Test
    fun `shows the no-models message when nothing is installed`() {
        composeRule.setContent { SettingsContent(state = SettingsUiState(models = emptyList())) }
        composeRule.onNodeWithTag(SettingsScreenTags.NO_MODELS).assertIsDisplayed()
    }

    @Test
    fun `lists installed models with a delete control`() {
        val model =
            InstalledModel(
                name = "gemma.task",
                path = "/models/gemma.task",
                sizeBytes = 5_000_000,
                capability = ModelCapability.GEMMA_SUMMARIZER,
            )
        composeRule.setContent { SettingsContent(state = SettingsUiState(models = listOf(model))) }
        composeRule.onNodeWithTag(SettingsScreenTags.MODELS_LIST).assertIsDisplayed()
        composeRule.onNodeWithText("gemma.task").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsScreenTags.deleteModel("gemma.task")).assertIsDisplayed()
    }

    @Test
    fun `delete control invokes the callback with the model name`() {
        var deleted: String? = null
        val model =
            InstalledModel("m.task", "/models/m.task", 1_000_000, ModelCapability.GEMMA_SUMMARIZER)
        composeRule.setContent {
            SettingsContent(state = SettingsUiState(models = listOf(model)), onDelete = { deleted = it })
        }
        composeRule.onNodeWithTag(SettingsScreenTags.deleteModel("m.task")).performClick()
        assertEquals("m.task", deleted)
    }

    @Test
    fun `import button invokes the import callback`() {
        var imported = false
        composeRule.setContent { SettingsContent(onImport = { imported = true }) }
        composeRule.onNodeWithTag(SettingsScreenTags.IMPORT_MODEL_BUTTON).performClick()
        assertEquals(true, imported)
    }

    @Test
    fun `download progress is shown while a download is in flight`() {
        composeRule.setContent {
            SettingsContent(
                state =
                    SettingsUiState(
                        download = DownloadUiState(fileName = "gemma.task", fraction = 0.5f, inProgress = true),
                    ),
            )
        }
        composeRule.onNodeWithTag(SettingsScreenTags.DOWNLOAD_PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithText("Downloading gemma.task: 50%").assertIsDisplayed()
    }
}
