package com.notova.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Exhaustive coverage of the domain models: construction, defaults, copy/equality semantics, and
 * — critically — that every enum's [Enum.name] matches the cross-platform contract shared with the
 * backend and iOS clients. The string names are part of the wire/storage contract (see
 * [com.notova.data.repository] mappers and the sync DTOs), so any rename here is a breaking change.
 */
class ModelsTest {
    // ---------------------------------------------------------------------------------------------
    // RecordingSource — cross-platform contract: MIC / BLUETOOTH / FILE / OTHER
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `RecordingSource has exactly four values in declared order`() {
        assertEquals(4, RecordingSource.entries.size)
        assertEquals(
            listOf("MIC", "BLUETOOTH", "FILE", "OTHER"),
            RecordingSource.entries.map { it.name },
        )
    }

    @Test
    fun `RecordingSource names match the cross-platform contract`() {
        assertEquals("MIC", RecordingSource.MIC.name)
        assertEquals("BLUETOOTH", RecordingSource.BLUETOOTH.name)
        assertEquals("FILE", RecordingSource.FILE.name)
        assertEquals("OTHER", RecordingSource.OTHER.name)
    }

    @Test
    fun `RecordingSource valueOf round-trips every value`() {
        RecordingSource.entries.forEach { source ->
            assertEquals(source, RecordingSource.valueOf(source.name))
        }
    }

    @Test
    fun `RecordingSource ordinals are stable`() {
        assertEquals(0, RecordingSource.MIC.ordinal)
        assertEquals(1, RecordingSource.BLUETOOTH.ordinal)
        assertEquals(2, RecordingSource.FILE.ordinal)
        assertEquals(3, RecordingSource.OTHER.ordinal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RecordingSource valueOf rejects unknown name`() {
        RecordingSource.valueOf("CARRIER_PIGEON")
    }

    // ---------------------------------------------------------------------------------------------
    // RecordingStatus — cross-platform contract: RECORDING / PROCESSING / READY / FAILED
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `RecordingStatus has exactly four values in declared order`() {
        assertEquals(4, RecordingStatus.entries.size)
        assertEquals(
            listOf("RECORDING", "PROCESSING", "READY", "FAILED"),
            RecordingStatus.entries.map { it.name },
        )
    }

    @Test
    fun `RecordingStatus names match the cross-platform contract`() {
        assertEquals("RECORDING", RecordingStatus.RECORDING.name)
        assertEquals("PROCESSING", RecordingStatus.PROCESSING.name)
        assertEquals("READY", RecordingStatus.READY.name)
        assertEquals("FAILED", RecordingStatus.FAILED.name)
    }

    @Test
    fun `RecordingStatus valueOf round-trips every value`() {
        RecordingStatus.entries.forEach { status ->
            assertEquals(status, RecordingStatus.valueOf(status.name))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `RecordingStatus valueOf rejects unknown name`() {
        RecordingStatus.valueOf("PAUSED")
    }

    // ---------------------------------------------------------------------------------------------
    // IntegrationExportStatus — cross-platform contract: PENDING / DONE / FAILED
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `IntegrationExportStatus has exactly three values in declared order`() {
        assertEquals(3, IntegrationExportStatus.entries.size)
        assertEquals(
            listOf("PENDING", "DONE", "FAILED"),
            IntegrationExportStatus.entries.map { it.name },
        )
    }

    @Test
    fun `IntegrationExportStatus names match the cross-platform contract`() {
        assertEquals("PENDING", IntegrationExportStatus.PENDING.name)
        assertEquals("DONE", IntegrationExportStatus.DONE.name)
        assertEquals("FAILED", IntegrationExportStatus.FAILED.name)
    }

    @Test
    fun `IntegrationExportStatus valueOf round-trips every value`() {
        IntegrationExportStatus.entries.forEach { status ->
            assertEquals(status, IntegrationExportStatus.valueOf(status.name))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `IntegrationExportStatus valueOf rejects unknown name`() {
        IntegrationExportStatus.valueOf("IN_PROGRESS")
    }

    // ---------------------------------------------------------------------------------------------
    // Recording
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Recording auto-generates a UUID id when not supplied`() {
        val r =
            Recording(
                title = "Daily standup",
                createdAt = Instant.ofEpochMilli(1_000),
                durationSec = 12.5,
                source = RecordingSource.MIC,
                localAudioPath = "/tmp/a.m4a",
                status = RecordingStatus.READY,
            )
        // A random UUID is 36 chars with 4 dashes.
        assertEquals(36, r.id.length)
        assertEquals(4, r.id.count { it == '-' })
    }

    @Test
    fun `Recording generates distinct ids across instances`() {
        val a = recording()
        val b = recording()
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `Recording honours an explicit id`() {
        val r = recording(id = "fixed-id")
        assertEquals("fixed-id", r.id)
    }

    @Test
    fun `Recording allows a null local audio path`() {
        val r = recording(localAudioPath = null)
        assertNull(r.localAudioPath)
    }

    @Test
    fun `Recording copy preserves untouched fields and updates target field`() {
        val original = recording(id = "x", status = RecordingStatus.PROCESSING)
        val updated = original.copy(status = RecordingStatus.READY)

        assertEquals(RecordingStatus.READY, updated.status)
        assertEquals(original.id, updated.id)
        assertEquals(original.title, updated.title)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(original.durationSec, updated.durationSec, 0.0)
        assertEquals(original.source, updated.source)
        assertEquals(original.localAudioPath, updated.localAudioPath)
    }

    @Test
    fun `Recording equality is structural for identical field sets`() {
        val instant = Instant.ofEpochMilli(42)
        val a = Recording("id", "t", instant, 1.0, RecordingSource.FILE, "/p", RecordingStatus.READY)
        val b = Recording("id", "t", instant, 1.0, RecordingSource.FILE, "/p", RecordingStatus.READY)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Recording inequality when any field differs`() {
        val base = recording(id = "id")
        assertNotEquals(base, base.copy(title = "other"))
        assertNotEquals(base, base.copy(durationSec = base.durationSec + 1))
        assertNotEquals(base, base.copy(source = RecordingSource.OTHER))
        assertNotEquals(base, base.copy(status = RecordingStatus.FAILED))
    }

    // ---------------------------------------------------------------------------------------------
    // TranscriptSegment & Transcript
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `TranscriptSegment speaker defaults to null`() {
        val seg = TranscriptSegment(startMs = 0, endMs = 100, text = "hi")
        assertNull(seg.speaker)
    }

    @Test
    fun `TranscriptSegment retains explicit speaker`() {
        val seg = TranscriptSegment(0, 100, "hi", speaker = "Alice")
        assertEquals("Alice", seg.speaker)
    }

    @Test
    fun `TranscriptSegment copy can clear and set speaker`() {
        val seg = TranscriptSegment(0, 100, "hi", "Alice")
        assertNull(seg.copy(speaker = null).speaker)
        assertEquals("Bob", seg.copy(speaker = "Bob").speaker)
    }

    @Test
    fun `Transcript holds its segments and identifying fields`() {
        val segs = listOf(TranscriptSegment(0, 500, "a"), TranscriptSegment(500, 1000, "b"))
        val t = Transcript(recordingId = "rec1", language = "en", fullText = "a b", segments = segs)
        assertEquals("rec1", t.recordingId)
        assertEquals("en", t.language)
        assertEquals("a b", t.fullText)
        assertEquals(2, t.segments.size)
        assertEquals(segs, t.segments)
    }

    @Test
    fun `Transcript supports an empty segment list`() {
        val t = Transcript("rec", "en", "", emptyList())
        assertTrue(t.segments.isEmpty())
    }

    @Test
    fun `Transcript equality is structural`() {
        val segs = listOf(TranscriptSegment(0, 1, "x"))
        assertEquals(
            Transcript("r", "en", "x", segs),
            Transcript("r", "en", "x", segs),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // ActionItem
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `ActionItem defaults done to false and generates an id`() {
        val item = ActionItem(text = "Email Bob")
        assertFalse(item.done)
        assertEquals(36, item.id.length)
    }

    @Test
    fun `ActionItem generates distinct ids`() {
        assertNotEquals(ActionItem(text = "a").id, ActionItem(text = "b").id)
    }

    @Test
    fun `ActionItem honours explicit id and done`() {
        val item = ActionItem(id = "fixed", text = "do it", done = true)
        assertEquals("fixed", item.id)
        assertTrue(item.done)
    }

    @Test
    fun `ActionItem copy toggles done without changing identity`() {
        val item = ActionItem(id = "k", text = "do it", done = false)
        val toggled = item.copy(done = true)
        assertTrue(toggled.done)
        assertEquals(item.id, toggled.id)
        assertEquals(item.text, toggled.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Summary retains all fields including action items`() {
        val now = Instant.ofEpochMilli(9_999)
        val items = listOf(ActionItem(text = "x"), ActionItem(text = "y"))
        val s =
            Summary(
                recordingId = "rec",
                style = "concise",
                contentMarkdown = "## Summary",
                actionItems = items,
                model = "stub-v0",
                generatedAt = now,
            )
        assertEquals("rec", s.recordingId)
        assertEquals("concise", s.style)
        assertEquals("## Summary", s.contentMarkdown)
        assertEquals(2, s.actionItems.size)
        assertEquals("stub-v0", s.model)
        assertEquals(now, s.generatedAt)
    }

    @Test
    fun `Summary supports an empty action item list`() {
        val s = Summary("rec", "concise", "md", emptyList(), "m", Instant.ofEpochMilli(0))
        assertTrue(s.actionItems.isEmpty())
    }

    @Test
    fun `Summary copy replaces action items`() {
        val s = Summary("rec", "concise", "md", emptyList(), "m", Instant.ofEpochMilli(0))
        val updated = s.copy(actionItems = listOf(ActionItem(text = "z")))
        assertEquals(1, updated.actionItems.size)
        assertTrue(s.actionItems.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // IntegrationExport
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `IntegrationExport defaults externalId and url to null and status to PENDING`() {
        val export = IntegrationExport(recordingId = "rec", provider = "notion")
        assertNull(export.externalId)
        assertNull(export.url)
        assertEquals(IntegrationExportStatus.PENDING, export.status)
    }

    @Test
    fun `IntegrationExport honours explicit fields`() {
        val export =
            IntegrationExport(
                recordingId = "rec",
                provider = "todoist",
                externalId = "ext-1",
                url = "https://todoist.com/x",
                status = IntegrationExportStatus.DONE,
            )
        assertEquals("rec", export.recordingId)
        assertEquals("todoist", export.provider)
        assertEquals("ext-1", export.externalId)
        assertEquals("https://todoist.com/x", export.url)
        assertEquals(IntegrationExportStatus.DONE, export.status)
    }

    @Test
    fun `IntegrationExport copy can transition status to FAILED`() {
        val export = IntegrationExport("rec", "notion")
        val failed = export.copy(status = IntegrationExportStatus.FAILED)
        assertEquals(IntegrationExportStatus.FAILED, failed.status)
        assertEquals(IntegrationExportStatus.PENDING, export.status)
    }

    private fun recording(
        id: String = java.util.UUID.randomUUID().toString(),
        localAudioPath: String? = "/tmp/rec.m4a",
        status: RecordingStatus = RecordingStatus.READY,
    ): Recording =
        Recording(
            id = id,
            title = "title",
            createdAt = Instant.ofEpochMilli(1_000),
            durationSec = 10.0,
            source = RecordingSource.MIC,
            localAudioPath = localAudioPath,
            status = status,
        )
}
