package com.notova.integrations.provider

import com.notova.core.model.Recording
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import com.notova.integrations.api.NotovaBackendApi
import com.notova.integrations.api.buildExportRequest
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of exporting a note to a connected provider. */
sealed interface ExportResult {
    /** The backend wrote (or queued) the note. [url] is present when the provider returned one. */
    data class Success(
        val provider: String,
        val externalId: String,
        val url: String?,
        val status: String,
    ) : ExportResult

    data class Failure(
        val provider: String,
        val message: String,
    ) : ExportResult
}

/**
 * Exports a finished on-device note ([Recording] + [Summary] + [Transcript]) to a connected
 * provider via `POST v1/integrations/{provider}/export`. The on-device models are mapped onto the
 * `/v1` contract DTOs with the existing [buildExportRequest] mappers (ms→sec offsets, ISO-8601
 * dates, lowercase source/status) — never raw audio. Backend errors map to [ExportResult.Failure].
 */
@Singleton
class ExportRepository
    @Inject
    constructor(
        private val api: NotovaBackendApi,
    ) {
        suspend fun export(
            provider: String,
            recording: Recording,
            summary: Summary,
            transcript: Transcript,
        ): ExportResult =
            runCatching {
                api.exportToIntegration(
                    provider = provider,
                    body = buildExportRequest(recording, summary, transcript),
                )
            }.fold(
                onSuccess = { response ->
                    ExportResult.Success(
                        provider = provider,
                        externalId = response.externalId,
                        url = response.url,
                        status = response.status,
                    )
                },
                onFailure = { ExportResult.Failure(provider, messageFor(it)) },
            )

        private fun messageFor(error: Throwable): String =
            when {
                error is HttpException && error.code() == HTTP_UNAUTHORIZED ->
                    "Your session expired. Please sign in again."
                error is HttpException && error.code() == HTTP_NOT_FOUND ->
                    "That provider isn't connected. Connect it in Settings first."
                error is HttpException ->
                    "Export failed (${error.code()}). Please try again."
                else ->
                    "Network error. Check your connection and try again."
            }

        private companion object {
            const val HTTP_UNAUTHORIZED = 401
            const val HTTP_NOT_FOUND = 404
        }
    }
