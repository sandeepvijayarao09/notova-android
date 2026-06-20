package com.notova.app.ui.integrations

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.notova.integrations.provider.IntegrationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Compose UI coverage for the stateless [IntegrationsContent] under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntegrationsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows a loading indicator while listing`() {
        composeRule.setContent { IntegrationsContent(state = IntegrationsUiState(loading = true)) }
        composeRule.onNodeWithTag(IntegrationsScreenTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun `shows the empty state when no providers are available`() {
        composeRule.setContent {
            IntegrationsContent(state = IntegrationsUiState(loading = false, providers = emptyList()))
        }
        composeRule.onNodeWithTag(IntegrationsScreenTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun `renders a connect action for a disconnected provider`() {
        composeRule.setContent {
            IntegrationsContent(
                state =
                    IntegrationsUiState(
                        loading = false,
                        providers = listOf(IntegrationProvider("notion", connected = false)),
                    ),
            )
        }
        composeRule.onNodeWithTag(IntegrationsScreenTags.connect("notion")).assertIsDisplayed()
    }

    @Test
    fun `renders a disconnect action for a connected provider`() {
        composeRule.setContent {
            IntegrationsContent(
                state =
                    IntegrationsUiState(
                        loading = false,
                        providers = listOf(IntegrationProvider("notion", connected = true)),
                    ),
            )
        }
        composeRule.onNodeWithTag(IntegrationsScreenTags.disconnect("notion")).assertIsDisplayed()
    }

    @Test
    fun `connect action invokes the callback with the provider`() {
        var connected: String? = null
        composeRule.setContent {
            IntegrationsContent(
                state =
                    IntegrationsUiState(
                        loading = false,
                        providers = listOf(IntegrationProvider("todoist", connected = false)),
                    ),
                onConnect = { connected = it },
            )
        }
        composeRule.onNodeWithTag(IntegrationsScreenTags.connect("todoist")).performClick()
        assertEquals("todoist", connected)
    }

    @Test
    fun `shows a busy indicator for the provider being connected`() {
        composeRule.setContent {
            IntegrationsContent(
                state =
                    IntegrationsUiState(
                        loading = false,
                        providers = listOf(IntegrationProvider("notion", connected = false)),
                        busyProvider = "notion",
                    ),
            )
        }
        composeRule.onNodeWithTag(IntegrationsScreenTags.busy("notion")).assertIsDisplayed()
    }

    @Test
    fun `surfaces a banner message such as the not-configured note`() {
        composeRule.setContent {
            IntegrationsContent(
                state =
                    IntegrationsUiState(
                        loading = false,
                        providers = emptyList(),
                        message = "“slack” isn't configured on this backend yet.",
                    ),
            )
        }
        composeRule.onNodeWithTag(IntegrationsScreenTags.MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithText("“slack” isn't configured on this backend yet.").assertIsDisplayed()
    }
}
