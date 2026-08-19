package io.github.ytdw.android.download.worker

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object DownloadScheduler {
    const val JOB_ID = 0x59544457
    private const val JOB_NAMESPACE = "io.github.ytdw.android.downloads"

    fun start(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, DownloadJobService::class.java))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresStorageNotLow(true)
                .build()
            val scheduler = context.getSystemService(JobScheduler::class.java).forNamespace(JOB_NAMESPACE)
            check(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) {
                "Android rejected the user-initiated download job"
            }
        } else {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("download-queue", ExistingWorkPolicy.KEEP, request)
        }
    }
}
