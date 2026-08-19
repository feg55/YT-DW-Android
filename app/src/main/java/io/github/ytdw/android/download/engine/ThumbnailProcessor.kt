package io.github.ytdw.android.download.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThumbnailProcessor(private val cacheDir: File) {
    suspend fun download(url: String, key: String, cropSquare: Boolean, maxBytes: Int = 25 * 1024 * 1024): File =
        withContext(Dispatchers.IO) {
            val uri = URI(url)
            require(uri.scheme in setOf("http", "https") && uri.host != null) { "Thumbnail URL must use HTTP(S)" }
            val raw = File(cacheDir, "covers/$key.raw")
            val result = File(cacheDir, "covers/$key.jpg")
            check(raw.parentFile?.mkdirs() == true || raw.parentFile?.isDirectory == true)
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "image/*")
            connection.setRequestProperty("User-Agent", "YT-DW Android/0.1")
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(raw).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= maxBytes) { "Thumbnail exceeds the download-size limit" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(raw.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported thumbnail image" }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2800) sample *= 2
                val decoded = BitmapFactory.decodeFile(
                    raw.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: error("Unsupported thumbnail image")
                val rotation = when (ExifInterface(raw).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                val source = if (rotation != 0f) Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true,
                ) else decoded
                val cropped = if (cropSquare) {
                    val side = minOf(source.width, source.height)
                    Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
                } else source
                val boundedSide = maxOf(cropped.width, cropped.height).coerceIn(600, 1400)
                val scale = boundedSide.toDouble() / maxOf(cropped.width, cropped.height)
                val target = if (scale != 1.0) cropped.scale(
                    (cropped.width * scale).toInt(),
                    (cropped.height * scale).toInt(),
                ) else cropped
                FileOutputStream(result).use { check(target.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                listOf(target, cropped, source, decoded).distinctBy(System::identityHashCode).forEach(Bitmap::recycle)
                result
            } finally {
                connection.disconnect()
                raw.delete()
            }
        }
}
