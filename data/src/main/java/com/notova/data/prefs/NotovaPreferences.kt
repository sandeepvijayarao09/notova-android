package com.notova.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.notova.core.pipeline.PipelineUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin DataStore wrapper for user preferences (e.g. default summary style).
 *
 * Takes the [DataStore] as a constructor dependency (provided by Hilt in
 * [com.notova.data.di.PreferencesModule]) so it can be unit-tested against an isolated store.
 */
@Singleton
class NotovaPreferences
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val summaryStyle: Flow<String> =
            dataStore.data.map { prefs -> prefs[KEY_SUMMARY_STYLE] ?: PipelineUseCase.DEFAULT_STYLE }

        suspend fun setSummaryStyle(style: String) {
            dataStore.edit { prefs -> prefs[KEY_SUMMARY_STYLE] = style }
        }

        private companion object {
            val KEY_SUMMARY_STYLE = stringPreferencesKey("summary_style")
        }
    }
