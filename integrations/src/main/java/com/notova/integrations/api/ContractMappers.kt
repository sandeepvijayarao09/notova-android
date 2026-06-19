package com.notova.integrations.api

import com.notova.core.model.ActionItem
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import java.time.Instant

/**
 * Maps on-device domain models onto the backend `/v1` contract DTOs.
 *
 * Decisions pinned by the backend contract:
 *  - Dates are ISO-8601 strings WITH offset ([Instant.toString] emits `...Z`, a valid offset form).
 *  - `recording.source` is one of "mic"|"bluetooth"|"file"|"other" (lowercase).
 *  - `recording.status` is one of "recording"|"processing"|"ready"|"failed" (lowercase).
 *  - `summary.text` carries the on-device summary markdown.
 *  - `transcript.text` carries the full transcript text; segment offsets convert ms -> sec.
 */

internal fun RecordingSource.toContractString(): String =
    when (this) {
        RecordingSource.MIC -> "mic"
        RecordingSource.BLUETOOTH -> "bluetooth"
        RecordingSource.FILE -> "file"
        RecordingSource.OTHER -> "other"
    }

internal fun RecordingStatus.toContractString(): String =
    when (this) {
        RecordingStatus.RECORDING -> "recording"
        RecordingStatus.PROCESSING -> "processing"
        RecordingStatus.READY -> "ready"
        RecordingStatus.FAILED -> "failed"
    }

internal fun Instant.toIsoOffset(): String = this.toString()

internal fun Recording.toRecordingDto(): RecordingDto =
    RecordingDto(
        id = id,
        title = title,
        createdAt = createdAt.toIsoOffset(),
        durationSec = durationSec,
        source = source.toContractString(),
        status = status.toContractString(),
    )

/** ms -> sec for transcript segment offsets, preserving sub-second precision. */
internal fun TranscriptSegment.toSegmentDto(): TranscriptSegmentDto =
    TranscriptSegmentDto(
        startSec = startMs / 1000.0,
        endSec = endMs / 1000.0,
        speaker = speaker,
        text = text,
    )

internal fun ActionItem.toActionItemDto(): ActionItemDto =
    ActionItemDto(
        id = id,
        text = text,
        done = done,
    )

internal fun Summary.toSummaryDto(): SummaryDto =
    SummaryDto(
        text = contentMarkdown,
        bullets = null,
        actionItems = actionItems.takeIf { it.isNotEmpty() }?.map { it.toActionItemDto() },
    )

internal fun Transcript.toTranscriptDto(): TranscriptDto =
    TranscriptDto(
        text = fullText,
        segments = segments.takeIf { it.isNotEmpty() }?.map { it.toSegmentDto() },
        language = language,
    )

/** Builds the contract [ExportRequest] from the on-device recording, summary, and transcript. */
internal fun buildExportRequest(
    recording: Recording,
    summary: Summary,
    transcript: Transcript,
): ExportRequest =
    ExportRequest(
        recording = recording.toRecordingDto(),
        summary = summary.toSummaryDto(),
        transcript = transcript.toTranscriptDto(),
    )

/** Maps an on-device [Recording] to the metadata-only sync upsert body (no id; it is the path). */
internal fun Recording.toSyncUpsertRequest(): SyncRecordingUpsertRequest =
    SyncRecordingUpsertRequest(
        title = title,
        createdAt = createdAt.toIsoOffset(),
        durationSec = durationSec,
        source = source.toContractString(),
        status = status.toContractString(),
    )
