package com.notova.integrations.auth

import com.notova.integrations.api.LoginRequest
import com.notova.integrations.api.NotovaBackendApi
import com.notova.integrations.api.RegisterRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of an auth attempt, surfaced to the SignIn ViewModel. */
sealed interface AuthResult {
    data object Success : AuthResult

    /** A human-readable failure (bad credentials, network, etc.). */
    data class Failure(val message: String) : AuthResult
}

/**
 * Auth boundary over [NotovaBackendApi]. Registers / logs in, persisting the returned token pair in
 * the [TokenStore]; exposes the signed-in state reactively; and clears tokens on sign-out.
 *
 * Network/backend errors are mapped to [AuthResult.Failure] with a friendly message — the raw
 * [HttpException] never leaks to the UI.
 */
@Singleton
class AuthRepository
    @Inject
    constructor(
        private val api: NotovaBackendApi,
        private val tokenStore: TokenStore,
    ) {
        /** `true` once a token pair is persisted; drives the launch route (main app vs SignIn). */
        val isSignedIn: Flow<Boolean> = tokenStore.tokens.map { it != null }

        suspend fun login(
            email: String,
            password: String,
        ): AuthResult =
            runCatching { api.login(LoginRequest(email = email.trim(), password = password)) }
                .fold(
                    onSuccess = { response ->
                        tokenStore.save(AuthTokens(response.accessToken, response.refreshToken))
                        AuthResult.Success
                    },
                    onFailure = { AuthResult.Failure(messageFor(it, signIn = true)) },
                )

        suspend fun register(
            email: String,
            password: String,
        ): AuthResult =
            runCatching { api.register(RegisterRequest(email = email.trim(), password = password)) }
                .fold(
                    onSuccess = { response ->
                        tokenStore.save(AuthTokens(response.accessToken, response.refreshToken))
                        AuthResult.Success
                    },
                    onFailure = { AuthResult.Failure(messageFor(it, signIn = false)) },
                )

        suspend fun signOut() {
            tokenStore.clear()
        }

        private fun messageFor(
            error: Throwable,
            signIn: Boolean,
        ): String =
            when {
                error is HttpException && error.code() == HTTP_UNAUTHORIZED && signIn ->
                    "Incorrect email or password."
                error is HttpException && error.code() == HTTP_CONFLICT && !signIn ->
                    "An account with that email already exists."
                error is HttpException && error.code() == HTTP_BAD_REQUEST ->
                    "Please enter a valid email and password."
                error is HttpException ->
                    "Server error (${error.code()}). Please try again."
                else ->
                    "Network error. Check your connection and try again."
            }

        private companion object {
            const val HTTP_BAD_REQUEST = 400
            const val HTTP_UNAUTHORIZED = 401
            const val HTTP_CONFLICT = 409
        }
    }
