package com.notova.data.repository

import com.notova.core.model.ActionItem
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import com.notova.data.db.RecordingEntity
import com.notova.data.db.SummaryEntity
import java.time.Instant

internal fun RecordingEntity.toDomain(): Recording =
    Recording(
        id = id,
        title = title,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        durationSec = durationSec,
        source = RecordingSource.valueOf(source),
        localAudioPath = localAudioPath,
        status = RecordingStatus.valueOf(status),
    )

internal fun Recording.toEntity(): RecordingEntity =
    RecordingEntity(
        id = id,
        title = title,
        createdAtEpochMs = createdAt.toEpochMilli(),
        durationSec = durationSec,
        source = source.name,
        localAudioPath = localAudioPath,
        status = status.name,
    )

private const val FIELD_SEP = "|"

internal fun SummaryEntity.toDomain(): Summary =
    Summary(
        recordingId = recordingId,
        style = style,
        contentMarkdown = contentMarkdown,
        actionItems = decodeActionItems(actionItemsBlob),
        model = model,
        generatedAt = Instant.ofEpochMilli(generatedAtEpochMs),
    )

internal fun Summary.toEntity(): SummaryEntity =
    SummaryEntity(
        recordingId = recordingId,
        style = style,
        contentMarkdown = contentMarkdown,
        actionItemsBlob = encodeActionItems(actionItems),
        model = model,
        generatedAtEpochMs = generatedAt.toEpochMilli(),
    )

private fun encodeActionItems(items: List<ActionItem>): String =
    items.joinToString("\n") { "${it.id}$FIELD_SEP${it.done}$FIELD_SEP${it.text.replace("\n", " ")}" }

private fun decodeActionItems(blob: String): List<ActionItem> =
    blob.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(FIELD_SEP, limit = 3)
            if (parts.size == 3) {
                ActionItem(id = parts[0], done = parts[1].toBoolean(), text = parts[2])
            } else {
                null
            }
        }
        .toList()
