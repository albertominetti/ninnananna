package com.alberto.ninnananna

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * **Persistent** (ongoing) notification shown while a track is playing,
 * without using a foreground service: it is a normal notification with ongoing=true.
 *
 * - [show] posts the notification with the track title + "Now playing"
 *   and a "Stop" action that stops the audio via [PlaybackStopReceiver].
 * - Tap on the notification -> reopens [MainActivity].
 * - [hide] removes the notification (at the end of playback, stop or release).
 */
object PlaybackNotification {

    private const val CHANNEL_ID = "playback"
    private const val NOTIFICATION_ID = 1001

    const val ACTION_STOP = "com.alberto.ninnananna.action.STOP_PLAYBACK"

    /** Shows (or updates) the persistent notification of the track being played. */
    fun show(context: Context, lullabyTitle: String, statusText: String? = null) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        val status = statusText ?: appContext.getString(R.string.now_playing)

        // Android 13+: if POST_NOTIFICATIONS is not granted we show nothing
        // (silent fallback; also avoids the SecurityException of notify()).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        ) return

        // Tap on the notification -> reopens MainActivity
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Stop" action -> BroadcastReceiver that stops the audio and hides the notification
        val stopIntent = Intent(appContext, PlaybackStopReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            appContext,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(lullabyTitle)
            .setContentText(status)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_download,
                    appContext.getString(R.string.stop),
                    stopPendingIntent
                )
            )
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    /** Removes the persistent notification (stop, end of track or release). */
    fun hide(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_playback),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.channel_playback_desc)
                }
            )
        }
    }
}

/**
 * Receives the "Stop" action from the persistent notification and stops the playback.
 */
class PlaybackStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlaybackNotification.ACTION_STOP) return
        val appContext = context.applicationContext
        PlayerManager.stop()
        CastManager.stopStreaming()
        PlaybackNotification.hide(appContext)
    }
}