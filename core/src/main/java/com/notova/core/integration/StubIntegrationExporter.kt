package com.notova.core.integration

import com.notova.core.model.IntegrationExport
import com.notova.core.model.IntegrationExportStatus
import com.notova.core.model.Summary
import javax.inject.Inject

/** No-op [IntegrationExporter] used until real provider brokering is wired through the backend. */
class StubIntegrationExporter
    @Inject
    constructor() : IntegrationExporter {
        override val supportedProviders: Set<String> = setOf("notion", "todoist", "google_tasks")

        override suspend fun export(
            provider: String,
            summary: Summary,
        ): IntegrationExport =
            IntegrationExport(
                recordingId = summary.recordingId,
                provider = provider,
                externalId = null,
                url = null,
                status = IntegrationExportStatus.PENDING,
            )
    }
