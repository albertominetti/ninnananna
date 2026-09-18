package com.alberto.ninnananna

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Scarica una ninnananna da YouTube in **background** con WorkManager.
 *
 * Durante il download mostra una **notifica foreground permanente** con
 * l'avanzamento; il file viene comunque salvato in filesDir/lullabies da
 * [DownloadRepository]. L'esecuzione continua anche se l'app va in background
 * o viene chiusa.
 */
class DownloadLullabyWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)?.trim()
        if (url.isNullOrEmpty()) {
            return Result.failure(workDataOf(KEY_ERROR to "URL non valido."))
        }

        ensureNotificationChannel(applicationContext)

        // Avvia subito la notifica foreground permanente.
        setForeground(createForegroundInfo(0f, "Avvio download…"))

        return try {
            val lullaby = DownloadRepository.download(applicationContext, url) { progress ->
                val pct = (progress * 100).toInt().coerceIn(0, 100)
                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                setForegroundAsync(
                    createForegroundInfo(progress, "Download in corso… $pct%")
                )
            }
            setForegroundAsync(createForegroundInfo(1f, "Download completato"))
            Result.success(
                workDataOf(KEY_TITLE to (lullaby?.title ?: ""))
            )
        } catch (e: Exception) {
            Result.failure(
                workDataOf(KEY_ERROR to (e.message ?: "Errore durante il download."))
            )
        }
    }

    /**
     * Usato da WorkManager per ri-creare la notifica (es. worker riavviato
     * dopo un kill del processo).
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(0f, "Preparazione download…")

    private fun createForegroundInfo(progress: Float, text: String): ForegroundInfo {
        val indeterminate = progress <= 0f
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle("NinnaNanna")
            .setContentText(text)
            .setProgress(100, (progress * 100).toInt().coerceIn(0, 100), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return ForegroundInfo(
            notificationId(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    /** Id stabile per lo stesso lavoro (anche dopo un riavvio del processo). */
    private fun notificationId(): Int =
        NOTIFICATION_ID + (id.hashCode() and 0x1F)

    companion object {
        const val TAG_DOWNLOAD = "lullaby_download"
        const val KEY_URL = "url"
        const val KEY_PROGRESS = "progress"
        const val KEY_TITLE = "title"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1001

        /**
         * Accoda il download in background (WorkManager). Se l'app viene
         * chiusa il lavoro continua; la notifica foreground mostra l'avanzamento.
         */
        fun enqueue(context: Context, url: String) {
            ensureNotificationChannel(context)
            val request = OneTimeWorkRequestBuilder<DownloadLullabyWorker>()
                .setInputData(workDataOf(KEY_URL to url))
                .addTag(TAG_DOWNLOAD)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun ensureNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Download ninnenanne",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Avanzamento dei download in background" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}