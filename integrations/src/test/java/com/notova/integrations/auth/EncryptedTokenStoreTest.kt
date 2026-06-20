package com.notova.integrations.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round-trips the real [EncryptedTokenStore] store logic under Robolectric: save -> read (sync +
 * reactive), partial access-token update, and clear (sign-out). Backed by a real (Robolectric)
 * [android.content.SharedPreferences] — the Android Keystore that EncryptedSharedPreferences needs
 * is unavailable under Robolectric, so the storage logic is exercised against plain prefs while the
 * encryption wrapper is verified by instrumented tests / production wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EncryptedTokenStoreTest {
    private fun store(): EncryptedTokenStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_tokens_${System.nanoTime()}", Context.MODE_PRIVATE)
        return EncryptedTokenStore(prefs)
    }

    @Test
    fun `save then read returns the persisted pair synchronously and reactively`() =
        runBlocking {
            val store = store()
            store.save(AuthTokens(accessToken = "at-1", refreshToken = "rt-1"))

            assertEquals("at-1", store.accessTokenBlocking())
            assertEquals("rt-1", store.refreshTokenBlocking())
            assertTrue(store.isSignedInBlocking())
            assertEquals(AuthTokens("at-1", "rt-1"), store.tokens.first())
        }

    @Test
    fun `updateAccessToken replaces only the access token`() =
        runBlocking {
            val store = store()
            store.save(AuthTokens("at-1", "rt-1"))
            store.updateAccessToken("at-2")

            assertEquals("at-2", store.accessTokenBlocking())
            assertEquals("rt-1", store.refreshTokenBlocking())
        }

    @Test
    fun `clear removes all tokens and reports signed out`() =
        runBlocking {
            val store = store()
            store.save(AuthTokens("at-1", "rt-1"))
            store.clear()

            assertNull(store.accessTokenBlocking())
            assertNull(store.refreshTokenBlocking())
            assertFalse(store.isSignedInBlocking())
            assertNull(store.tokens.first())
        }

    @Test
    fun `a fresh store starts signed out`() {
        val store = store()
        assertFalse(store.isSignedInBlocking())
        assertNull(store.accessTokenBlocking())
    }

    @Test
    fun `tokens persist across store instances over the same prefs`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefs = context.getSharedPreferences("shared_tokens", Context.MODE_PRIVATE)
            EncryptedTokenStore(prefs).save(AuthTokens("persisted", "refresh"))

            // A new store over the same prefs reads the persisted pair on construction.
            val reopened = EncryptedTokenStore(prefs)
            assertEquals("persisted", reopened.accessTokenBlocking())
            assertEquals(AuthTokens("persisted", "refresh"), reopened.tokens.first())
        }
}
