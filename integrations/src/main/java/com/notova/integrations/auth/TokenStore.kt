package com.notova.integrations.auth

import kotlinx.coroutines.flow.Flow

/** The persisted auth token pair, or `null`/blank fields when signed out. */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Persistence boundary for the auth token pair.
 *
 * The OkHttp auth interceptor/authenticator read the access token synchronously off the network
 * thread via [accessTokenBlocking]; the rest of the app observes [tokens] reactively. Production
 * backs this with EncryptedSharedPreferences ([EncryptedTokenStore]); tests use an in-memory fake.
 */
interface TokenStore {
    /** Emits the current token pair, or `null` when signed out. */
    val tokens: Flow<AuthTokens?>

    /** Synchronous read for the OkHttp interceptor; returns `null` when signed out. */
    fun accessTokenBlocking(): String?

    /** Synchronous read for the OkHttp authenticator refresh path; returns `null` when signed out. */
    fun refreshTokenBlocking(): String?

    /** Whether a token pair is currently persisted. */
    fun isSignedInBlocking(): Boolean

    /** Persists a full token pair (sign-in / register). */
    suspend fun save(tokens: AuthTokens)

    /** Replaces only the access token (after a successful refresh). */
    suspend fun updateAccessToken(accessToken: String)

    /** Clears all tokens (sign-out). */
    suspend fun clear()
}
