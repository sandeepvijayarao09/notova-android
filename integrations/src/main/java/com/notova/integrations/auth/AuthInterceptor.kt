package com.notova.integrations.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <accessToken>` to every outgoing request when a token is present.
 *
 * Auth-bootstrap routes (register / login / refresh) carry no token yet, so a missing token simply
 * means the header is omitted — the request still goes out. The 401 refresh-then-sign-out dance is
 * handled separately by [TokenAuthenticator] (an OkHttp [okhttp3.Authenticator]).
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenStore.accessTokenBlocking()
        if (token.isNullOrEmpty() || request.header(HEADER) != null) {
            return chain.proceed(request)
        }
        val authed =
            request.newBuilder()
                .header(HEADER, "$PREFIX$token")
                .build()
        return chain.proceed(authed)
    }

    private companion object {
        const val HEADER = "Authorization"
        const val PREFIX = "Bearer "
    }
}
