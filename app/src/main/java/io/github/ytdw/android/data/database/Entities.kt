package io.github.ytdw.android.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "queue_items", indices = [Index(value = ["videoId"], unique = true)])
data class QueueItemEntity(
    @PrimaryKey val id: String,
    val position: Long,
    val sourceUrl: String,
    val videoId: String?,
    val playlistId: String?,
    val playlistTitle: String?,
    val playlistIndex: Int?,
    val playlistCount: Int?,
    val originalTitle: String,
    val cleanedTitle: String,
    val channel: String,
    val uploader: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val trackNumber: Int?,
    val uploadDate: String?,
    val duration: Double?,
    val thumbnailUrl: String?,
    val cachedThumbnailPath: String?,
    val contentUri: String?,
    val displayName: String?,
    val downloadMode: String,
    val videoQuality: String,
    val status: String,
    val progressPercentage: Double,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speed: Double?,
    val etaSeconds: Long?,
    val retryCount: Int,
    val errorCategory: String?,
    val errorMessage: String?,
    val technicalError: String?,
    val currentPhase: String,
    val createdAt: Long,
    val updatedAt: Long,
    val selected: Boolean,
    val titleManuallyEdited: Boolean,
    val artistManuallyEdited: Boolean,
    val albumManuallyEdited: Boolean,
    val trackManuallyEdited: Boolean,
)

@Entity(tableName = "download_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val queueItemId: String,
    val sourceUrl: String,
    val videoId: String?,
    val title: String,
    val downloadMode: String,
    val status: String,
    val contentUri: String?,
    val displayName: String?,
    val errorCategory: String?,
    val errorMessage: String?,
    val technicalError: String?,
    val createdAt: Long,
    val finishedAt: Long,
)

@Entity(tableName = "download_archive")
data class ArchiveEntity(
    @PrimaryKey val archiveKey: String,
    val videoId: String?,
    val sourceUrl: String,
    val contentUri: String?,
    val createdAt: Long,
)

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val theme: String = "DARK",
    val language: String = "SYSTEM",
    val rememberLastTab: Boolean = true,
    val lastTab: Int = 0,
    val useChannelAsArtist: Boolean = true,
    val removeChannelFromTitle: Boolean = true,
    val removeLabels: Boolean = true,
    val usePlaylistTitleAsAlbum: Boolean = false,
    val usePlaylistIndexAsTrack: Boolean = true,
    val useCleanedTitleAsFilename: Boolean = true,
    val embedThumbnailAsCover: Boolean = true,
    val cropCoverToSquare: Boolean = true,
    val saveCoverAsSeparateJpeg: Boolean = false,
    val storeOriginalUrlInComment: Boolean = true,
    val storeUploadYear: Boolean = true,
    val useChannelAsAlbumArtist: Boolean = true,
    val skipDownloadArchive: Boolean = true,
    val retryCount: Int = 5,
    val fragmentRetryCount: Int = 5,
    val continuePartialDownloads: Boolean = true,
    val socketTimeoutSeconds: Int = 30,
    @ColumnInfo(defaultValue = "'external_primary'")
    val audioVolumeName: String = "external_primary",
    @ColumnInfo(defaultValue = "'Music/YT-DW'")
    val audioRelativePath: String = "Music/YT-DW",
    @ColumnInfo(defaultValue = "'external_primary'")
    val videoVolumeName: String = "external_primary",
    @ColumnInfo(defaultValue = "'Movies/YT-DW'")
    val videoRelativePath: String = "Movies/YT-DW",
    val queuePaused: Boolean = true,
)

@Entity(tableName = "error_log")
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val message: String,
    val technical: String?,
    val createdAt: Long = System.currentTimeMillis(),
)
