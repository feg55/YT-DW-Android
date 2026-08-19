package io.github.ytdw.android.ui.review

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.ui.AppViewModel
import io.github.ytdw.android.ui.UiStrings

@Composable
fun ReviewScreen(viewModel: AppViewModel, strings: UiStrings) {
    val queue by viewModel.queue.collectAsState()
    if (queue.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { Text(strings.nothingFound) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(queue, key = DownloadItem::id) { item -> ReviewCard(item, strings, viewModel::updateItem) }
    }
}

@Composable
private fun ReviewCard(item: DownloadItem, strings: UiStrings, update: (DownloadItem) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(item.selected, { update(item.copy(selected = it)) })
                item.cachedThumbnailPath?.let { path ->
                    remember(path) { BitmapFactory.decodeFile(path) }?.let {
                        Image(it.asImageBitmap(), null, Modifier.size(84.dp))
                    }
                }
                Column { Text(item.originalTitle); Text(item.channel.ifEmpty { item.uploader }) }
            }
            OutlinedTextField(
                item.cleanedTitle,
                { update(item.copy(cleanedTitle = it, titleManuallyEdited = true)) },
                label = { Text(strings.title) }, modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    item.artist, { update(item.copy(artist = it, artistManuallyEdited = true)) },
                    label = { Text(strings.artist) }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    item.albumArtist, { update(item.copy(albumArtist = it)) },
                    label = { Text(strings.albumArtist) }, modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    item.album, { update(item.copy(album = it, albumManuallyEdited = true)) },
                    label = { Text(strings.album) }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    item.trackNumber?.toString().orEmpty(),
                    { update(item.copy(trackNumber = it.toIntOrNull()?.takeIf { number -> number > 0 }, trackManuallyEdited = true)) },
                    label = { Text(strings.track) }, modifier = Modifier.weight(0.45f),
                )
            }
        }
    }
}
