package com.notova.integrations.api

import com.notova.core.model.ActionItem
import com.notova.core.model.Recording
import com.notova.core.model.RecordingSource
import com.notova.core.model.RecordingStatus
import com.notova.core.model.Summary
import com.notova.core.model.Transcript
import com.notova.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Pins how on-device domain models map onto the backend `/v1` contract DTOs: enum casing,
 * ISO-8601 dates, ms -> sec on transcript segments, and the metadata-only sync upsert body.
 */
class ContractMappersTest {
    private val recording =
        Recording(
            id = "rec-1",
            title = "Standup",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            durationSec = 42.5,
            source = RecordingSource.BLUETOOTH,
            localAudioPath = "/tmp/audio.m4a",
            status = RecordingStatus.READY,
        )

    @Test
    fun `recording enums map to lowercase contract strings`() {
        assertEquals("bluetooth", RecordingSource.BLUETOOTH.toContractString())
        assertEquals("mic", RecordingSource.MIC.toContractString())
        assertEquals("file", RecordingSource.FILE.toContractString())
        assertEquals("other", RecordingSource.OTHER.toContractString())
        assertEquals("recording", RecordingStatus.RECORDING.toContractString())
        assertEquals("processing", RecordingStatus.PROCESSING.toContractString())
        assertEquals("ready", RecordingStatus.READY.toContractString())
        assertEquals("failed", RecordingStatus.FAILED.toContractString())
    }

    @Test
    fun `recording maps to a contract recording dto with iso date`() {
        val dto = recording.toRecordingDto()
        assertEquals("rec-1", dto.id)
        assertEquals("Standup", dto.title)
        assertEquals("2026-01-01T00:00:00Z", dto.createdAt)
        assertEquals(42.5, dto.durationSec, 0.0)
        assertEquals("bluetooth", dto.source)
        assertEquals("ready", dto.status)
    }

    @Test
    fun `transcript segments convert milliseconds to seconds`() {
        val transcript =
            Transcript(
                recordingId = "rec-1",
                language = "en",
                fullText = "hello there",
                segments =
                    listOf(
                        TranscriptSegment(startMs = 0, endMs = 1500, text = "hello", speaker = "A"),
                        TranscriptSegment(startMs = 1500, endMs = 3250, text = "there"),
                    ),
            )

        val dto = transcript.toTranscriptDto()
        assertEquals("hello there", dto.text)
        assertEquals("en", dto.language)
        assertEquals(2, dto.segments?.size)
        assertEquals(0.0, dto.segments!![0].startSec!!, 0.0)
        assertEquals(1.5, dto.segments[0].endSec!!, 0.0)
        assertEquals("A", dto.segments[0].speaker)
        assertEquals(1.5, dto.segments[1].startSec!!, 0.0)
        assertEquals(3.25, dto.segments[1].endSec!!, 0.0)
    }

    @Test
    fun `empty transcript segments serialize as absent`() {
        val transcript =
            Transcript(recordingId = "rec-1", language = "en", fullText = "x", segments = emptyList())
        assertNull(transcript.toTranscriptDto().segments)
    }

    @Test
    fun `summary maps markdown to text and carries action items`() {
        val summary =
            Summary(
                recordingId = "rec-1",
                style = "concise",
                contentMarkdown = "## Notes\n- a",
                actionItems = listOf(ActionItem(id = "a1", text = "Email Bob", done = false)),
                model = "stub",
                generatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )

        val dto = summary.toSummaryDto()
        assertEquals("## Notes\n- a", dto.text)
        assertEquals(1, dto.actionItems?.size)
        assertEquals("a1", dto.actionItems!![0].id)
        assertEquals("Email Bob", dto.actionItems[0].text)
        assertEquals(false, dto.actionItems[0].done)
    }

    @Test
    fun `empty action items serialize as absent`() {
        val summary =
            Summary(
                recordingId = "rec-1",
                style = "concise",
                contentMarkdown = "x",
                actionItems = emptyList(),
                model = "stub",
                generatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
        assertNull(summary.toSummaryDto().actionItems)
    }

    @Test
    fun `sync upsert request carries metadata only and omits the id`() {
        val req = recording.toSyncUpsertRequest()
        assertEquals("Standup", req.title)
        assertEquals("2026-01-01T00:00:00Z", req.createdAt)
        assertEquals(42.5, req.durationSec, 0.0)
        assertEquals("bluetooth", req.source)
        assertEquals("ready", req.status)
        // SyncRecordingUpsertRequest has no id field by construction — the id is the path param.
        val fields = SyncRecordingUpsertRequest::class.java.declaredFields.map { it.name }
        assertEquals(false, fields.contains("id"))
    }
}
