package com.notova.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.notova.core.pipeline.PipelineUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Coverage for [NotovaPreferences] against an isolated, file-backed [DataStore] built per test (via
 * a [TemporaryFolder]) so each case starts from a clean slate with no cross-test state leakage.
 *
 * Uses [runBlocking] (not `runTest`) because these are real file-I/O round-trips with no need for
 * the virtual-time scheduler — and mixing the DataStore's own scope scheduler with `runTest`'s
 * scheduler is explicitly unsupported.
 */
class NotovaPreferencesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: NotovaPreferences

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                tempFolder.newFile("prefs-${System.nanoTime()}.preferences_pb")
            }
        prefs = NotovaPreferences(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `summaryStyle defaults to the pipeline default style`() =
        runBlocking {
            assertEquals(PipelineUseCase.DEFAULT_STYLE, prefs.summaryStyle.first())
            assertEquals("concise", prefs.summaryStyle.first())
        }

    @Test
    fun `setSummaryStyle is read back`() =
        runBlocking {
            prefs.setSummaryStyle("detailed")
            assertEquals("detailed", prefs.summaryStyle.first())
        }

    @Test
    fun `last write wins`() =
        runBlocking {
            prefs.setSummaryStyle("bullet")
            prefs.setSummaryStyle("detailed")
            assertEquals("detailed", prefs.summaryStyle.first())
        }

    @Test
    fun `writing an empty string is preserved and overrides the default`() =
        runBlocking {
            prefs.setSummaryStyle("")
            assertEquals("", prefs.summaryStyle.first())
        }
}
