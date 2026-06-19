package com.notova.feature.record

import com.notova.core.audio.AudioSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordModule {
    @Binds
    @Singleton
    abstract fun bindAudioSource(impl: MediaRecorderAudioSource): AudioSource
}
