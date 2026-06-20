package com.notova.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notova.integrations.auth.AuthRepository
import com.notova.integrations.auth.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the app should route on launch / after auth changes. */
enum class AuthRoute {
    /** Still determining whether a token exists. */
    LOADING,

    /** No token — show SignIn. */
    SIGNED_OUT,

    /** A token exists — show the main app. */
    SIGNED_IN,
}

/** Everything the SignIn screen renders. */
data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

/**
 * Backs the SignIn screen and the launch routing decision.
 *
 * [route] is derived from the [AuthRepository] signed-in flow: it starts [AuthRoute.LOADING] and
 * settles to [AuthRoute.SIGNED_IN] / [AuthRoute.SIGNED_OUT] once the token store reports. Login /
 * register success persists the token pair (advancing [route] reactively); failure surfaces a
 * friendly message in [uiState]. Sign-out clears the token store.
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SignInUiState())
        val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

        // Set when the user chooses "Continue without an account". The on-device
        // experience (record/transcribe/summarize) works fully offline; only
        // integrations + metadata sync require an account.
        private val offlineOverride = MutableStateFlow(false)

        val route: StateFlow<AuthRoute> =
            combine(authRepository.isSignedIn, offlineOverride) { signedIn, offline ->
                if (signedIn || offline) AuthRoute.SIGNED_IN else AuthRoute.SIGNED_OUT
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AuthRoute.LOADING,
            )

        /** Enter the app without signing in (on-device features only). */
        fun continueOffline() {
            offlineOverride.value = true
        }

        fun onEmailChange(value: String) {
            _uiState.update { it.copy(email = value, error = null) }
        }

        fun onPasswordChange(value: String) {
            _uiState.update { it.copy(password = value, error = null) }
        }

        fun signIn() = submit(register = false)

        fun createAccount() = submit(register = true)

        fun signOut() {
            offlineOverride.value = false
            viewModelScope.launch { authRepository.signOut() }
        }

        private fun submit(register: Boolean) {
            val state = _uiState.value
            val validationError = validate(state.email, state.password)
            if (validationError != null) {
                _uiState.update { it.copy(error = validationError) }
                return
            }
            _uiState.update { it.copy(submitting = true, error = null) }
            viewModelScope.launch {
                val result =
                    if (register) {
                        authRepository.register(state.email, state.password)
                    } else {
                        authRepository.login(state.email, state.password)
                    }
                when (result) {
                    is AuthResult.Success ->
                        // route advances reactively via the signed-in flow; just clear the form.
                        _uiState.update { it.copy(submitting = false, password = "", error = null) }
                    is AuthResult.Failure ->
                        _uiState.update { it.copy(submitting = false, error = result.message) }
                }
            }
        }

        private fun validate(
            email: String,
            password: String,
        ): String? =
            when {
                email.isBlank() -> "Enter your email."
                !email.contains('@') -> "Enter a valid email."
                password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters."
                else -> null
            }

        private companion object {
            const val MIN_PASSWORD_LENGTH = 8
        }
    }
