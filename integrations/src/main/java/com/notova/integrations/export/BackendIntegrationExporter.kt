package com.notova.integrations.export

import com.notova.core.integration.IntegrationExporter
import com.notova.core.model.IntegrationExport
import com.notova.core.model.IntegrationExportStatus
import com.notova.core.model.Summary
import com.notova.integrations.api.ExportRequest
import com.notova.integrations.api.NotovaBackendApi
import com.notova.integrations.api.RecordingDto
import com.notova.integrations.api.TranscriptDto
import com.notova.integrations.api.toSummaryDto
import javax.inject.Inject

/**
 * Backend-brokered [IntegrationExporter]. Forwards the on-device summary to the Notova backend,
 * which performs the provider-specific OAuth + write. Only metadata/content the user chose to
 * export leaves the device here — never raw audio. The backend's reported status string is mapped
 * onto [IntegrationExportStatus]; any failure (network, decode, unexpected status) maps to
 * [IntegrationExportStatus.FAILED].
 *
 * The `/v1` export contract is `{recording, summary, transcript}`. At this layer only the
 * [Summary] is available, so the recording is described by its id (the summary's `recordingId`)
 * and the transcript is omitted (empty text); the summary markdown and action items are carried
 * through via [toSummaryDto]. Recording/segment/language enrichment is handled by the contract
 * mappers when richer models are available upstream.
 */
class BackendIntegrationExporter
    @Inject
    constructor(
        private val api: NotovaBackendApi,
    ) : IntegrationExporter {
        override val supportedProviders: Set<String> = setOf("notion", "todoist", "google_tasks")

        override suspend fun export(
            provider: String,
            summary: Summary,
        ): IntegrationExport {
            require(provider in supportedProviders) { "Unsupported provider: $provider" }

            return runCatching {
                api.exportToIntegration(
                    provider = provider,
                    body = exportRequestFor(summary),
                )
            }.fold(
                onSuccess = { response ->
                    IntegrationExport(
                        recordingId = summary.recordingId,
                        provider = provider,
                        externalId = response.externalId,
                        url = response.url,
                        status = mapStatus(response.status),
                    )
                },
                onFailure = {
                    IntegrationExport(
                        recordingId = summary.recordingId,
                        provider = provider,
                        externalId = null,
                        url = null,
                        status = IntegrationExportStatus.FAILED,
                    )
                },
            )
        }

        private fun exportRequestFor(summary: Summary): ExportRequest =
            ExportRequest(
                recording =
                    RecordingDto(
                        id = summary.recordingId,
                        title = titleFor(summary),
                        createdAt = summary.generatedAt.toString(),
                        durationSec = 0.0,
                        source = "other",
                        status = "ready",
                    ),
                summary = summary.toSummaryDto(),
                transcript = TranscriptDto(text = ""),
            )

        private fun titleFor(summary: Summary): String = "Notova note ${summary.recordingId}"

        private fun mapStatus(raw: String): IntegrationExportStatus =
            when (raw.lowercase()) {
                "exported", "done", "ok", "success", "completed" -> IntegrationExportStatus.DONE
                "queued", "pending", "processing" -> IntegrationExportStatus.PENDING
                else -> IntegrationExportStatus.FAILED
            }
    }
