package io.github.ytdw.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ytdw.android.ui.analyze.AnalyzeScreen
import io.github.ytdw.android.ui.downloads.DownloadsScreen
import io.github.ytdw.android.ui.review.ReviewScreen
import io.github.ytdw.android.ui.settings.SettingsDialog
import io.github.ytdw.android.ui.theme.YtdwTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tab by viewModel.currentTab.collectAsStateWithLifecycle()
    val strings = uiStrings(settings.language)
    val showSettings = remember { mutableStateOf(false) }
    YtdwTheme(settings.theme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.appName) },
                    actions = { TextButton({ showSettings.value = true }) { Text(strings.settings) } },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().then(Modifier.padding(padding))) {
                val labels = listOf(strings.analyzeTab, strings.reviewTab, strings.downloadTab)
                TabRow(tab) {
                    labels.forEachIndexed { index, label ->
                        Tab(tab == index, { viewModel.selectTab(index) }, text = { Text(label) })
                    }
                }
                when (tab) {
                    0 -> AnalyzeScreen(viewModel, strings)
                    1 -> ReviewScreen(viewModel, strings)
                    else -> DownloadsScreen(viewModel, strings)
                }
            }
        }
        if (showSettings.value) SettingsDialog(
            settings, strings,
            onChange = viewModel::saveSettings,
            onClose = { showSettings.value = false },
        )
    }
}
