package io.github.ytdw.android.util

import io.github.ytdw.android.data.database.AppDao
import io.github.ytdw.android.data.database.ErrorLogEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ErrorLogger(private val dao: AppDao, private val directory: File) {
    suspend fun log(category: String, message: String, technical: String?) = withContext(Dispatchers.IO) {
        dao.insertError(ErrorLogEntity(category = category, message = message, technical = technical))
        dao.trimErrors()
        directory.mkdirs()
        val file = File(directory, "yt-dw.log")
        if (file.length() > 1_000_000) {
            val previous = File(directory, "yt-dw.log.1")
            if (previous.exists()) previous.delete()
            file.renameTo(previous)
        }
        file.appendText("${java.time.Instant.now()} [$category] $message\n${technical.orEmpty()}\n")
    }
}
