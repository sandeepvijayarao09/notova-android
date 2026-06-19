package com.notova.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.notova.data.db.NotovaDatabase
import com.notova.data.db.RecordingDao
import com.notova.data.db.SummaryDao
import com.notova.data.repository.RecordingRepository
import com.notova.data.repository.RecordingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "notova_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): NotovaDatabase =
        Room.databaseBuilder(context, NotovaDatabase::class.java, NotovaDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRecordingDao(db: NotovaDatabase): RecordingDao = db.recordingDao()

    @Provides
    fun provideSummaryDao(db: NotovaDatabase): SummaryDao = db.summaryDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.preferencesDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindRecordingRepository(impl: RecordingRepositoryImpl): RecordingRepository
}
