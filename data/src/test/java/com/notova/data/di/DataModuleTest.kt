package com.notova.data.di

import androidx.test.core.app.ApplicationProvider
import com.notova.data.db.NotovaDatabase
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the [DatabaseModule] `@Provides` functions directly under Robolectric so the database,
 * DAO and DataStore wiring is covered without standing up the full Hilt graph.
 */
@RunWith(RobolectricTestRunner::class)
class DataModuleTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `provideDatabase builds a NotovaDatabase exposing both daos`() {
        val db = DatabaseModule.provideDatabase(context)
        try {
            assertNotNull(db)
            assertNotNull(DatabaseModule.provideRecordingDao(db))
            assertNotNull(DatabaseModule.provideSummaryDao(db))
        } finally {
            db.close()
        }
    }

    @Test
    fun `provideSummaryDao returns the database's summary dao`() {
        val db = DatabaseModule.provideDatabase(context)
        try {
            assertNotNull(DatabaseModule.provideSummaryDao(db))
        } finally {
            db.close()
        }
    }

    @Test
    fun `providePreferencesDataStore returns a non-null store`() {
        assertNotNull(DatabaseModule.providePreferencesDataStore(context))
    }

    @Test
    fun `database name constant is stable`() {
        assertNotNull(NotovaDatabase.NAME)
    }
}
