package com.notova.integrations.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit client for the Notova backend `/v1` contract.
 *
 * The backend handles ONLY accounts, OAuth brokering, metadata sync, and billing.
 * It NEVER performs AI compute — transcription and summarization stay fully on-device.
 *
 * Protected routes expect `Authorization: Bearer <accessToken>`; register/login/refresh do not.
 * The auth interceptor is wired in [com.notova.integrations.di.IntegrationsModule].
 */
interface NotovaBackendApi {
    // ---- Auth ----

    @POST("v1/auth/register")
    suspend fun register(
        @Body body: RegisterRequest,
    ): AuthResponse

    @POST("v1/auth/login")
    suspend fun login(
        @Body body: LoginRequest,
    ): AuthResponse

    @POST("v1/auth/refresh")
    suspend fun refresh(
        @Body body: RefreshRequest,
    ): RefreshResponse

    @GET("v1/auth/me")
    suspend fun me(): MeResponse

    // ---- Integrations ----

    @GET("v1/integrations")
    suspend fun listIntegrations(): List<IntegrationDto>

    @GET("v1/integrations/{provider}/connect")
    suspend fun connectIntegration(
        @Path("provider") provider: String,
    ): ConnectResponse

    @POST("v1/integrations/{provider}/export")
    suspend fun exportToIntegration(
        @Path("provider") provider: String,
        @Body body: ExportRequest,
    ): ExportResponse

    @DELETE("v1/integrations/{provider}")
    suspend fun disconnectIntegration(
        @Path("provider") provider: String,
    ): DisconnectResponse

    // ---- Sync ----

    @GET("v1/sync/recordings")
    suspend fun getRecordings(
        @Query("since") since: String? = null,
    ): List<SyncRecordingDto>

    @PUT("v1/sync/recordings/{id}")
    suspend fun upsertRecording(
        @Path("id") id: String,
        @Body body: SyncRecordingUpsertRequest,
    ): SyncUpsertResponse

    // ---- Billing ----

    @GET("v1/billing/subscription")
    suspend fun getSubscription(): SubscriptionResponse

    @POST("v1/billing/checkout")
    suspend fun checkout(
        @Body body: CheckoutRequest,
    ): CheckoutResponse
}
