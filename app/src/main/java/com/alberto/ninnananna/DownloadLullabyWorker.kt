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
 * Downloads a lullaby from YouTube in the **background** with WorkManager.
 *
 * During the download it shows a **permanent foreground notification** with
 * the progress; the file is still saved into filesDir/lullabies by
 * [DownloadRepository]. Execution continues even if the app goes to the
 * background or is closed.
 */
class DownloadLullabyWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)?.trim()
        if (url.isNullOrEmpty()) {
            return Result.failure(
                workDataOf(KEY_ERROR to applicationContext.getString(R.string.invalid_url))
            )
        }

        ensureNotificationChannel(applicationContext)

        // Starts the permanent foreground notification right away.
        setForeground(
            createForegroundInfo(0f, applicationContext.getString(R.string.starting_download))
        )

        return try {
            val lullaby = DownloadRepository.download(applicationContext, url) { progress ->
                val pct = (progress * 100).toInt().coerceIn(0, 100)
                setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                setForegroundAsync(
                    createForegroundInfo(
                        progress,
                        applicationContext.getString(R.string.downloading_progress, pct)
                    )
                )
            }
            setForegroundAsync(
                createForegroundInfo(1f, applicationContext.getString(R.string.download_completed))
            )
            val title = lullaby?.title
                ?.let { formatDisplayName(applicationContext, it) }
                .orEmpty()
            if (title.isNotBlank()) {
                setForegroundAsync(
                    createForegroundInfo(
                        1f,
                        applicationContext.getString(R.string.download_completed_title, title)
                    )
                )
            }
            Result.success(
                workDataOf(KEY_TITLE to (lullaby?.title ?: ""))
            )
        } catch (e: Exception) {
            Result.failure(
                workDataOf(
                    KEY_ERROR to (e.message
                        ?: applicationContext.getString(R.string.download_error))
                )
            )
        }
    }

    /**
     * Used by WorkManager to re-create the notification (e.g. worker
     * restarted after a process kill).
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(0f, applicationContext.getString(R.string.preparing_download))

    private fun createForegroundInfo(progress: Float, text: String): ForegroundInfo {
        val indeterminate = progress <= 0f
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(applicationContext.getString(R.string.app_name))
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

    /** Stable id for the same work (also after a process restart). */
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
         * Queues the download in the background (WorkManager). If the app is
         * closed the work continues; the foreground notification shows the progress.
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
                context.getString(R.string.channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.channel_downloads_desc) }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}