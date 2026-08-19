package io.github.ytdw.android.download.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.ytdw.android.appContainer
import kotlinx.coroutines.CancellationException

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            setForeground(foreground(null, "Preparing", null))
            QueueProcessor(applicationContext).run { item, text, progress ->
                setForeground(foreground(item, text, progress))
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            applicationContext.appContainer.errorLogger.log(
                "scheduler",
                error.message ?: "Download worker failed",
                error.stackTraceToString(),
            )
            Result.retry()
        }
    }

    private fun foreground(item: io.github.ytdw.android.domain.model.DownloadItem?, text: String, progress: Int?) =
        ForegroundInfo(
            DownloadNotifications.NOTIFICATION_ID,
            DownloadNotifications.build(applicationContext, item, text, progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
}
