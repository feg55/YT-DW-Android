package io.github.ytdw.android.ui.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.domain.model.DownloadStatus
import io.github.ytdw.android.ui.AppViewModel
import io.github.ytdw.android.ui.UiStrings

@Composable
fun DownloadsScreen(viewModel: AppViewModel, strings: UiStrings) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val queueError by viewModel.queueError.collectAsStateWithLifecycle()
    val confirm = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startQueue()
    }
    fun requestQueueStart() {
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (notificationGranted) {
            viewModel.startQueue()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(::requestQueueStart) { Text(strings.start) }
            OutlinedButton(viewModel::pauseQueue) { Text(strings.pause) }
            OutlinedButton(viewModel::cancelCurrent) { Text(strings.cancel) }
        }
        queueError?.let {
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(viewModel::retryFailed) { Text(strings.retryFailed) }
            OutlinedButton(viewModel::removeCompleted) { Text(strings.removeCompleted) }
        }
        Text("${strings.history}: ${history.size}")
        OutlinedButton(
            { confirm.value = true },
            enabled = queue.none { it.status.isActive },
        ) { Text(strings.clearAll) }
        if (queue.isEmpty()) Text(strings.queueEmpty)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(queue, key = DownloadItem::id) { item -> DownloadRow(item, strings, settings.language, viewModel) }
        }
    }
    if (confirm.value) AlertDialog(
        onDismissRequest = { confirm.value = false },
        title = { Text(strings.clearAll) }, text = { Text(strings.confirmClear) },
        confirmButton = { TextButton({ viewModel.clearAll(); confirm.value = false }) { Text(strings.confirm) } },
        dismissButton = { TextButton({ confirm.value = false }) { Text(strings.cancel) } },
    )
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    strings: UiStrings,
    language: io.github.ytdw.android.domain.model.LanguagePreference,
    viewModel: AppViewModel,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(item.cleanedTitle.ifEmpty { item.originalTitle })
        Text("${io.github.ytdw.android.ui.localizedStatus(item.status.name, language)} · ${io.github.ytdw.android.ui.localizedPhase(item.currentPhase, language)}")
        LinearProgressIndicator(
            progress = { (item.progressPercentage / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        val speed = item.speed?.let { "%.1f MiB/s".format(it / 1024 / 1024) } ?: "—"
        val eta = item.etaSeconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: "—"
        Text("${strings.progress}: ${"%.1f".format(item.progressPercentage)}% · ${strings.speed}: $speed · ${strings.eta}: $eta")
        item.errorMessage?.let {
            Text(
                io.github.ytdw.android.ui.localizedError(item.errorCategory, it, language),
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.status == DownloadStatus.COMPLETED && item.contentUri != null) {
                TextButton({ viewModel.open(item) }) { Text(strings.open) }
            }
            if (item.status in setOf(DownloadStatus.FAILED, DownloadStatus.CANCELLED)) {
                TextButton({ viewModel.retry(item) }) { Text(strings.retryFailed) }
            }
        }
    }
}
