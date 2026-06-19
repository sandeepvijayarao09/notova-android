package com.notova.integrations.api

import kotlinx.serialization.Serializable

/*
 * DTOs for the Notova backend `/v1` contract.
 *
 * The backend speaks JSON with camelCase field names and ISO-8601 date strings (with offset).
 * These mirror the contract shapes exactly; on-device domain models are mapped onto them by the
 * exporter/sync callers, never the other way around.
 */

// ---- Auth ----

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/** Authenticated user. `createdAt` is an ISO-8601 string with offset. */
@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val createdAt: String,
)

/** Response for register/login: full user plus a fresh token pair. */
@Serializable
data class AuthResponse(
    val user: UserDto,
    val accessToken: String,
    val refreshToken: String,
)

/** Response for refresh: only a new access token is issued. */
@Serializable
data class RefreshResponse(val accessToken: String)

/** Response for `GET v1/auth/me`: the current user wrapped in `{user}`. */
@Serializable
data class MeResponse(val user: UserDto)

// ---- Integrations ----

@Serializable
data class IntegrationDto(
    val provider: String,
    val connected: Boolean,
)

@Serializable
data class ConnectResponse(
    val authorizeUrl: String,
    val state: String,
)

/** Recording metadata as sent on export. Dates are ISO-8601 with offset; duration is seconds. */
@Serializable
data class RecordingDto(
    val id: String,
    val title: String,
    val createdAt: String,
    val durationSec: Double,
    val source: String,
    val status: String,
)

@Serializable
data class ActionItemDto(
    val id: String? = null,
    val text: String,
    val done: Boolean,
    val dueAt: String? = null,
)

@Serializable
data class SummaryDto(
    val text: String,
    val bullets: List<String>? = null,
    val actionItems: List<ActionItemDto>? = null,
)

/** A transcript segment. Offsets are in seconds (the on-device model stores ms; convert ms->sec). */
@Serializable
data class TranscriptSegmentDto(
    val startSec: Double? = null,
    val endSec: Double? = null,
    val speaker: String? = null,
    val text: String,
)

@Serializable
data class TranscriptDto(
    val text: String,
    val segments: List<TranscriptSegmentDto>? = null,
    val language: String? = null,
)

@Serializable
data class ExportRequest(
    val recording: RecordingDto,
    val summary: SummaryDto,
    val transcript: TranscriptDto,
)

/** Export result. `status` is one of "exported" | "queued" | "skipped". */
@Serializable
data class ExportResponse(
    val externalId: String,
    val url: String? = null,
    val status: String,
)

@Serializable
data class DisconnectResponse(val disconnected: Boolean)

// ---- Sync ----

/** Recording metadata exchanged with the metadata sync endpoints. */
@Serializable
data class SyncRecordingDto(
    val id: String,
    val title: String,
    val createdAt: String,
    val durationSec: Double,
    val source: String,
    val status: String,
)

/**
 * Upsert body for `PUT v1/sync/recordings/{id}`. Carries ONLY metadata — never audio, transcript,
 * or summary content. The id is the path param, so it is intentionally absent from the body.
 */
@Serializable
data class SyncRecordingUpsertRequest(
    val title: String,
    val createdAt: String,
    val durationSec: Double,
    val source: String,
    val status: String,
)

@Serializable
data class SyncUpsertResponse(val ok: Boolean)

// ---- Billing ----

/** Subscription state. `tier` is "free" | "pro"; `renewsAt` is an ISO-8601 string when present. */
@Serializable
data class SubscriptionResponse(
    val tier: String,
    val renewsAt: String? = null,
)

/** Checkout body. `plan` is one of "pro_monthly" | "pro_yearly" | "pro". */
@Serializable
data class CheckoutRequest(val plan: String)

@Serializable
data class CheckoutResponse(val checkoutUrl: String)
