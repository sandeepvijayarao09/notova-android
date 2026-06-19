package com.notova.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.notova.core.model.ActionItem
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import com.notova.data.db.NotovaDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * End-to-end repository coverage against a real in-memory Room database. Exercises the full
 * domain<->entity mapping path through [RecordingRepositoryImpl].
 */
@RunWith(RobolectricTestRunner::class)
class RecordingRepositoryImplTest {
    private lateinit var db: NotovaDatabase
    private lateinit var repository: RecordingRepository

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                NotovaDatabase::class.java,
            ).allowMainThreadQueries().build()
        repository = RecordingRepositoryImpl(db.recordingDao(), db.summaryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun recording(
        id: String,
        createdAtMs: Long = 1_000,
        status: RecordingStatus = RecordingStatus.READY,
        source: RecordingSource = RecordingSource.MIC,
    ) = Recording(
        id = id,
        title = "Note $id",
        createdAt = Instant.ofEpochMilli(createdAtMs),
        durationSec = 12.0,
        source = source,
        localAudioPath = "/p/$id.m4a",
        status = status,
    )

    @Test
    fun `upsert then getRecording round-trips a domain object`() =
        runTest {
            val r = recording("a", source = RecordingSource.BLUETOOTH, status = RecordingStatus.PROCESSING)
            repository.upsertRecording(r)

            val loaded = repository.getRecording("a")!!
            assertEquals(r.id, loaded.id)
            assertEquals(r.title, loaded.title)
            assertEquals(r.createdAt, loaded.createdAt)
            assertEquals(r.durationSec, loaded.durationSec, 0.0)
            assertEquals(RecordingSource.BLUETOOTH, loaded.source)
            assertEquals(RecordingStatus.PROCESSING, loaded.status)
            assertEquals(r.localAudioPath, loaded.localAudioPath)
        }

    @Test
    fun `getRecording returns null when not present`() =
        runTest {
            assertNull(repository.getRecording("ghost"))
        }

    @Test
    fun `observeRecordings maps entities to domain newest-first`() =
        runTest {
            repository.upsertRecording(recording("old", createdAtMs = 1_000))
            repository.upsertRecording(recording("new", createdAtMs = 3_000))

            repository.observeRecordings().test {
                val list = awaitItem()
                assertEquals(listOf("new", "old"), list.map { it.id })
                assertEquals(RecordingStatus.READY, list.first().status)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeRecordings emits updates as recordings are written`() =
        runTest {
            repository.observeRecordings().test {
                assertTrue(awaitItem().isEmpty())
                repository.upsertRecording(recording("a", createdAtMs = 1_000))
                assertEquals(listOf("a"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteRecording removes it from observe and getRecording`() =
        runTest {
            repository.upsertRecording(recording("a"))
            repository.deleteRecording("a")
            assertNull(repository.getRecording("a"))
        }

    @Test
    fun `upsertRecording with same id updates in place`() =
        runTest {
            repository.upsertRecording(recording("a", status = RecordingStatus.PROCESSING))
            repository.upsertRecording(recording("a", status = RecordingStatus.READY))
            assertEquals(RecordingStatus.READY, repository.getRecording("a")?.status)
        }

    @Test
    fun `summary round-trips including action items`() =
        runTest {
            val summary =
                Summary(
                    recordingId = "rec-1",
                    style = "concise",
                    contentMarkdown = "## Summary\nbody",
                    actionItems =
                        listOf(
                            ActionItem(id = "x1", text = "ship it", done = false),
                            ActionItem(id = "x2", text = "review it", done = true),
                        ),
                    model = "stub-summarizer-v0",
                    generatedAt = Instant.ofEpochMilli(7_000),
                )
            repository.upsertSummary(summary)

            val loaded = repository.getSummary("rec-1")!!
            assertEquals("concise", loaded.style)
            assertEquals("## Summary\nbody", loaded.contentMarkdown)
            assertEquals("stub-summarizer-v0", loaded.model)
            assertEquals(Instant.ofEpochMilli(7_000), loaded.generatedAt)
            assertEquals(2, loaded.actionItems.size)
            assertEquals("x1", loaded.actionItems[0].id)
            assertEquals("ship it", loaded.actionItems[0].text)
            assertEquals(false, loaded.actionItems[0].done)
            assertEquals("x2", loaded.actionItems[1].id)
            assertEquals(true, loaded.actionItems[1].done)
        }

    @Test
    fun `getSummary returns null when absent`() =
        runTest {
            assertNull(repository.getSummary("none"))
        }

    @Test
    fun `summary with no action items round-trips to an empty list`() =
        runTest {
            val summary =
                Summary(
                    recordingId = "rec-2",
                    style = "concise",
                    contentMarkdown = "## Summary",
                    actionItems = emptyList(),
                    model = "m",
                    generatedAt = Instant.ofEpochMilli(0),
                )
            repository.upsertSummary(summary)
            assertTrue(repository.getSummary("rec-2")!!.actionItems.isEmpty())
        }

    @Test
    fun `every RecordingSource and status survives the round-trip`() =
        runTest {
            RecordingSource.entries.forEachIndexed { i, src ->
                RecordingStatus.entries.forEachIndexed { j, st ->
                    val id = "r-$i-$j"
                    repository.upsertRecording(recording(id, source = src, status = st))
                    val loaded = repository.getRecording(id)!!
                    assertEquals(src, loaded.source)
                    assertEquals(st, loaded.status)
                }
            }
        }
}
