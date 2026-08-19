package io.github.ytdw.android.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ytdw.android.domain.model.AppSettings
import io.github.ytdw.android.domain.model.LanguagePreference
import io.github.ytdw.android.domain.model.MetadataSettings
import io.github.ytdw.android.domain.model.ThemePreference
import io.github.ytdw.android.ui.UiStrings
import io.github.ytdw.android.ui.isRussian
import io.github.ytdw.android.util.MediaStoreLocation
import io.github.ytdw.android.util.MediaStoreLocationParser

@Composable
fun SettingsDialog(
    settings: AppSettings,
    strings: UiStrings,
    onChange: (AppSettings) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val russian = isRussian(settings.language)
    var storageError by remember { mutableStateOf<String?>(null) }
    fun applyLocation(uri: Uri?, root: String, update: (MediaStoreLocation) -> AppSettings) {
        if (uri == null) return
        runCatching { locationFromTree(context, uri, root) }
            .onSuccess { location -> storageError = null; onChange(update(location)) }
            .onFailure {
                storageError = if (russian) {
                    "Выберите папку внутри $root на доступном накопителе"
                } else {
                    it.message ?: "Select a folder inside $root on an available storage volume"
                }
            }
    }
    val audioFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        applyLocation(uri, "Music") { location ->
            settings.copy(audioVolumeName = location.volumeName, audioRelativePath = location.relativePath)
        }
    }
    val videoFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        applyLocation(uri, "Movies") { location ->
            settings.copy(videoVolumeName = location.volumeName, videoRelativePath = location.relativePath)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(strings.settings) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(strings.theme)
                FlowRow {
                    ThemePreference.entries.forEach { theme ->
                        FilterChip(
                            settings.theme == theme, { onChange(settings.copy(theme = theme)) },
                            { Text(themeLabel(theme, strings)) }, Modifier.padding(end = 4.dp),
                        )
                    }
                }
                Text(strings.language)
                FlowRow {
                    LanguagePreference.entries.forEach { language ->
                        FilterChip(
                            settings.language == language, { onChange(settings.copy(language = language)) },
                            { Text(languageLabel(language, strings)) }, Modifier.padding(end = 4.dp),
                        )
                    }
                }
                OutputFolderSetting(
                    label = if (russian) "Папка для аудио" else "Audio folder",
                    value = "${settings.audioVolumeName}:${settings.audioRelativePath}",
                    choose = if (russian) "Выбрать" else "Choose",
                    onChoose = { audioFolderPicker.launch(null) },
                )
                OutputFolderSetting(
                    label = if (russian) "Папка для видео" else "Video folder",
                    value = "${settings.videoVolumeName}:${settings.videoRelativePath}",
                    choose = if (russian) "Выбрать" else "Choose",
                    onChoose = { videoFolderPicker.launch(null) },
                )
                storageError?.let {
                    Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
                MetadataSwitch(strings.useChannelArtist, settings.metadata.useChannelAsArtist) {
                    onChange(settings.withMetadata { copy(useChannelAsArtist = it) })
                }
                MetadataSwitch(strings.channelAlbumArtist, settings.metadata.useChannelAsAlbumArtist) {
                    onChange(settings.withMetadata { copy(useChannelAsAlbumArtist = it) })
                }
                MetadataSwitch(strings.removeChannel, settings.metadata.removeChannelFromTitle) {
                    onChange(settings.withMetadata { copy(removeChannelFromTitle = it) })
                }
                MetadataSwitch(strings.removeLabels, settings.metadata.removeLabels) {
                    onChange(settings.withMetadata { copy(removeLabels = it) })
                }
                MetadataSwitch(strings.playlistAlbum, settings.metadata.usePlaylistTitleAsAlbum) {
                    onChange(settings.withMetadata { copy(usePlaylistTitleAsAlbum = it) })
                }
                MetadataSwitch(strings.playlistTrack, settings.metadata.usePlaylistIndexAsTrack) {
                    onChange(settings.withMetadata { copy(usePlaylistIndexAsTrack = it) })
                }
                MetadataSwitch(strings.cleanedFilename, settings.metadata.useCleanedTitleAsFilename) {
                    onChange(settings.withMetadata { copy(useCleanedTitleAsFilename = it) })
                }
                MetadataSwitch(strings.embedCover, settings.metadata.embedThumbnailAsCover) {
                    onChange(settings.withMetadata { copy(embedThumbnailAsCover = it) })
                }
                MetadataSwitch(strings.squareCover, settings.metadata.cropCoverToSquare) {
                    onChange(settings.withMetadata { copy(cropCoverToSquare = it) })
                }
                MetadataSwitch(strings.separateCover, settings.metadata.saveCoverAsSeparateJpeg) {
                    onChange(settings.withMetadata { copy(saveCoverAsSeparateJpeg = it) })
                }
                MetadataSwitch(strings.storeUrl, settings.metadata.storeOriginalUrlInComment) {
                    onChange(settings.withMetadata { copy(storeOriginalUrlInComment = it) })
                }
                MetadataSwitch(strings.storeYear, settings.metadata.storeUploadYear) {
                    onChange(settings.withMetadata { copy(storeUploadYear = it) })
                }
                SettingSwitch(strings.skipArchive, settings.skipDownloadArchive) {
                    onChange(settings.copy(skipDownloadArchive = it))
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text(strings.close) } },
    )
}

@Composable
private fun OutputFolderSetting(label: String, value: String, choose: String, onChoose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label)
        Text(value, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        OutlinedButton(onChoose) { Text(choose) }
    }
}

private fun locationFromTree(context: Context, uri: Uri, root: String): MediaStoreLocation {
    val location = MediaStoreLocationParser.fromTreeDocumentId(
        DocumentsContract.getTreeDocumentId(uri),
        root,
    )
    require(location.volumeName in MediaStore.getExternalVolumeNames(context)) {
        "The selected storage volume is not available"
    }
    return location
}

@Composable
private fun MetadataSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) =
    SettingSwitch(label, checked, onChecked)

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}

private fun AppSettings.withMetadata(block: MetadataSettings.() -> MetadataSettings): AppSettings =
    copy(metadata = metadata.block())

private fun themeLabel(value: ThemePreference, s: UiStrings) = when (value) {
    ThemePreference.DARK -> s.dark
    ThemePreference.ORIGINAL -> s.original
    ThemePreference.LIGHT -> s.light
    ThemePreference.SYSTEM -> s.system
}

private fun languageLabel(value: LanguagePreference, s: UiStrings) = when (value) {
    LanguagePreference.SYSTEM -> s.system
    LanguagePreference.RUSSIAN -> s.russian
    LanguagePreference.ENGLISH -> s.english
}
