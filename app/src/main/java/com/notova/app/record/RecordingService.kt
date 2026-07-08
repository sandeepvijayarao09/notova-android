package com.notova.app.record

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.notova.app.notifications.NotovaNotifications

/**
 * Microphone foreground service. It holds the `microphone`-typed foreground state and the ongoing
 * "Recording" notification for the lifetime of a capture, so recording survives the app being
 * backgrounded and the declared `FOREGROUND_SERVICE_MICROPHONE` permission is justified. The audio
 * capture itself is driven by the recorder in the record feature; this service owns only the
 * foreground lifecycle (started/stopped via [RecordingForegroundController]).
 */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> startRecordingForeground()
        }
        return START_NOT_STICKY
    }

    private fun startRecordingForeground() {
        // FOREGROUND_SERVICE_TYPE_MICROPHONE (and the manifest `microphone` type) is API 30+.
        // On older releases a plain foreground service is sufficient to keep mic capture alive.
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
        ServiceCompat.startForeground(
            this,
            NotovaNotifications.ID_RECORDING,
            NotovaNotifications.recordingNotification(this),
            type,
        )
    }

    companion object {
        const val ACTION_START = "com.notova.app.record.action.START"
        const val ACTION_STOP = "com.notova.app.record.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecordingService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
