package com.notova.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** In-memory Room coverage for [SummaryDao] and the (intentional) lack of FK cascade. */
@RunWith(RobolectricTestRunner::class)
class SummaryDaoTest {
    private lateinit var db: NotovaDatabase
    private lateinit var summaryDao: SummaryDao
    private lateinit var recordingDao: RecordingDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                NotovaDatabase::class.java,
            ).allowMainThreadQueries().build()
        summaryDao = db.summaryDao()
        recordingDao = db.recordingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun summary(
        recordingId: String,
        style: String = "concise",
    ) = SummaryEntity(
        recordingId = recordingId,
        style = style,
        contentMarkdown = "## Summary",
        actionItemsBlob = "id1|false|do it",
        model = "stub-summarizer-v0",
        generatedAtEpochMs = 5_000,
    )

    @Test
    fun `insert then getByRecordingId returns the stored summary`() =
        runTest {
            val s = summary("rec-1")
            summaryDao.upsert(s)
            assertEquals(s, summaryDao.getByRecordingId("rec-1"))
        }

    @Test
    fun `getByRecordingId returns null when absent`() =
        runTest {
            assertNull(summaryDao.getByRecordingId("missing"))
        }

    @Test
    fun `upsert replaces a summary with the same recordingId`() =
        runTest {
            summaryDao.upsert(summary("rec-1", style = "concise"))
            summaryDao.upsert(summary("rec-1", style = "detailed"))
            assertEquals("detailed", summaryDao.getByRecordingId("rec-1")?.style)
        }

    @Test
    fun `summaries for different recordings coexist`() =
        runTest {
            summaryDao.upsert(summary("rec-1"))
            summaryDao.upsert(summary("rec-2"))
            assertEquals("rec-1", summaryDao.getByRecordingId("rec-1")?.recordingId)
            assertEquals("rec-2", summaryDao.getByRecordingId("rec-2")?.recordingId)
        }

    @Test
    fun `all summary fields round-trip`() =
        runTest {
            val s =
                SummaryEntity(
                    recordingId = "rec-9",
                    style = "bullet",
                    contentMarkdown = "## Summary\n- point",
                    actionItemsBlob = "a|true|done thing\nb|false|todo thing",
                    model = "gemma-3n",
                    generatedAtEpochMs = 1_700_000_000_000,
                )
            summaryDao.upsert(s)
            val stored = summaryDao.getByRecordingId("rec-9")!!
            assertEquals("bullet", stored.style)
            assertEquals("## Summary\n- point", stored.contentMarkdown)
            assertEquals("a|true|done thing\nb|false|todo thing", stored.actionItemsBlob)
            assertEquals("gemma-3n", stored.model)
            assertEquals(1_700_000_000_000, stored.generatedAtEpochMs)
        }

    @Test
    fun `deleting a recording does not cascade to its summary - no FK is modeled`() =
        runTest {
            recordingDao.upsert(
                RecordingEntity("rec-1", "t", 1_000, 1.0, "MIC", null, "READY"),
            )
            summaryDao.upsert(summary("rec-1"))

            recordingDao.deleteById("rec-1")

            // The summary table is independent; the row survives.
            assertNull(recordingDao.getById("rec-1"))
            assertEquals("rec-1", summaryDao.getByRecordingId("rec-1")?.recordingId)
        }
}
