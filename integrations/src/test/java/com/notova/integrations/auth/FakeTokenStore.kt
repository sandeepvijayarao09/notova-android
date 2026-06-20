package com.notova.integrations.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [TokenStore] for unit tests. Mirrors [EncryptedTokenStore] semantics without I/O. */
class FakeTokenStore(initial: AuthTokens? = null) : TokenStore {
    private val _tokens = MutableStateFlow(initial)
    override val tokens: Flow<AuthTokens?> = _tokens.asStateFlow()

    /** Counts how many times [clear] ran, so tests can assert sign-out happened (once).*/
    var clearCount: Int = 0
        private set

    override fun accessTokenBlocking(): String? = _tokens.value?.accessToken?.takeIf { it.isNotEmpty() }

    override fun refreshTokenBlocking(): String? = _tokens.value?.refreshToken?.takeIf { it.isNotEmpty() }

    override fun isSignedInBlocking(): Boolean = accessTokenBlocking() != null

    override suspend fun save(tokens: AuthTokens) {
        _tokens.value = tokens
    }

    override suspend fun updateAccessToken(accessToken: String) {
        val current = _tokens.value
        _tokens.value =
            if (current == null) {
                AuthTokens(accessToken = accessToken, refreshToken = "")
            } else {
                current.copy(accessToken = accessToken)
            }
    }

    override suspend fun clear() {
        clearCount++
        _tokens.value = null
    }
}
