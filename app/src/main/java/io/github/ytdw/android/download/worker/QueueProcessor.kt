package io.github.ytdw.android.download.worker

import android.content.Context
import android.net.Uri
import io.github.ytdw.android.appContainer
import io.github.ytdw.android.domain.model.AnalyzedMedia
import io.github.ytdw.android.domain.model.AppSettings
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.domain.model.DownloadMode
import io.github.ytdw.android.domain.model.DownloadStatus
import io.github.ytdw.android.domain.service.ErrorCategory
import io.github.ytdw.android.domain.service.ErrorMapper
import io.github.ytdw.android.domain.service.MetadataCleaner
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class QueueProcessor(private val context: Context) {
    private val container = context.appContainer

    suspend fun run(onProgress: suspend (DownloadItem?, String, Int?) -> Unit) {
        container.awaitReady()
        val parallelism = container.settingsRepository.load().parallelDownloads.coerceIn(1, 3)
        val progressLock = Mutex()
        var lastNotificationAt = 0L
        suspend fun report(item: DownloadItem?, text: String, progress: Int?) {
            progressLock.withLock {
                val now = android.os.SystemClock.elapsedRealtime()
                val intermediateProgress = progress != null && progress in 1..99
                if (intermediateProgress && now - lastNotificationAt < NOTIFICATION_INTERVAL_MS) return@withLock
                onProgress(item, text, progress)
                lastNotificationAt = now
            }
        }
        coroutineScope {
            val workers = List(parallelism) {
                launch {
                    while (currentCoroutineContext().isActive && !container.settingsRepository.isQueuePaused()) {
                        val settings = container.settingsRepository.load()
                        val item = container.queueRepository.claimNext(settings.skipDownloadArchive) ?: break
                        processItem(item, settings, ::report)
                    }
                }
            }
            workers.joinAll()
        }
        report(null, "Queue stopped", null)
    }

    private suspend fun processItem(
        item: DownloadItem,
        settings: AppSettings,
        report: suspend (DownloadItem?, String, Int?) -> Unit,
    ) {
        report(item, "Downloading", 0)
        var publishedUri: Uri? = null
        try {
            var lastPersistedProgress = 0L
            var lastPersistedPhase: String? = null
            val downloaded = container.downloadEngine.download(item, settings) { progress ->
                val now = android.os.SystemClock.elapsedRealtime()
                val phaseChanged = progress.phase != lastPersistedPhase
                if (phaseChanged || now - lastPersistedProgress >= 500 || progress.percentage >= 100.0) {
                    container.queueRepository.progress(item.id, progress)
                    lastPersistedProgress = now
                    lastPersistedPhase = progress.phase
                }
                report(item, progress.phase, progress.percentage.toInt())
            }
            if (!container.queueRepository.processing(item.id, "Processing")) {
                if (container.queueRepository.get(item.id)?.status == DownloadStatus.CANCELLED) {
                    deleteTemporaryFiles(item.id)
                }
                return
            }
            var current = container.queueRepository.get(item.id) ?: run {
                deleteTemporaryFiles(item.id)
                return
            }
            downloaded.metadata?.let { metadata ->
                current = container.queueRepository.save(enrichMetadata(current, metadata, settings))
            }
            report(current, "Processing", null)
            var cover: File? = null
            if (current.downloadMode == DownloadMode.AUDIO &&
                (settings.metadata.embedThumbnailAsCover || settings.metadata.saveCoverAsSeparateJpeg)
            ) {
                current.thumbnailUrl?.let {
                    runCatching {
                        val cached = current.cachedThumbnailPath?.let(::File)?.takeIf(File::isFile)
                        if (cached != null) {
                            container.thumbnailProcessor.process(cached, current.id, settings.metadata.cropCoverToSquare)
                        } else {
                            container.thumbnailProcessor.download(it, current.id, settings.metadata.cropCoverToSquare)
                        }
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
                    return
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
                return
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
                return
            }
            publishedUri = null
            if (settings.metadata.saveCoverAsSeparateJpeg && cover != null) {
                runCatching { container.mediaStorePublisher.publishCover(cover, title, current.videoId) }
                    .onFailure {
                        container.errorLogger.log("cover", it.message ?: "Cover publish failed", it.stackTraceToString())
                    }
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
                container.queueRepository.fail(
                    item.id,
                    mapped.category.name.lowercase(),
                    mapped.userMessage,
                    mapped.technical,
                )
                container.errorLogger.log(mapped.category.name, mapped.userMessage, mapped.technical)
            }
        }
    }

    private fun deleteTemporaryFiles(itemId: String) {
        context.cacheDir.resolve("downloads/$itemId").deleteRecursively()
    }

    private fun enrichMetadata(item: DownloadItem, media: AnalyzedMedia, settings: AppSettings): DownloadItem {
        val channel = media.channel.ifBlank { media.uploader }
        val title = media.title.takeUnless { it.isBlank() || it == "Untitled" } ?: item.originalTitle
        val cleanedTitle = if (item.titleManuallyEdited) {
            item.cleanedTitle
        } else {
            MetadataCleaner.cleanTrackTitle(
                title,
                channel.takeIf { settings.metadata.removeChannelFromTitle }.orEmpty(),
                settings.metadata.removeLabels,
            )
        }
        return item.copy(
            sourceUrl = media.sourceUrl,
            videoId = media.videoId ?: item.videoId,
            originalTitle = title,
            cleanedTitle = cleanedTitle,
            channel = media.channel.ifBlank { item.channel },
            uploader = media.uploader.ifBlank { item.uploader },
            artist = if (!item.artistManuallyEdited && item.artist.isBlank() && settings.metadata.useChannelAsArtist) {
                channel
            } else item.artist,
            albumArtist = if (item.albumArtist.isBlank() && settings.metadata.useChannelAsAlbumArtist) channel else item.albumArtist,
            album = if (!item.albumManuallyEdited && item.album.isBlank() && settings.metadata.usePlaylistTitleAsAlbum) {
                media.playlistTitle.orEmpty()
            } else item.album,
            trackNumber = if (!item.trackManuallyEdited && item.trackNumber == null && settings.metadata.usePlaylistIndexAsTrack) {
                media.playlistIndex
            } else item.trackNumber,
            uploadDate = media.uploadDate ?: item.uploadDate,
            duration = media.duration ?: item.duration,
            thumbnailUrl = media.thumbnailUrl ?: item.thumbnailUrl,
        )
    }

    private companion object {
        const val NOTIFICATION_INTERVAL_MS = 250L
    }
}
