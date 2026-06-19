package com.notova.ai.transcribe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SpeechRecognitionEngine] over Android's on-device [SpeechRecognizer].
 *
 * Availability is fully guarded and never throws:
 *  - on-device recognition must be supported (API 31+ `isOnDeviceRecognitionAvailable`),
 *  - the RECORD_AUDIO permission must be granted, and
 *  - a recognition service must be present.
 *
 * [recognize] is the integration point. `SpeechRecognizer` consumes a *live* audio stream; feeding
 * a pre-recorded file into it (decode + replay through an audio-source shim, or the API 33+
 * file-based recognition intent) is the remaining device-side work. Until that is wired, recognize
 * throws so the [ResolvingTranscriber] degrades to the stub. We keep the gating logic real and unit
 * tested via [SpeechRecognitionEngine] fakes so the device-side feed can drop in without touching
 * the resolver.
 */
@Singleton
class AndroidSpeechRecognitionEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SpeechRecognitionEngine {
        override suspend fun isAvailable(): Boolean =
            withContext(Dispatchers.Main.immediate) {
                runCatching {
                    if (!hasRecordAudioPermission()) return@runCatching false
                    val onDeviceSupported =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                    onDeviceSupported && SpeechRecognizer.isRecognitionAvailable(context)
                }.getOrDefault(false) &&
                    // File-based recognition not yet wired (see class doc): stay unavailable so the
                    // resolver falls through to the stub instead of failing at recognize() time.
                    FILE_RECOGNITION_WIRED
            }

        override suspend fun recognize(audioPath: String): RecognitionResult =
            throw UnsupportedOperationException(
                "On-device file recognition is not wired yet; feed $audioPath through SpeechRecognizer.",
            )

        private fun hasRecordAudioPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        private companion object {
            // Flip to true once recognize() feeds the audio file through SpeechRecognizer.
            const val FILE_RECOGNITION_WIRED = false
        }
    }
