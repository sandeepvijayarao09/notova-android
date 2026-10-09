package com.notova.core.integration

import com.notova.core.model.IntegrationExport
import com.notova.core.model.Summary

/**
 * Exports a finished note to an external provider via the Notova backend (Notion today; Google,
 * Slack and Salesforce can connect but their export is not implemented server-side yet).
 *
 * Only metadata/content leaves the device here — never raw audio for AI compute. The backend
 * brokers OAuth and forwards the export.
 */
interface IntegrationExporter {
    /** Providers this exporter can target (e.g. "notion", "slack"). */
    val supportedProviders: Set<String>

    suspend fun export(
        provider: String,
        summary: Summary,
    ): IntegrationExport
}
