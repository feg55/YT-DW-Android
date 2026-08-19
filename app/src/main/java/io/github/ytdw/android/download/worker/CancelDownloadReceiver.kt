package io.github.ytdw.android.download.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ytdw.android.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CancelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadNotifications.ACTION_CANCEL) return
        val itemId = intent.getStringExtra("item_id") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.appContainer.downloadEngine.cancel(itemId)
                context.appContainer.queueRepository.cancel(itemId)
            } finally {
                pending.finish()
            }
        }
    }
}
