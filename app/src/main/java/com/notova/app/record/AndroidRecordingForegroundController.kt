package com.notova.app.record

import android.content.Context
import com.notova.core.audio.RecordingForegroundController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Starts/stops [RecordingService] to satisfy [RecordingForegroundController]. */
@Singleton
class AndroidRecordingForegroundController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RecordingForegroundController {
        override fun start() = RecordingService.start(context)

        override fun stop() = RecordingService.stop(context)
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingControllerModule {
    @Binds
    @Singleton
    abstract fun bindRecordingForegroundController(
        impl: AndroidRecordingForegroundController,
    ): RecordingForegroundController
}
