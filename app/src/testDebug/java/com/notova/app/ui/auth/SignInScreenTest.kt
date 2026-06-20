package com.notova.app.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Compose UI coverage for the stateless [SignInContent] under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders email password and both actions`() {
        composeRule.setContent { SignInContent() }
        composeRule.onNodeWithTag(SignInScreenTags.EMAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(SignInScreenTags.PASSWORD).assertIsDisplayed()
        composeRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_BUTTON).assertIsDisplayed()
    }

    @Test
    fun `sign in button invokes the callback`() {
        var signedIn = false
        composeRule.setContent { SignInContent(onSignIn = { signedIn = true }) }
        composeRule.onNodeWithTag(SignInScreenTags.SIGN_IN_BUTTON).performClick()
        assertEquals(true, signedIn)
    }

    @Test
    fun `create account button invokes the callback`() {
        var created = false
        composeRule.setContent { SignInContent(onCreateAccount = { created = true }) }
        composeRule.onNodeWithTag(SignInScreenTags.CREATE_ACCOUNT_BUTTON).performClick()
        assertEquals(true, created)
    }

    @Test
    fun `shows an error message when present`() {
        composeRule.setContent { SignInContent(state = SignInUiState(error = "Incorrect email or password.")) }
        composeRule.onNodeWithTag(SignInScreenTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Incorrect email or password.").assertIsDisplayed()
    }

    @Test
    fun `shows a progress indicator while submitting`() {
        composeRule.setContent { SignInContent(state = SignInUiState(submitting = true)) }
        composeRule.onNodeWithTag(SignInScreenTags.PROGRESS).assertIsDisplayed()
    }
}
