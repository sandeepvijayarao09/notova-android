package com.notova.integrations.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Reacts to a `401 Unauthorized` from a protected route: tries to refresh the access token exactly
 * once, and on success replays the original request with the new bearer. If the request already
 * carried a freshly-refreshed token (i.e. we are looping), or the refresh fails, it clears the
 * token store (sign-out) and gives up by returning `null`.
 *
 * Refresh is delegated to [refresh], a suspend lambda that calls `POST v1/auth/refresh` through a
 * SEPARATE OkHttp client WITHOUT this authenticator wired in — otherwise a 401 on the refresh call
 * would recurse. Returning `null` from [refresh] is treated as a failed refresh.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refresh: suspend (refreshToken: String) -> String?,
    private val onSignOut: suspend () -> Unit = { tokenStore.clear() },
) : Authenticator {
    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        // Refresh at most once: if the failing request already carried the current token AND we
        // have already retried (priorResponse present), don't loop — sign out instead.
        if (responseCount(response) >= MAX_ATTEMPTS) {
            runBlocking { onSignOut() }
            return null
        }

        val currentRefresh = tokenStore.refreshTokenBlocking()
        if (currentRefresh.isNullOrEmpty()) {
            runBlocking { onSignOut() }
            return null
        }

        val newAccessToken =
            runBlocking {
                runCatching { refresh(currentRefresh) }.getOrNull()
            }

        if (newAccessToken.isNullOrEmpty()) {
            runBlocking { onSignOut() }
            return null
        }

        runBlocking { tokenStore.updateAccessToken(newAccessToken) }
        return response.request.newBuilder()
            .header(HEADER, "$PREFIX$newAccessToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val HEADER = "Authorization"
        const val PREFIX = "Bearer "
        const val MAX_ATTEMPTS = 2
    }
}
