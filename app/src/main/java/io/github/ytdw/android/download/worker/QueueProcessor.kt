package io.github.ytdw.android.download.worker

import android.content.Context
import android.net.Uri
import io.github.ytdw.android.appContainer
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.domain.model.DownloadMode
import io.github.ytdw.android.domain.model.DownloadStatus
import io.github.ytdw.android.domain.service.ErrorCategory
import io.github.ytdw.android.domain.service.ErrorMapper
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class QueueProcessor(private val context: Context) {
    private val container = context.appContainer

    suspend fun run(onProgress: suspend (DownloadItem?, String, Int?) -> Unit) {
        while (currentCoroutineContext().isActive && !container.settingsRepository.isQueuePaused()) {
            val settings = container.settingsRepository.load()
            val item = container.queueRepository.claimNext(settings.skipDownloadArchive) ?: break
            onProgress(item, "Downloading", 0)
            var publishedUri: Uri? = null
            try {
                var lastPersistedProgress = 0L
                val downloaded = container.downloadEngine.download(item, settings) { progress ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastPersistedProgress >= 500 || progress.percentage >= 100.0) {
                        container.queueRepository.progress(item.id, progress)
                        lastPersistedProgress = now
                    }
                    onProgress(item, progress.phase, progress.percentage.toInt())
                }
                if (!container.queueRepository.processing(item.id, "Processing")) {
                    if (container.queueRepository.get(item.id)?.status == DownloadStatus.CANCELLED) {
                        deleteTemporaryFiles(item.id)
                    }
                    continue
                }
                val current = container.queueRepository.get(item.id) ?: continue
                onProgress(current, "Processing", null)
                var cover: File? = null
                if (current.downloadMode == DownloadMode.AUDIO &&
                    (settings.metadata.embedThumbnailAsCover || settings.metadata.saveCoverAsSeparateJpeg)
                ) {
                    current.thumbnailUrl?.let {
                        runCatching {
                            container.thumbnailProcessor.download(
                                it, current.id, settings.metadata.cropCoverToSquare,
                            )
                        }.onSuccess { downloadedCover ->
                            cover = downloadedCover
                        }.onFailure { error ->
                            container.errorLogger.log(
                                "cover",
                                error.message ?: "Cover download failed; continuing without a cover",
                                error.stackTraceToString(),
                            )
                        }
                    }
                }
                val source = File(downloaded.mediaPath)
                val finalFile = if (current.downloadMode == DownloadMode.AUDIO) {
                    if (!container.queueRepository.processing(item.id, "Writing metadata")) {
                        deleteTemporaryFiles(item.id)
                        continue
                    }
                    container.metadataService.writeAndVerify(
                        source, current,
                        cover.takeIf { settings.metadata.embedThumbnailAsCover },
                        settings.metadata.storeOriginalUrlInComment,
                        settings.metadata.storeUploadYear,
                    )
                } else source
                if (container.queueRepository.get(item.id)?.status != DownloadStatus.PROCESSING) {
                    deleteTemporaryFiles(item.id)
                    continue
                }
                val title = (if (settings.metadata.useCleanedTitleAsFilename) {
                    current.cleanedTitle
                } else {
                    current.originalTitle
                }).ifBlank { current.originalTitle }.ifBlank { "Untitled" }
                val volumeName = if (current.downloadMode == DownloadMode.AUDIO) {
                    settings.audioVolumeName
                } else {
                    settings.videoVolumeName
                }
                val relativePath = if (current.downloadMode == DownloadMode.AUDIO) {
                    settings.audioRelativePath
                } else {
                    settings.videoRelativePath
                }
                val published = container.mediaStorePublisher.publish(
                    finalFile,
                    title,
                    current.downloadMode,
                    current.videoId,
                    volumeName,
                    relativePath,
                )
                publishedUri = published.uri
                val completed = container.queueRepository.complete(
                    item.id,
                    published.uri.toString(),
                    published.displayName,
                )
                if (completed?.status != DownloadStatus.COMPLETED) {
                    container.mediaStorePublisher.delete(published.uri)
                    publishedUri = null
                    deleteTemporaryFiles(item.id)
                    continue
                }
                publishedUri = null
                if (settings.metadata.saveCoverAsSeparateJpeg && cover != null) {
                    runCatching { container.mediaStorePublisher.publishCover(cover, title, current.videoId) }
                        .onFailure { container.errorLogger.log("cover", it.message ?: "Cover publish failed", it.stackTraceToString()) }
                }
                deleteTemporaryFiles(item.id)
            } catch (cancelled: CancellationException) {
                container.downloadEngine.cancel(item.id)
                withContext(NonCancellable) {
                    if (container.queueRepository.get(item.id)?.status == DownloadStatus.CANCELLED) {
                        deleteTemporaryFiles(item.id)
                    } else {
                        container.queueRepository.release(item.id)
                    }
                }
                throw cancelled
            } catch (error: Throwable) {
                publishedUri?.let { uri -> runCatching { container.mediaStorePublisher.delete(uri) } }
                val mapped = ErrorMapper.map(error)
                if (mapped.category == ErrorCategory.CANCELLED ||
                    container.queueRepository.get(item.id)?.status == DownloadStatus.CANCELLED
                ) {
                    container.queueRepository.cancel(item.id)
                    deleteTemporaryFiles(item.id)
                } else {
                    container.queueRepository.fail(item.id, mapped.category.name.lowercase(), mapped.userMessage, mapped.technical)
                    container.errorLogger.log(mapped.category.name, mapped.userMessage, mapped.technical)
                }
            }
        }
        onProgress(null, "Queue stopped", null)
    }

    private fun deleteTemporaryFiles(itemId: String) {
        context.cacheDir.resolve("downloads/$itemId").deleteRecursively()
    }
}
