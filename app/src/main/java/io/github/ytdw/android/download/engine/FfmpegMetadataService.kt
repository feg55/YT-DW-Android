package io.github.ytdw.android.download.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FfmpegMetadataService(private val context: Context) {
    suspend fun writeAndVerify(
        input: File,
        item: io.github.ytdw.android.domain.model.DownloadItem,
        cover: File?,
        storeSourceUrl: Boolean,
        storeUploadYear: Boolean,
    ): File =
        withContext(Dispatchers.IO) {
            val title = item.cleanedTitle.ifBlank { item.originalTitle }.ifBlank { "Untitled" }
            val artist = item.artist.trim().takeIf(String::isNotEmpty)
            val albumArtist = item.albumArtist.trim().takeIf(String::isNotEmpty) ?: artist
            val output = File(input.parentFile, "tagged.m4a")
            val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
            check(ffmpeg.isFile) { "Bundled Android FFmpeg executable is unavailable" }
            val command = mutableListOf(ffmpeg.absolutePath, "-y", "-i", input.absolutePath)
            if (cover != null) command += listOf("-i", cover.absolutePath)
            command += listOf("-map", "0:a:0")
            if (cover != null) command += listOf("-map", "1:v:0", "-c:v", "mjpeg", "-disposition:v:0", "attached_pic")
            command += listOf("-c:a", "copy", "-metadata", "title=$title")
            artist?.let { command += listOf("-metadata", "artist=$it") }
            albumArtist?.let { command += listOf("-metadata", "album_artist=$it") }
            item.album.takeIf(String::isNotBlank)?.let { command += listOf("-metadata", "album=$it") }
            item.trackNumber?.let {
                val track = item.playlistCount?.let { total -> "$it/$total" } ?: it.toString()
                command += listOf("-metadata", "track=$track")
            }
            if (storeUploadYear) item.uploadDate?.take(4)?.takeIf { it.all(Char::isDigit) }
                ?.let { command += listOf("-metadata", "date=$it") }
            if (storeSourceUrl) command += listOf("-metadata", "comment=${item.sourceUrl}")
            command += output.absolutePath
            val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
            val nativeDir = context.applicationInfo.nativeLibraryDir
            val packagesDir = File(context.noBackupFilesDir, "youtubedl-android/packages")
            val ffmpegLib = packagesDir.resolve("ffmpeg/usr/lib")
            val pythonLib = packagesDir.resolve("python/usr/lib")
            processBuilder.environment()["LD_LIBRARY_PATH"] =
                listOf(nativeDir, ffmpegLib.absolutePath, pythonLib.absolutePath).joinToString(":")
            val process = processBuilder.start()
            val log = process.inputStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0 && output.isFile) { "FFmpeg metadata writing failed: $log" }
            verify(output, title, artist, cover != null)
            output
        }

    private fun verify(file: File, title: String, artist: String?, requireCover: Boolean) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) == title) {
                "M4A title verification failed"
            }
            if (artist != null) {
                check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) == artist) {
                    "M4A artist verification failed"
                }
            }
            if (requireCover) check(retriever.embeddedPicture?.isNotEmpty() == true) { "M4A cover verification failed" }
        } finally {
            retriever.release()
        }
    }
}
