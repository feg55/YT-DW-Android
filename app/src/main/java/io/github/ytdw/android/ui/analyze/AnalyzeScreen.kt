package io.github.ytdw.android.ui.analyze

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ytdw.android.domain.model.DownloadMode
import io.github.ytdw.android.domain.model.VideoQuality
import io.github.ytdw.android.ui.AppViewModel
import io.github.ytdw.android.ui.UiStrings
import io.github.ytdw.android.ui.isRussian

@Composable
fun AnalyzeScreen(viewModel: AppViewModel, strings: UiStrings) {
    val draft by viewModel.urlDraft.collectAsState()
    val running by viewModel.analysisRunning.collectAsState()
    val foundCount by viewModel.analysisFoundCount.collectAsState()
    val errors by viewModel.analysisErrors.collectAsState()
    val mode by viewModel.selectedMode.collectAsState()
    val quality by viewModel.selectedQuality.collectAsState()
    val settings by viewModel.settings.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(16.dp).widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft, onValueChange = { viewModel.urlDraft.value = it },
            label = { Text(strings.urls) }, minLines = 5, modifier = Modifier.fillMaxWidth(),
            enabled = !running,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == DownloadMode.AUDIO, { viewModel.selectedMode.value = DownloadMode.AUDIO }, { Text(strings.audio) })
            FilterChip(mode == DownloadMode.VIDEO, { viewModel.selectedMode.value = DownloadMode.VIDEO }, { Text(strings.video) })
        }
        if (mode == DownloadMode.VIDEO) {
            Text(strings.quality)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoQuality.entries.forEach { item ->
                    FilterChip(quality == item, { viewModel.selectedQuality.value = item }, { Text(item.value) })
                }
            }
        }
        Text(
            "${strings.outputHint}: " +
                "${settings.audioVolumeName}:${settings.audioRelativePath}; " +
                "${settings.videoVolumeName}:${settings.videoRelativePath}",
        )
        if (running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                when {
                    foundCount == 0 && isRussian(settings.language) -> "Подключение к источнику…"
                    foundCount == 0 -> "Connecting to source…"
                    isRussian(settings.language) -> "Найдено: $foundCount"
                    else -> "Found: $foundCount"
                },
            )
        }
        Button(onClick = { if (running) viewModel.cancelAnalysis() else viewModel.analyze() }) {
            Text(if (running) strings.cancel else strings.analyze)
        }
        errors.forEach { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
    }
}
