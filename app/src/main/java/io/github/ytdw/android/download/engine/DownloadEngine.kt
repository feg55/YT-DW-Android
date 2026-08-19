package io.github.ytdw.android.download.engine

import io.github.ytdw.android.domain.model.AnalyzedMedia
import io.github.ytdw.android.domain.model.AppSettings
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.domain.model.DownloadProgress
import kotlinx.coroutines.flow.Flow

data class DownloadedFile(val mediaPath: String, val coverPath: String? = null)

interface DownloadEngine {
    fun analyze(urls: List<String>): Flow<Result<AnalyzedMedia>>
    suspend fun download(item: DownloadItem, settings: AppSettings, onProgress: suspend (DownloadProgress) -> Unit): DownloadedFile
    fun cancel(itemId: String)
}
