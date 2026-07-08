package com.notova.ai.transcribe

/**
 * Derives a recording id from an audio file path: the file name without its directory or extension
 * (e.g. `/cache/rec_123.m4a` -> `rec_123`).
 *
 * Shared by [TranscriberEngine] implementations so the path -> id contract lives in one place.
 */
internal fun recordingIdFromAudioPath(audioPath: String): String =
    audioPath.substringAfterLast('/').substringBeforeLast('.')
