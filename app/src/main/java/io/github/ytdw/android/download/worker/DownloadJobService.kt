package io.github.ytdw.android.download.worker

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.ytdw.android.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// This service uses a dedicated JobScheduler namespace, so WorkManager IDs cannot collide with it.
@SuppressLint("SpecifyJobSchedulerIdRange")
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DownloadJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val initial = DownloadNotifications.build(this, null, "Preparing", null)
        setNotification(params, DownloadNotifications.NOTIFICATION_ID, initial, JOB_END_NOTIFICATION_POLICY_REMOVE)
        job = scope.launch {
            try {
                QueueProcessor(this@DownloadJobService).run { item, text, progress ->
                    setNotification(
                        params, DownloadNotifications.NOTIFICATION_ID,
                        DownloadNotifications.build(this@DownloadJobService, item, text, progress),
                        JOB_END_NOTIFICATION_POLICY_REMOVE,
                    )
                }
                jobFinished(params, false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                appContainer.errorLogger.log(
                    "scheduler",
                    error.message ?: "Download job failed",
                    error.stackTraceToString(),
                )
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
