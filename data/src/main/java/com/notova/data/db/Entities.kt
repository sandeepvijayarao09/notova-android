package com.notova.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val durationSec: Double,
    val source: String,
    val localAudioPath: String?,
    val status: String,
)

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey val recordingId: String,
    val style: String,
    val contentMarkdown: String,
    /** Action items serialized as a simple newline-joined "done|text" list. */
    val actionItemsBlob: String,
    val model: String,
    val generatedAtEpochMs: Long,
)
