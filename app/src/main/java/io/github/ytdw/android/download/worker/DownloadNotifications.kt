package io.github.ytdw.android.download.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.ytdw.android.MainActivity
import io.github.ytdw.android.R
import io.github.ytdw.android.domain.model.DownloadItem

object DownloadNotifications {
    const val CHANNEL_ID = "downloads"
    const val NOTIFICATION_ID = 4102
    const val ACTION_CANCEL = "io.github.ytdw.android.CANCEL_DOWNLOAD"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "YT-DW download progress" })
    }

    fun build(context: Context, item: DownloadItem?, text: String, progress: Int? = null): Notification {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(item?.cleanedTitle?.ifEmpty { item.originalTitle } ?: "YT-DW")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setProgress(100, progress ?: 0, progress == null)
        if (item != null) {
            val cancelIntent = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, CancelDownloadReceiver::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra("item_id", item.id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Cancel", cancelIntent)
        }
        return builder.build()
    }
}
