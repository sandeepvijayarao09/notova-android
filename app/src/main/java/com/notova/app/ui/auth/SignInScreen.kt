package com.notova.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Test tags for the SignIn screen controls, used by Compose UI tests. */
object SignInScreenTags {
    const val EMAIL = "signin_email"
    const val PASSWORD = "signin_password"
    const val SIGN_IN_BUTTON = "signin_sign_in_button"
    const val CREATE_ACCOUNT_BUTTON = "signin_create_account_button"
    const val ERROR = "signin_error"
    const val PROGRESS = "signin_progress"
}

/**
 * Sign-in / create-account screen. Email + password, with Sign In and Create Account actions that
 * call the backend via the [AuthViewModel]. On success the auth route advances reactively and the
 * host swaps to the main app; on failure a friendly error is shown.
 */
@Composable
fun SignInScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignInContent(
        state = state,
        modifier = modifier,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignIn = viewModel::signIn,
        onCreateAccount = viewModel::createAccount,
    )
}

/** Stateless SignIn content, rendered without a Hilt graph for Robolectric/preview. */
@Composable
fun SignInContent(
    state: SignInUiState = SignInUiState(),
    modifier: Modifier = Modifier,
    onEmailChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onSignIn: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Notova", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Sign in to sync metadata and connect integrations. Your audio and AI stay on-device.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag(SignInScreenTags.EMAIL),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            enabled = !state.submitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag(SignInScreenTags.PASSWORD),
        )

        if (state.error != null) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().testTag(SignInScreenTags.ERROR),
            )
        }

        if (state.submitting) {
            CircularProgressIndicator(modifier = Modifier.testTag(SignInScreenTags.PROGRESS))
        }

        Button(
            onClick = onSignIn,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().testTag(SignInScreenTags.SIGN_IN_BUTTON),
        ) {
            Text("Sign In")
        }

        OutlinedButton(
            onClick = onCreateAccount,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().testTag(SignInScreenTags.CREATE_ACCOUNT_BUTTON),
        ) {
            Text("Create Account")
        }
    }
}
