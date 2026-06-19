package com.notova.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** In-memory Room coverage for [RecordingDao]: CRUD, ordering, upsert-replace and Flow emissions. */
@RunWith(RobolectricTestRunner::class)
class RecordingDaoTest {
    private lateinit var db: NotovaDatabase
    private lateinit var dao: RecordingDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                NotovaDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = db.recordingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        id: String,
        createdAt: Long,
        title: String = "t-$id",
        status: String = "READY",
        source: String = "MIC",
    ) = RecordingEntity(
        id = id,
        title = title,
        createdAtEpochMs = createdAt,
        durationSec = 10.0,
        source = source,
        localAudioPath = "/p/$id.m4a",
        status = status,
    )

    @Test
    fun `insert then getById returns the stored entity`() =
        runTest {
            val e = entity("a", 1_000)
            dao.upsert(e)
            assertEquals(e, dao.getById("a"))
        }

    @Test
    fun `getById returns null for a missing id`() =
        runTest {
            assertNull(dao.getById("nope"))
        }

    @Test
    fun `observeAll on an empty table emits an empty list`() =
        runTest {
            dao.observeAll().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeAll returns newest first by createdAt`() =
        runTest {
            dao.upsert(entity("old", createdAt = 1_000))
            dao.upsert(entity("new", createdAt = 3_000))
            dao.upsert(entity("mid", createdAt = 2_000))

            dao.observeAll().test {
                val ids = awaitItem().map { it.id }
                assertEquals(listOf("new", "mid", "old"), ids)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `upsert replaces an existing row with the same id`() =
        runTest {
            dao.upsert(entity("a", 1_000, title = "first", status = "PROCESSING"))
            dao.upsert(entity("a", 1_000, title = "second", status = "READY"))

            val stored = dao.getById("a")
            assertEquals("second", stored?.title)
            assertEquals("READY", stored?.status)
            // Still a single row.
            dao.observeAll().test {
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteById removes only the targeted row`() =
        runTest {
            dao.upsert(entity("a", 1_000))
            dao.upsert(entity("b", 2_000))

            dao.deleteById("a")

            assertNull(dao.getById("a"))
            assertEquals("b", dao.getById("b")?.id)
        }

    @Test
    fun `deleteById on a missing id is a no-op`() =
        runTest {
            dao.upsert(entity("a", 1_000))
            dao.deleteById("ghost")
            dao.observeAll().test {
                assertEquals(listOf("a"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("a", dao.getById("a")?.id)
        }

    @Test
    fun `observeAll emits a new list after a write`() =
        runTest {
            dao.observeAll().test {
                assertEquals(emptyList<RecordingEntity>(), awaitItem())

                dao.upsert(entity("a", 1_000))
                assertEquals(listOf("a"), awaitItem().map { it.id })

                dao.upsert(entity("b", 2_000))
                assertEquals(listOf("b", "a"), awaitItem().map { it.id })

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeAll emits after a delete`() =
        runTest {
            dao.upsert(entity("a", 1_000))
            dao.observeAll().test {
                assertEquals(listOf("a"), awaitItem().map { it.id })
                dao.deleteById("a")
                assertEquals(emptyList<RecordingEntity>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `all scalar fields round-trip through the database`() =
        runTest {
            val e =
                RecordingEntity(
                    id = "full",
                    title = "Quarterly review",
                    createdAtEpochMs = 1_700_000_000_000,
                    durationSec = 123.456,
                    source = "BLUETOOTH",
                    localAudioPath = null,
                    status = "FAILED",
                )
            dao.upsert(e)
            val stored = dao.getById("full")!!
            assertEquals("Quarterly review", stored.title)
            assertEquals(1_700_000_000_000, stored.createdAtEpochMs)
            assertEquals(123.456, stored.durationSec, 0.0)
            assertEquals("BLUETOOTH", stored.source)
            assertNull(stored.localAudioPath)
            assertEquals("FAILED", stored.status)
        }
}
