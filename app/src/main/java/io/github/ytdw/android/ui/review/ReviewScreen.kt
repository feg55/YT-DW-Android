package io.github.ytdw.android.ui.review

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.ui.AppViewModel
import io.github.ytdw.android.ui.UiStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReviewScreen(viewModel: AppViewModel, strings: UiStrings) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    if (queue.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { Text(strings.nothingFound) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(queue, key = DownloadItem::id) { item ->
            ReviewCard(item, strings, viewModel::updateItem, viewModel::updateSelection)
        }
    }
}

@Composable
private fun ReviewCard(
    item: DownloadItem,
    strings: UiStrings,
    update: (DownloadItem) -> Unit,
    updateSelection: (String, Boolean) -> Unit,
) {
    var selected by rememberSaveable(item.id) { mutableStateOf(item.selected) }
    var cleanedTitle by rememberSaveable(item.id) { mutableStateOf(item.cleanedTitle) }
    var artist by rememberSaveable(item.id) { mutableStateOf(item.artist) }
    var albumArtist by rememberSaveable(item.id) { mutableStateOf(item.albumArtist) }
    var album by rememberSaveable(item.id) { mutableStateOf(item.album) }
    var trackNumber by rememberSaveable(item.id) { mutableStateOf(item.trackNumber?.toString().orEmpty()) }
    var titleEdited by rememberSaveable(item.id) { mutableStateOf(item.titleManuallyEdited) }
    var artistEdited by rememberSaveable(item.id) { mutableStateOf(item.artistManuallyEdited) }
    var albumEdited by rememberSaveable(item.id) { mutableStateOf(item.albumManuallyEdited) }
    var trackEdited by rememberSaveable(item.id) { mutableStateOf(item.trackManuallyEdited) }

    fun editedItem() = item.copy(
        cleanedTitle = cleanedTitle,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        trackNumber = trackNumber.toIntOrNull()?.takeIf { it > 0 },
        titleManuallyEdited = titleEdited,
        artistManuallyEdited = artistEdited,
        albumManuallyEdited = albumEdited,
        trackManuallyEdited = trackEdited,
    )

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(selected, {
                    selected = it
                    updateSelection(item.id, it)
                })
                item.cachedThumbnailPath?.let { path ->
                    PreviewImage(path)
                }
                Column { Text(item.originalTitle); Text(item.channel.ifEmpty { item.uploader }) }
            }
            OutlinedTextField(
                cleanedTitle,
                {
                    cleanedTitle = it
                    titleEdited = true
                    update(editedItem())
                },
                label = { Text(strings.title) }, modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    artist,
                    {
                        artist = it
                        artistEdited = true
                        update(editedItem())
                    },
                    label = { Text(strings.artist) }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    albumArtist,
                    {
                        albumArtist = it
                        update(editedItem())
                    },
                    label = { Text(strings.albumArtist) }, modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    album,
                    {
                        album = it
                        albumEdited = true
                        update(editedItem())
                    },
                    label = { Text(strings.album) }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    trackNumber,
                    {
                        trackNumber = it
                        trackEdited = true
                        update(editedItem())
                    },
                    label = { Text(strings.track) }, modifier = Modifier.weight(0.45f),
                )
            }
        }
    }
}

@Composable
private fun PreviewImage(path: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodePreview(path, targetSize = 256) }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(84.dp))
    }
}

private fun decodePreview(path: String, targetSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= targetSize) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}
