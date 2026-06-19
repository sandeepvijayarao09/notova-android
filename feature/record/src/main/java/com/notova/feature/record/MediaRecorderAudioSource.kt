package com.notova.feature.record

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.notova.core.audio.AudioCaptureResult
import com.notova.core.audio.AudioSource
import com.notova.core.model.RecordingSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis

/**
 * [AudioSource] backed by [MediaRecorder] for capture, with Bluetooth SCO routing when a BT mic
 * is selected, and Storage Access Framework import via [loadFromUri].
 *
 * This is a working scaffold: it records real audio to the app cache and copies imported files.
 * Routing to "OTHER" arbitrary input devices is left as a TODO once device enumeration is wired.
 */
class MediaRecorderAudioSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AudioSource {
        private var recorder: MediaRecorder? = null
        private var outputFile: File? = null
        private var startedAtMs: Long = 0
        private var bluetoothRouted: Boolean = false

        override suspend fun start() =
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                outputFile = file

                maybeStartBluetoothSco()

                val mr =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                mr.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                recorder = mr
                startedAtMs = System.currentTimeMillis()
            }

        override suspend fun stop(): AudioCaptureResult =
            withContext(Dispatchers.IO) {
                val mr = recorder
                val file = outputFile
                checkNotNull(mr) { "stop() called before start()" }
                checkNotNull(file) { "no output file" }

                runCatching {
                    mr.stop()
                }
                mr.release()
                recorder = null

                maybeStopBluetoothSco()

                val durationSec = (System.currentTimeMillis() - startedAtMs) / MILLIS_PER_SECOND
                AudioCaptureResult(
                    outputFilePath = file.absolutePath,
                    durationSec = durationSec,
                    source = if (bluetoothRouted) RecordingSource.BLUETOOTH else RecordingSource.MIC,
                )
            }

        override suspend fun loadFromUri(uri: String): AudioCaptureResult =
            withContext(Dispatchers.IO) {
                val source = Uri.parse(uri)
                val dest = File(context.cacheDir, "import_${System.currentTimeMillis()}")
                context.contentResolver.openInputStream(source).use { input ->
                    checkNotNull(input) { "cannot open $uri" }
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                // Real duration extraction would use MediaMetadataRetriever; 0.0 until then.
                AudioCaptureResult(
                    outputFilePath = dest.absolutePath,
                    durationSec = 0.0,
                    source = RecordingSource.FILE,
                )
            }

        private fun maybeStartBluetoothSco() {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (audioManager.isBluetoothScoAvailableOffCall) {
                measureTimeMillis {
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                }
                bluetoothRouted = true
            }
        }

        private fun maybeStopBluetoothSco() {
            if (!bluetoothRouted) return
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            bluetoothRouted = false
        }

        private companion object {
            const val MILLIS_PER_SECOND = 1000.0
        }
    }
