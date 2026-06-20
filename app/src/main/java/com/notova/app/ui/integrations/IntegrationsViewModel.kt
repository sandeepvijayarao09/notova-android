package com.notova.app.ui.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notova.integrations.provider.ConnectResult
import com.notova.integrations.provider.DisconnectResult
import com.notova.integrations.provider.IntegrationProvider
import com.notova.integrations.provider.IntegrationsListResult
import com.notova.integrations.provider.IntegrationsRepository
import com.notova.integrations.provider.OAuthRedirect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the Integrations screen renders. */
data class IntegrationsUiState(
    val loading: Boolean = true,
    val providers: List<IntegrationProvider> = emptyList(),
    /** Provider currently being connected/disconnected (shows an inline spinner / disables row). */
    val busyProvider: String? = null,
    /** A transient banner message (error, not-configured note, or connected confirmation). */
    val message: String? = null,
    /** Set when a connect call yields an authorize URL the screen should open in a Custom Tab. */
    val pendingAuthorize: PendingAuthorize? = null,
)

/** A URL the screen should open in a Chrome Custom Tab to start the provider's OAuth flow. */
data class PendingAuthorize(
    val provider: String,
    val authorizeUrl: String,
    val state: String,
)

/**
 * Backs the Integrations screen: lists providers with connected status, starts a connect (OAuth
 * authorize) flow by surfacing a [PendingAuthorize] URL for the screen to open in a Custom Tab,
 * handles the `notova://oauth/<provider>?status=connected` return deep link by re-listing, and
 * disconnects via DELETE. The dev-safe "provider not configured" backend error is surfaced as a
 * clear [IntegrationsUiState.message] rather than a generic failure.
 */
@HiltViewModel
class IntegrationsViewModel
    @Inject
    constructor(
        private val repository: IntegrationsRepository,
        private val redirectBus: OAuthRedirectBus,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(IntegrationsUiState())
        val uiState: StateFlow<IntegrationsUiState> = _uiState.asStateFlow()

        init {
            refresh()
            viewModelScope.launch {
                redirectBus.redirects.collectLatest { onOAuthRedirect(it) }
            }
        }

        fun refresh() {
            _uiState.update { it.copy(loading = true) }
            viewModelScope.launch {
                when (val result = repository.list()) {
                    is IntegrationsListResult.Success ->
                        _uiState.update {
                            it.copy(loading = false, providers = result.providers)
                        }
                    is IntegrationsListResult.Failure ->
                        _uiState.update {
                            it.copy(loading = false, message = result.message)
                        }
                }
            }
        }

        fun connect(provider: String) {
            _uiState.update { it.copy(busyProvider = provider, message = null) }
            viewModelScope.launch {
                when (val result = repository.connect(provider)) {
                    is ConnectResult.Authorize ->
                        _uiState.update {
                            it.copy(
                                busyProvider = null,
                                pendingAuthorize =
                                    PendingAuthorize(result.provider, result.authorizeUrl, result.state),
                            )
                        }
                    is ConnectResult.NotConfigured ->
                        _uiState.update { it.copy(busyProvider = null, message = result.message) }
                    is ConnectResult.Failure ->
                        _uiState.update { it.copy(busyProvider = null, message = result.message) }
                }
            }
        }

        /** Called by the screen once it has launched the Custom Tab, so it isn't re-launched. */
        fun onAuthorizeLaunched() {
            _uiState.update { it.copy(pendingAuthorize = null) }
        }

        fun disconnect(provider: String) {
            _uiState.update { it.copy(busyProvider = provider, message = null) }
            viewModelScope.launch {
                when (val result = repository.disconnect(provider)) {
                    is DisconnectResult.Success -> {
                        _uiState.update { it.copy(busyProvider = null, message = "Disconnected $provider.") }
                        refresh()
                    }
                    is DisconnectResult.Failure ->
                        _uiState.update { it.copy(busyProvider = null, message = result.message) }
                }
            }
        }

        /**
         * Handles the OAuth return deep link. The backend holds the authoritative connected state,
         * so a successful redirect just re-lists; a failed redirect surfaces the error message.
         */
        fun onOAuthRedirect(redirect: OAuthRedirect) {
            if (redirect.isConnected) {
                _uiState.update { it.copy(message = "Connected ${redirect.provider}.") }
                refresh()
            } else {
                val detail = redirect.errorMessage?.let { ": $it" }.orEmpty()
                _uiState.update {
                    it.copy(message = "Couldn't connect ${redirect.provider}$detail")
                }
            }
        }

        fun consumeMessage() {
            _uiState.update { it.copy(message = null) }
        }
    }
