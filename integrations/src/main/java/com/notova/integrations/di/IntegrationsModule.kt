package com.notova.integrations.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.notova.integrations.BuildConfig
import com.notova.integrations.api.NotovaBackendApi
import com.notova.integrations.api.RefreshRequest
import com.notova.integrations.auth.AuthInterceptor
import com.notova.integrations.auth.EncryptedTokenStore
import com.notova.integrations.auth.TokenAuthenticator
import com.notova.integrations.auth.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the bare (no auth) Retrofit/OkHttp used only for the token-refresh call. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshClient

/** Marks the bare (logging-only) OkHttp client that the authenticated client is built from. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseClient

@Module
@InstallIn(SingletonComponent::class)
object IntegrationsModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    /**
     * Bare client: logging only, NO auth interceptor/authenticator. Used both as the base for the
     * authenticated client and standalone (qualified) for the refresh call, so a 401 on refresh
     * can't recurse back through the authenticator.
     */
    @Provides
    @Singleton
    @BaseClient
    fun provideOkHttpClient(): OkHttpClient {
        val logging =
            HttpLoggingInterceptor().apply {
                level =
                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
            }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideTokenStore(
        @ApplicationContext context: Context,
    ): TokenStore = EncryptedTokenStore.create(context)

    /** The bare client + Retrofit used solely for `POST v1/auth/refresh`. */
    @Provides
    @Singleton
    @RefreshClient
    fun provideRefreshApi(
        @BaseClient baseClient: OkHttpClient,
        json: Json,
    ): NotovaBackendApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(baseClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NotovaBackendApi::class.java)
    }

    /** Authenticated client: bearer interceptor + 401 refresh-once authenticator. */
    @Provides
    @Singleton
    fun provideAuthenticatedOkHttpClient(
        @BaseClient baseClient: OkHttpClient,
        tokenStore: TokenStore,
        @RefreshClient refreshApi: NotovaBackendApi,
    ): OkHttpClient {
        val authenticator =
            TokenAuthenticator(
                tokenStore = tokenStore,
                refresh = { refreshToken ->
                    runCatching { refreshApi.refresh(RefreshRequest(refreshToken)).accessToken }.getOrNull()
                },
                onSignOut = { runBlocking { tokenStore.clear() } },
            )
        return baseClient.newBuilder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(authenticator)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideNotovaBackendApi(retrofit: Retrofit): NotovaBackendApi = retrofit.create(NotovaBackendApi::class.java)
}
