package com.notova.core.model

import java.time.Instant
import java.util.UUID

/** Source of the captured audio. */
enum class RecordingSource {
    MIC,
    BLUETOOTH,
    FILE,
    OTHER,
}

/** Lifecycle status of a recording as it moves through the on-device pipeline. */
enum class RecordingStatus {
    RECORDING,
    PROCESSING,
    READY,
    FAILED,
}

/** A single captured audio note. */
data class Recording(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Instant,
    val durationSec: Double,
    val source: RecordingSource,
    val localAudioPath: String?,
    val status: RecordingStatus,
)

/** A time-bounded chunk of transcript text, optionally attributed to a speaker. */
data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speaker: String? = null,
)

/** The full transcript produced on-device for a recording. */
data class Transcript(
    val recordingId: String,
    val language: String,
    val fullText: String,
    val segments: List<TranscriptSegment>,
)

/** A single actionable item extracted from a summary. */
data class ActionItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false,
)

/** The on-device generated summary for a recording. */
data class Summary(
    val recordingId: String,
    val style: String,
    val contentMarkdown: String,
    val actionItems: List<ActionItem>,
    val model: String,
    val generatedAt: Instant,
)

/** Status of exporting a note to an external integration provider. */
enum class IntegrationExportStatus {
    PENDING,
    DONE,
    FAILED,
}

/** Record of a note being exported to an external provider (Notion, Todoist, etc.). */
data class IntegrationExport(
    val recordingId: String,
    val provider: String,
    val externalId: String? = null,
    val url: String? = null,
    val status: IntegrationExportStatus = IntegrationExportStatus.PENDING,
)
