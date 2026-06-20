package com.notova.app.ui.auth

import com.notova.integrations.auth.AuthTokens
import com.notova.integrations.auth.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [TokenStore] for app-layer ViewModel tests. */
class FakeTokenStore(initial: AuthTokens? = null) : TokenStore {
    private val _tokens = MutableStateFlow(initial)
    override val tokens: Flow<AuthTokens?> = _tokens.asStateFlow()

    override fun accessTokenBlocking(): String? = _tokens.value?.accessToken?.takeIf { it.isNotEmpty() }

    override fun refreshTokenBlocking(): String? = _tokens.value?.refreshToken?.takeIf { it.isNotEmpty() }

    override fun isSignedInBlocking(): Boolean = accessTokenBlocking() != null

    override suspend fun save(tokens: AuthTokens) {
        _tokens.value = tokens
    }

    override suspend fun updateAccessToken(accessToken: String) {
        val current = _tokens.value
        _tokens.value = current?.copy(accessToken = accessToken) ?: AuthTokens(accessToken, "")
    }

    override suspend fun clear() {
        _tokens.value = null
    }
}
