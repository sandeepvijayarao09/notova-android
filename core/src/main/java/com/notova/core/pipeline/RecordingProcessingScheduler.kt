package com.notova.core.pipeline

/**
 * Schedules durable, background processing (transcribe + summarize) of an already-persisted
 * recording, so the work survives the user leaving the screen or the app being backgrounded —
 * important for long imported audio files that take minutes to process on-device.
 *
 * The production implementation enqueues a WorkManager job; tests provide a fake. Callers depend on
 * this interface, not on WorkManager, keeping the Record flow free of Android scheduling details.
 */
interface RecordingProcessingScheduler {
    /** Enqueues processing for the recording with [recordingId]. Idempotent per id. */
    fun schedule(recordingId: String)
}
