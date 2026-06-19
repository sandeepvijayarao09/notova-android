package com.notova.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecordingEntity::class, SummaryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NotovaDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    abstract fun summaryDao(): SummaryDao

    companion object {
        const val NAME = "notova.db"
    }
}
