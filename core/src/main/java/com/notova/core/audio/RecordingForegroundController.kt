package com.notova.core.audio

/**
 * Owns the microphone foreground-service lifecycle for a live recording: while a capture is active
 * the app runs a `microphone`-typed foreground service (with an ongoing notification) so recording
 * keeps going if the app is backgrounded and the `FOREGROUND_SERVICE_MICROPHONE` permission is
 * justified. Implemented in the app module; injected into the recorder so the feature/ViewModel
 * layer stays free of Android `Service` plumbing (and is trivially fakeable in tests).
 */
interface RecordingForegroundController {
    /** Start (or keep) the foreground microphone service and its ongoing notification. */
    fun start()

    /** Tear down the foreground service and remove its notification. */
    fun stop()
}
