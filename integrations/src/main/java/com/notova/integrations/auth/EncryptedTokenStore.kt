package com.notova.integrations.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [TokenStore] backed by [SharedPreferences]. In production the prefs are AndroidX Security
 * [EncryptedSharedPreferences] (AES-256 GCM, key in the Android Keystore) built via [create], so
 * tokens are at-rest encrypted on disk. Reads are synchronous (the prefs are in-memory cached),
 * which is what the OkHttp interceptor/authenticator need on the network thread; an in-memory
 * [StateFlow] mirror powers reactive observation for the UI.
 *
 * The store logic is decoupled from the encryption so it can be unit-tested against a plain
 * Robolectric [SharedPreferences] (the Android Keystore is unavailable under Robolectric).
 */
class EncryptedTokenStore internal constructor(
    private val prefs: SharedPreferences,
) : TokenStore {
    private val _tokens = MutableStateFlow(readTokens())
    override val tokens: StateFlow<AuthTokens?> = _tokens.asStateFlow()

    override fun accessTokenBlocking(): String? = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotEmpty() }

    override fun refreshTokenBlocking(): String? = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotEmpty() }

    override fun isSignedInBlocking(): Boolean = accessTokenBlocking() != null

    override suspend fun save(tokens: AuthTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .commit()
        _tokens.value = tokens
    }

    override suspend fun updateAccessToken(accessToken: String) {
        prefs.edit().putString(KEY_ACCESS, accessToken).commit()
        _tokens.value = readTokens()
    }

    override suspend fun clear() {
        prefs.edit().clear().commit()
        _tokens.value = null
    }

    private fun readTokens(): AuthTokens? {
        val access = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotEmpty() } ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null).orEmpty()
        return AuthTokens(accessToken = access, refreshToken = refresh)
    }

    companion object {
        private const val FILE_NAME = "notova_auth_tokens"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"

        /** Builds an EncryptedSharedPreferences-backed store. Blocking; call off the main thread. */
        fun create(context: Context): EncryptedTokenStore {
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            return EncryptedTokenStore(prefs)
        }
    }
}
