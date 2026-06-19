package com.notova.core.integration

import com.notova.core.model.IntegrationExport
import com.notova.core.model.Summary

/**
 * Exports a finished note to an external provider (Notion, Todoist, Google Tasks, ...).
 *
 * Only metadata/content leaves the device here — never raw audio for AI compute. The backend
 * brokers OAuth and forwards the export. [StubIntegrationExporter] is a no-op placeholder.
 */
interface IntegrationExporter {
    /** Providers this exporter can target (e.g. "notion", "todoist"). */
    val supportedProviders: Set<String>

    suspend fun export(
        provider: String,
        summary: Summary,
    ): IntegrationExport
}
