package com.notova.app.work

import com.notova.core.pipeline.RecordingProcessingScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the WorkManager-backed [RecordingProcessingScheduler] for injection into the Record flow. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkSchedulerModule {
    @Binds
    @Singleton
    abstract fun bindRecordingProcessingScheduler(impl: WorkManagerRecordingScheduler): RecordingProcessingScheduler
}
