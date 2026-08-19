package io.github.ytdw.android.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import io.github.ytdw.android.domain.model.DownloadMode
import io.github.ytdw.android.domain.service.FilenameService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PublishedMedia(val uri: Uri, val displayName: String)

class MediaStorePublisher(private val context: Context) {
    suspend fun publish(
        media: File,
        title: String,
        mode: DownloadMode,
        preferredSuffix: String?,
        volumeName: String,
        relativePath: String,
    ): PublishedMedia =
        withContext(Dispatchers.IO) {
            val extension = if (mode == DownloadMode.AUDIO) ".m4a" else ".mp4"
            require(volumeName in MediaStore.getExternalVolumeNames(context)) {
                "The selected storage volume is not available"
            }
            val collection = if (mode == DownloadMode.AUDIO) {
                MediaStore.Audio.Media.getContentUri(volumeName)
            } else {
                MediaStore.Video.Media.getContentUri(volumeName)
            }
            val proposed = FilenameService.sanitize(title) + extension
            val name = FilenameService.uniqueName(proposed, { exists(collection, relativePath, it) }, preferredSuffix)
            publishToCollection(media, collection, name, relativePath, if (mode == DownloadMode.AUDIO) "audio/mp4" else "video/mp4")
        }

    suspend fun publishCover(cover: File, mediaTitle: String, preferredSuffix: String?): PublishedMedia =
        withContext(Dispatchers.IO) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val path = "Pictures/YT-DW"
            val proposed = FilenameService.sanitize(mediaTitle) + ".jpg"
            val name = FilenameService.uniqueName(proposed, { exists(collection, path, it) }, preferredSuffix)
            publishToCollection(cover, collection, name, path, "image/jpeg")
        }

    fun open(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.delete(uri, null, null) > 0
    }

    private fun exists(collection: Uri, path: String, name: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val normalizedPath = path.trim('/')
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
            "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?)"
        context.contentResolver.query(
            collection,
            projection,
            selection,
            arrayOf(name, normalizedPath, "$normalizedPath/"),
            null,
        )?.use {
            return it.moveToFirst()
        }
        return false
    }

    private fun publishToCollection(file: File, collection: Uri, name: String, path: String, mime: String): PublishedMedia {
        val resolver: ContentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore rejected the output file")
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("MediaStore output stream is unavailable")
            check(
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                ) == 1,
            ) { "MediaStore could not finalize the output file" }
            return PublishedMedia(uri, name)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
