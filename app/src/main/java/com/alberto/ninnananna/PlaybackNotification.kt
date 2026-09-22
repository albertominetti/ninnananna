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
 * Notifica **persistente** (ongoing) mostrata mentre un brano è in riproduzione,
 * senza usare un foreground service: è una normale notifica con ongoing=true.
 *
 * - [show] pubblica la notifica con il titolo del brano + "In riproduzione"
 *   e un'azione "Stop" che ferma l'audio via [PlaybackStopReceiver].
 * - Tap sulla notifica -> riapre [MainActivity].
 * - [hide] rimuove la notifica (alla fine della riproduzione, stop o release).
 */
object PlaybackNotification {

    private const val CHANNEL_ID = "playback"
    private const val CHANNEL_NAME = "Riproduzione"
    private const val NOTIFICATION_ID = 1001

    const val ACTION_STOP = "com.alberto.ninnananna.action.STOP_PLAYBACK"

    /** Mostra (o aggiorna) la notifica persistente del brano in riproduzione. */
    fun show(context: Context, lullabyTitle: String, statusText: String = "In riproduzione") {
        val appContext = context.applicationContext
        ensureChannel(appContext)

        // Android 13+: se POST_NOTIFICATIONS non è concesso non mostriamo nulla
        // (fallback silenzioso; evita anche la SecurityException di notify()).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        ) return

        // Tap sulla notifica -> riapre MainActivity
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Azione "Stop" -> BroadcastReceiver che ferma l'audio e nasconde la notifica
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
            .setContentText(statusText)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_notification_download,
                    "Stop",
                    stopPendingIntent
                )
            )
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    /** Rimuove la notifica persistente (stop, fine brano o release). */
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
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifica persistente durante la riproduzione"
                }
            )
        }
    }
}

/**
 * Riceve l'azione "Stop" dalla notifica persistente e ferma la riproduzione.
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