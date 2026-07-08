package com.notova.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.notova.app.MainActivity
import com.notova.app.R

/**
 * All of Notova's notifications are **local**: an ongoing "Recording" status while the microphone
 * foreground service runs, a "Processing recording…" status while a note is transcribed/summarized
 * in the background, and a "Note ready" alert when it finishes. Nothing is sent to a server and
 * there is no push — `POST_NOTIFICATIONS` is used only for these.
 */
object NotovaNotifications {
    const val CHANNEL_RECORDING = "recording"
    const val CHANNEL_PROCESSING = "processing"

    const val ID_RECORDING = 1001
    const val ID_PROCESSING = 1002
    const val ID_READY = 1003

    /** Registers the notification channels. Idempotent; call once from `Application.onCreate()`. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                "Recording",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shown while Notova is recording audio." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROCESSING,
                "Processing",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Transcription/summarization status and when a note is ready."
            },
        )
    }

    /** The ongoing notification the microphone foreground service shows while recording. */
    fun recordingNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setContentTitle("Recording")
            .setContentText("Notova is recording audio on this device.")
            .setSmallIcon(R.drawable.ic_stat_notova)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent(context))
            .build()

    /** Posts an ongoing "Processing recording…" notification while a note is processed in the
     *  background (e.g. a long import). Replaced by [notifyNoteReady] / cleared by [cancelProcessing]. */
    fun notifyProcessing(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification =
            NotificationCompat.Builder(context, CHANNEL_PROCESSING)
                .setContentTitle("Processing recording")
                .setContentText("Transcribing and summarizing on this device…")
                .setSmallIcon(R.drawable.ic_stat_notova)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build()
        NotificationManagerCompat.from(context).notify(ID_PROCESSING, notification)
    }

    /** Clears the "Processing recording…" notification (e.g. on failure). */
    fun cancelProcessing(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_PROCESSING)
    }

    /**
     * Replaces any "Processing recording…" notification with a "Note ready" completion alert.
     * `POST_NOTIFICATIONS` is only a runtime permission on API 33+; below that `checkSelfPermission`
     * reports it granted. No-op (never crashes) when the user has denied notifications.
     */
    fun notifyNoteReady(
        context: Context,
        title: String,
    ) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(ID_PROCESSING)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification =
            NotificationCompat.Builder(context, CHANNEL_PROCESSING)
                .setContentTitle("Note ready")
                .setContentText("“$title” has been transcribed and summarized.")
                .setSmallIcon(R.drawable.ic_stat_notova)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
        manager.notify(ID_READY, notification)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
