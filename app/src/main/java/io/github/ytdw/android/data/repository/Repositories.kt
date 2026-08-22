package io.github.ytdw.android.data.repository

import androidx.room.withTransaction
import io.github.ytdw.android.data.database.AppDao
import io.github.ytdw.android.data.database.AppDatabase
import io.github.ytdw.android.data.database.ArchiveEntity
import io.github.ytdw.android.data.database.HistoryEntity
import io.github.ytdw.android.data.database.QueueItemEntity
import io.github.ytdw.android.data.database.SettingsEntity
import io.github.ytdw.android.domain.model.AppSettings
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.domain.model.DownloadProgress
import io.github.ytdw.android.domain.model.DownloadStatus
import io.github.ytdw.android.domain.model.LanguagePreference
import io.github.ytdw.android.domain.model.MetadataSettings
import io.github.ytdw.android.domain.model.ThemePreference
import io.github.ytdw.android.domain.model.VideoQuality
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QueueRepository(private val database: AppDatabase) {
    private val dao: AppDao = database.dao()
    val queue: Flow<List<DownloadItem>> = dao.observeQueue().map { list -> list.map(QueueItemEntity::toDomain) }
    val history: Flow<List<HistoryEntity>> = dao.observeHistory()

    suspend fun add(item: DownloadItem, skipArchived: Boolean = true): DownloadItem = database.withTransaction {
        item.videoId?.let { dao.getByVideoId(it)?.toDomain()?.let { found -> return@withTransaction found } }
        val stored = if (skipArchived && isArchived(item)) {
            item.copy(status = DownloadStatus.SKIPPED, currentPhase = "Already downloaded")
        } else item
        dao.insertQueueItem(stored.toEntity(dao.nextPosition()))
        if (stored.status == DownloadStatus.SKIPPED) recordHistory(stored)
        stored
    }

    suspend fun addAll(items: List<DownloadItem>, skipArchived: Boolean = true): List<DownloadItem> =
        database.withTransaction {
            items.map { add(it, skipArchived) }
        }

    suspend fun get(id: String): DownloadItem? = dao.getQueueItem(id)?.toDomain()

    suspend fun save(item: DownloadItem): DownloadItem = database.withTransaction {
        val current = dao.getQueueItem(item.id)
        if (current != null && enumValueOf<DownloadStatus>(current.status).isTerminal && item.status.isActive) {
            return@withTransaction current.toDomain()
        }
        val entity = item.copy(
            retryCount = maxOf(item.retryCount, current?.retryCount ?: 0),
            updatedAt = System.currentTimeMillis(),
        ).toEntity(current?.position ?: dao.nextPosition())
        if (current == null) dao.insertQueueItem(entity) else dao.updateQueueItem(entity)
        entity.toDomain()
    }

    suspend fun claimNext(skipArchived: Boolean): DownloadItem? = database.withTransaction {
        var claimed: DownloadItem? = null
        while (claimed == null) {
            val candidate = dao.nextReady() ?: break
            if (skipArchived && isArchived(candidate.toDomain())) {
                val skipped = candidate.toDomain().copy(
                    status = DownloadStatus.SKIPPED,
                    currentPhase = "Already downloaded",
                    errorMessage = "Already downloaded",
                    updatedAt = System.currentTimeMillis(),
                )
                dao.updateQueueItem(skipped.toEntity(candidate.position))
                recordHistory(skipped)
                continue
            }
            val changed = dao.compareAndSetStatus(
                candidate.id, DownloadStatus.READY.name, DownloadStatus.DOWNLOADING.name,
                "Downloading", System.currentTimeMillis(),
            )
            if (changed == 1) claimed = dao.getQueueItem(candidate.id)?.toDomain()
        }
        claimed
    }

    suspend fun progress(id: String, progress: DownloadProgress) {
        dao.updateProgress(
            id = id,
            percentage = progress.percentage,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            speed = progress.speedBytesPerSecond,
            etaSeconds = progress.etaSeconds,
            phase = progress.phase,
            now = System.currentTimeMillis(),
        )
    }

    suspend fun updateSelection(id: String, selected: Boolean): Boolean =
        dao.updateSelection(id, selected, System.currentTimeMillis()) == 1

    suspend fun updateMetadata(item: DownloadItem): Boolean = dao.updateMetadata(
        id = item.id,
        cleanedTitle = item.cleanedTitle,
        artist = item.artist,
        albumArtist = item.albumArtist,
        album = item.album,
        trackNumber = item.trackNumber,
        titleManuallyEdited = item.titleManuallyEdited,
        artistManuallyEdited = item.artistManuallyEdited,
        albumManuallyEdited = item.albumManuallyEdited,
        trackManuallyEdited = item.trackManuallyEdited,
        now = System.currentTimeMillis(),
    ) == 1

    suspend fun processing(id: String, phase: String): Boolean =
        dao.markProcessing(id, phase, System.currentTimeMillis()) == 1

    suspend fun complete(id: String, contentUri: String, displayName: String): DownloadItem? = database.withTransaction {
        val current = get(id) ?: return@withTransaction null
        if (current.status.isTerminal) return@withTransaction current
        val completed = current.copy(
            status = DownloadStatus.COMPLETED, progressPercentage = 100.0, currentPhase = "Completed",
            speed = null, etaSeconds = null, contentUri = contentUri, displayName = displayName,
            errorCategory = null, errorMessage = null, technicalError = null,
        )
        val saved = save(completed)
        recordHistory(saved)
        dao.upsertArchive(ArchiveEntity(archiveKey(saved), saved.videoId, saved.sourceUrl, contentUri, saved.updatedAt))
        saved
    }

    suspend fun fail(id: String, category: String, message: String, technical: String): DownloadItem? = database.withTransaction {
        val current = get(id) ?: return@withTransaction null
        if (current.status.isTerminal) return@withTransaction current
        val failed = save(current.copy(
            status = DownloadStatus.FAILED, retryCount = current.retryCount + 1, currentPhase = "Failed",
            speed = null, etaSeconds = null, errorCategory = category, errorMessage = message,
            technicalError = technical,
        ))
        recordHistory(failed)
        failed
    }

    suspend fun cancel(id: String): DownloadItem? {
        val current = get(id) ?: return null
        if (current.status.isTerminal) return current
        dao.cancelActive(id, System.currentTimeMillis())
        return get(id)
    }

    suspend fun release(id: String): Boolean =
        dao.releaseActive(id, System.currentTimeMillis()) == 1

    suspend fun retry(id: String): DownloadItem? {
        val current = get(id) ?: return null
        if (current.status !in setOf(DownloadStatus.FAILED, DownloadStatus.CANCELLED)) return current
        val next = if (current.videoId != null || current.originalTitle.isNotEmpty()) DownloadStatus.READY else DownloadStatus.PENDING
        return save(current.copy(status = next, currentPhase = "", speed = null, etaSeconds = null))
    }

    suspend fun retryFailed(): Int {
        val failed = dao.listQueue().filter { it.status == DownloadStatus.FAILED.name }
        failed.forEach { retry(it.id) }
        return failed.size
    }

    suspend fun removeCompleted(): Int = dao.deleteCompleted()
    suspend fun clearAll(): Triple<Int, Int, Int> = database.withTransaction {
        check(dao.listQueue().none { enumValueOf<DownloadStatus>(it.status).isActive }) {
            "Cannot clear the queue while a download is active"
        }
        dao.clearDownloadState()
    }

    suspend fun restoreUnfinished() = database.withTransaction {
        val now = System.currentTimeMillis()
        dao.restoreAnalysis(now)
        dao.restoreDownloads(now)
    }

    private suspend fun recordHistory(item: DownloadItem) {
        dao.insertHistory(HistoryEntity(
            queueItemId = item.id, sourceUrl = item.sourceUrl, videoId = item.videoId,
            title = item.cleanedTitle.ifEmpty { item.originalTitle }, downloadMode = item.downloadMode.name,
            status = item.status.name, contentUri = item.contentUri, displayName = item.displayName,
            errorCategory = item.errorCategory, errorMessage = item.errorMessage,
            technicalError = item.technicalError, createdAt = item.createdAt, finishedAt = item.updatedAt,
        ))
    }

    private suspend fun isArchived(item: DownloadItem): Boolean = dao.archiveContains(archiveKey(item))

    private fun archiveKey(item: DownloadItem): String = item.videoId?.let { "video:$it" }
        ?: "url:" + MessageDigest.getInstance("SHA-256").digest(item.sourceUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

class SettingsRepository(private val dao: AppDao) {
    val settings: Flow<AppSettings> = dao.observeSettings().map { it?.toDomain() ?: AppSettings() }
    suspend fun load(): AppSettings = dao.getSettings()?.toDomain() ?: AppSettings()
    suspend fun save(settings: AppSettings, queuePaused: Boolean? = null) {
        val paused = queuePaused ?: dao.getSettings()?.queuePaused ?: true
        dao.saveSettings(settings.toEntity(paused))
    }
    suspend fun isQueuePaused(): Boolean = dao.getSettings()?.queuePaused ?: true
    suspend fun setQueuePaused(paused: Boolean) = dao.saveSettings((dao.getSettings() ?: SettingsEntity()).copy(queuePaused = paused))
}

private fun DownloadItem.toEntity(position: Long) = QueueItemEntity(
    id, position, sourceUrl, videoId, playlistId, playlistTitle, playlistIndex, playlistCount,
    originalTitle, cleanedTitle, channel, uploader, artist, albumArtist, album, trackNumber,
    uploadDate, duration, thumbnailUrl, cachedThumbnailPath, contentUri, displayName,
    downloadMode.name, videoQuality.name, status.name, progressPercentage, downloadedBytes,
    totalBytes, speed, etaSeconds, retryCount, errorCategory, errorMessage, technicalError,
    currentPhase, createdAt, updatedAt, selected, titleManuallyEdited, artistManuallyEdited,
    albumManuallyEdited, trackManuallyEdited,
)

private fun QueueItemEntity.toDomain() = DownloadItem(
    id, sourceUrl, videoId, playlistId, playlistTitle, playlistIndex, playlistCount, originalTitle,
    cleanedTitle, channel, uploader, artist, albumArtist, album, trackNumber, uploadDate, duration,
    thumbnailUrl, cachedThumbnailPath, contentUri, displayName,
    enumValueOf(downloadMode), runCatching { enumValueOf<VideoQuality>(videoQuality) }.getOrDefault(VideoQuality.BEST),
    enumValueOf(status), progressPercentage, downloadedBytes, totalBytes, speed, etaSeconds,
    retryCount, errorCategory, errorMessage, technicalError, currentPhase, createdAt, updatedAt,
    selected, titleManuallyEdited, artistManuallyEdited, albumManuallyEdited, trackManuallyEdited,
)

private fun SettingsEntity.toDomain() = AppSettings(
    theme = runCatching { enumValueOf<ThemePreference>(theme) }.getOrDefault(ThemePreference.DARK),
    language = runCatching { enumValueOf<LanguagePreference>(language) }.getOrDefault(LanguagePreference.SYSTEM),
    rememberLastTab = rememberLastTab, lastTab = lastTab,
    metadata = MetadataSettings(
        useChannelAsArtist, removeChannelFromTitle, removeLabels, usePlaylistTitleAsAlbum,
        usePlaylistIndexAsTrack, useCleanedTitleAsFilename, embedThumbnailAsCover,
        cropCoverToSquare, saveCoverAsSeparateJpeg, storeOriginalUrlInComment,
        storeUploadYear, useChannelAsAlbumArtist,
    ),
    skipDownloadArchive = skipDownloadArchive, retryCount = retryCount,
    fragmentRetryCount = fragmentRetryCount, continuePartialDownloads = continuePartialDownloads,
    socketTimeoutSeconds = socketTimeoutSeconds,
    parallelDownloads = parallelDownloads.coerceIn(1, 3),
    concurrentFragmentDownloads = concurrentFragmentDownloads.coerceIn(1, 8),
    audioVolumeName = audioVolumeName, audioRelativePath = audioRelativePath,
    videoVolumeName = videoVolumeName, videoRelativePath = videoRelativePath,
)

private fun AppSettings.toEntity(paused: Boolean) = SettingsEntity(
    theme = theme.name, language = language.name, rememberLastTab = rememberLastTab,
    lastTab = lastTab.coerceIn(0, 2), useChannelAsArtist = metadata.useChannelAsArtist,
    removeChannelFromTitle = metadata.removeChannelFromTitle, removeLabels = metadata.removeLabels,
    usePlaylistTitleAsAlbum = metadata.usePlaylistTitleAsAlbum,
    usePlaylistIndexAsTrack = metadata.usePlaylistIndexAsTrack,
    useCleanedTitleAsFilename = metadata.useCleanedTitleAsFilename,
    embedThumbnailAsCover = metadata.embedThumbnailAsCover,
    cropCoverToSquare = metadata.cropCoverToSquare,
    saveCoverAsSeparateJpeg = metadata.saveCoverAsSeparateJpeg,
    storeOriginalUrlInComment = metadata.storeOriginalUrlInComment,
    storeUploadYear = metadata.storeUploadYear,
    useChannelAsAlbumArtist = metadata.useChannelAsAlbumArtist,
    skipDownloadArchive = skipDownloadArchive, retryCount = retryCount.coerceAtLeast(0),
    fragmentRetryCount = fragmentRetryCount.coerceAtLeast(0),
    continuePartialDownloads = continuePartialDownloads,
    socketTimeoutSeconds = socketTimeoutSeconds.coerceAtLeast(1),
    parallelDownloads = parallelDownloads.coerceIn(1, 3),
    concurrentFragmentDownloads = concurrentFragmentDownloads.coerceIn(1, 8),
    audioVolumeName = audioVolumeName, audioRelativePath = audioRelativePath,
    videoVolumeName = videoVolumeName, videoRelativePath = videoRelativePath,
    queuePaused = paused,
)
