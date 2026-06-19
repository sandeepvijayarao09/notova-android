package com.notova.data.repository

import com.notova.core.model.ActionItem
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pure-JVM coverage of the entity<->domain mappers, including the bespoke action-item blob codec
 * (`id|done|text` lines) and the Instant<->epoch-millis conversions.
 */
class MappersTest {
    @Test
    fun `recording domain to entity to domain is lossless`() {
        val domain =
            Recording(
                id = "r1",
                title = "Sync",
                createdAt = Instant.ofEpochMilli(1_700_000_000_000),
                durationSec = 42.5,
                source = RecordingSource.FILE,
                localAudioPath = "/p/r1.m4a",
                status = RecordingStatus.READY,
            )
        val back = domain.toEntity().toDomain()
        assertEquals(domain, back)
    }

    @Test
    fun `recording entity stores enum names and epoch millis`() {
        val entity =
            Recording(
                id = "r1",
                title = "Sync",
                createdAt = Instant.ofEpochMilli(12_345),
                durationSec = 1.0,
                source = RecordingSource.BLUETOOTH,
                localAudioPath = null,
                status = RecordingStatus.FAILED,
            ).toEntity()
        assertEquals("BLUETOOTH", entity.source)
        assertEquals("FAILED", entity.status)
        assertEquals(12_345, entity.createdAtEpochMs)
        assertNull(entity.localAudioPath)
    }

    @Test
    fun `every recording source maps both directions`() {
        RecordingSource.entries.forEach { src ->
            val domain =
                Recording("id", "t", Instant.ofEpochMilli(0), 0.0, src, null, RecordingStatus.READY)
            assertEquals(src, domain.toEntity().toDomain().source)
        }
    }

    @Test
    fun `every recording status maps both directions`() {
        RecordingStatus.entries.forEach { status ->
            val domain =
                Recording("id", "t", Instant.ofEpochMilli(0), 0.0, RecordingSource.MIC, null, status)
            assertEquals(status, domain.toEntity().toDomain().status)
        }
    }

    @Test
    fun `summary domain to entity to domain is lossless including action items`() {
        val domain =
            Summary(
                recordingId = "rec-1",
                style = "concise",
                contentMarkdown = "## Summary\nbody",
                actionItems =
                    listOf(
                        ActionItem(id = "a", text = "first", done = false),
                        ActionItem(id = "b", text = "second", done = true),
                    ),
                model = "stub",
                generatedAt = Instant.ofEpochMilli(9_000),
            )
        val back = domain.toEntity().toDomain()
        assertEquals(domain, back)
    }

    @Test
    fun `summary with empty action items encodes to an empty blob`() {
        val entity =
            Summary("rec", "concise", "md", emptyList(), "m", Instant.ofEpochMilli(0)).toEntity()
        assertEquals("", entity.actionItemsBlob)
        assertTrue(entity.toDomain().actionItems.isEmpty())
    }

    @Test
    fun `action item text newlines are flattened to spaces on encode`() {
        val entity =
            Summary(
                recordingId = "rec",
                style = "concise",
                contentMarkdown = "md",
                actionItems = listOf(ActionItem(id = "x", text = "line one\nline two", done = false)),
                model = "m",
                generatedAt = Instant.ofEpochMilli(0),
            ).toEntity()
        // Newline within the text would corrupt the line-based codec, so it is replaced.
        assertEquals("x|false|line one line two", entity.actionItemsBlob)
        assertEquals("line one line two", entity.toDomain().actionItems[0].text)
    }

    @Test
    fun `decode skips malformed lines`() {
        val entity =
            com.notova.data.db.SummaryEntity(
                recordingId = "rec",
                style = "concise",
                contentMarkdown = "md",
                // Second line has only two fields and must be dropped.
                actionItemsBlob = "a|false|valid\nbroken-line\nb|true|also valid",
                model = "m",
                generatedAtEpochMs = 0,
            )
        val items = entity.toDomain().actionItems
        assertEquals(2, items.size)
        assertEquals(listOf("a", "b"), items.map { it.id })
        assertEquals(listOf("valid", "also valid"), items.map { it.text })
        assertEquals(listOf(false, true), items.map { it.done })
    }

    @Test
    fun `decode ignores blank lines`() {
        val entity =
            com.notova.data.db.SummaryEntity(
                recordingId = "rec",
                style = "concise",
                contentMarkdown = "md",
                actionItemsBlob = "\n\na|false|only one\n\n",
                model = "m",
                generatedAtEpochMs = 0,
            )
        assertEquals(1, entity.toDomain().actionItems.size)
    }

    @Test
    fun `decode preserves text containing the field separator beyond the third field`() {
        // split(limit = 3) keeps everything after the second separator in the text field.
        val entity =
            com.notova.data.db.SummaryEntity(
                recordingId = "rec",
                style = "concise",
                contentMarkdown = "md",
                actionItemsBlob = "id|false|call A|B about the | budget",
                model = "m",
                generatedAtEpochMs = 0,
            )
        val item = entity.toDomain().actionItems.single()
        assertEquals("id", item.id)
        assertEquals(false, item.done)
        assertEquals("call A|B about the | budget", item.text)
    }

    @Test
    fun `decode treats a non-true done token as false`() {
        val entity =
            com.notova.data.db.SummaryEntity(
                recordingId = "rec",
                style = "concise",
                contentMarkdown = "md",
                actionItemsBlob = "id|maybe|text",
                model = "m",
                generatedAtEpochMs = 0,
            )
        assertEquals(false, entity.toDomain().actionItems.single().done)
    }
}
