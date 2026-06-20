package com.notova.integrations.provider

import com.notova.integrations.api.IntegrationDto
import com.notova.integrations.api.NotovaBackendApi
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** A provider and whether the signed-in user has connected it. */
data class IntegrationProvider(
    val provider: String,
    val connected: Boolean,
)

/** Result of listing the available integrations. */
sealed interface IntegrationsListResult {
    data class Success(val providers: List<IntegrationProvider>) : IntegrationsListResult

    data class Failure(val message: String) : IntegrationsListResult
}

/** Result of starting a connect (OAuth authorize) flow for a provider. */
sealed interface ConnectResult {
    /** The backend returned an authorize URL to open in a Custom Tab, plus the CSRF [state]. */
    data class Authorize(
        val provider: String,
        val authorizeUrl: String,
        val state: String,
    ) : ConnectResult

    /**
     * Dev-safe path: the backend reports the provider's OAuth app isn't configured. Surfaced
     * distinctly so the UI can explain it rather than showing a generic failure.
     */
    data class NotConfigured(
        val provider: String,
        val message: String,
    ) : ConnectResult

    data class Failure(
        val provider: String,
        val message: String,
    ) : ConnectResult
}

/** Result of disconnecting a provider. */
sealed interface DisconnectResult {
    data object Success : DisconnectResult

    data class Failure(val message: String) : DisconnectResult
}

/**
 * Integrations boundary over [NotovaBackendApi]: lists providers with connected status, starts a
 * connect (OAuth authorize) flow, and disconnects. Backend errors are mapped to friendly messages;
 * the dev-safe "provider not configured" case is surfaced as a distinct [ConnectResult.NotConfigured]
 * so the UI can explain it clearly instead of as a generic failure.
 */
@Singleton
class IntegrationsRepository
    @Inject
    constructor(
        private val api: NotovaBackendApi,
    ) {
        suspend fun list(): IntegrationsListResult =
            runCatching { api.listIntegrations() }
                .fold(
                    onSuccess = { dtos -> IntegrationsListResult.Success(dtos.map { it.toProvider() }) },
                    onFailure = { IntegrationsListResult.Failure(genericMessage(it)) },
                )

        suspend fun connect(provider: String): ConnectResult =
            runCatching { api.connectIntegration(provider) }
                .fold(
                    onSuccess = { response ->
                        ConnectResult.Authorize(
                            provider = provider,
                            authorizeUrl = response.authorizeUrl,
                            state = response.state,
                        )
                    },
                    onFailure = { error ->
                        if (isProviderNotConfigured(error)) {
                            ConnectResult.NotConfigured(
                                provider = provider,
                                message =
                                    "“$provider” isn't configured on this backend yet. " +
                                        "OAuth credentials need to be set server-side before it can be connected.",
                            )
                        } else {
                            ConnectResult.Failure(provider, genericMessage(error))
                        }
                    },
                )

        suspend fun disconnect(provider: String): DisconnectResult =
            runCatching { api.disconnectIntegration(provider) }
                .fold(
                    onSuccess = { response ->
                        if (response.disconnected) {
                            DisconnectResult.Success
                        } else {
                            DisconnectResult.Failure("Couldn't disconnect $provider.")
                        }
                    },
                    onFailure = { DisconnectResult.Failure(genericMessage(it)) },
                )

        /**
         * Detects the dev-safe "provider not configured" backend signal. The backend returns a 4xx
         * (typically 501/400/409) whose error body mentions configuration; we match on the body text
         * to stay robust to the exact status code.
         */
        private fun isProviderNotConfigured(error: Throwable): Boolean {
            if (error !is HttpException) return false
            if (error.code() == HTTP_NOT_IMPLEMENTED) return true
            val body = errorBody(error)
            return body.contains("not configured", ignoreCase = true) ||
                body.contains("not_configured", ignoreCase = true) ||
                body.contains("provider_not_configured", ignoreCase = true)
        }

        private fun errorBody(error: HttpException): String =
            runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty()

        private fun genericMessage(error: Throwable): String =
            when {
                error is HttpException && error.code() == HTTP_UNAUTHORIZED ->
                    "Your session expired. Please sign in again."
                error is HttpException ->
                    "Server error (${error.code()}). Please try again."
                else ->
                    "Network error. Check your connection and try again."
            }

        private fun IntegrationDto.toProvider(): IntegrationProvider =
            IntegrationProvider(provider = provider, connected = connected)

        private companion object {
            const val HTTP_UNAUTHORIZED = 401
            const val HTTP_NOT_IMPLEMENTED = 501
        }
    }
