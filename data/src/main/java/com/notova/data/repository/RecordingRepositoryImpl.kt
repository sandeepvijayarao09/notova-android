package com.notova.data.repository

import com.notova.core.model.Recording
import com.notova.core.model.Summary
import com.notova.data.db.RecordingDao
import com.notova.data.db.SummaryDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepositoryImpl
    @Inject
    constructor(
        private val recordingDao: RecordingDao,
        private val summaryDao: SummaryDao,
    ) : RecordingRepository {
        override fun observeRecordings(): Flow<List<Recording>> =
            recordingDao.observeAll().map { list -> list.map { it.toDomain() } }

        override suspend fun getRecording(id: String): Recording? = recordingDao.getById(id)?.toDomain()

        override suspend fun upsertRecording(recording: Recording) = recordingDao.upsert(recording.toEntity())

        override suspend fun deleteRecording(id: String) = recordingDao.deleteById(id)

        override suspend fun getSummary(recordingId: String): Summary? =
            summaryDao.getByRecordingId(recordingId)?.toDomain()

        override suspend fun upsertSummary(summary: Summary) = summaryDao.upsert(summary.toEntity())
    }
