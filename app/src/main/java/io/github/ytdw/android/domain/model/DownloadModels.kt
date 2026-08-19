package io.github.ytdw.android.domain.model

import java.util.UUID

enum class DownloadStatus {
    PENDING, ANALYZING, READY, DOWNLOADING, PROCESSING, COMPLETED, SKIPPED, CANCELLED, FAILED;

    val isTerminal: Boolean
        get() = this in setOf(COMPLETED, SKIPPED, CANCELLED, FAILED)

    val isActive: Boolean
        get() = this in setOf(ANALYZING, DOWNLOADING, PROCESSING)
}

enum class DownloadMode { AUDIO, VIDEO }

enum class VideoQuality(val value: String) {
    BEST("best"), UHD_2160("2160p"), QHD_1440("1440p"), FULL_HD_1080("1080p"),
    HD_720("720p"), SD_480("480p")
}

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val sourceUrl: String,
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val playlistIndex: Int? = null,
    val playlistCount: Int? = null,
    val originalTitle: String = "",
    val cleanedTitle: String = "",
    val channel: String = "",
    val uploader: String = "",
    val artist: String = "",
    val albumArtist: String = "",
    val album: String = "",
    val trackNumber: Int? = null,
    val uploadDate: String? = null,
    val duration: Double? = null,
    val thumbnailUrl: String? = null,
    val cachedThumbnailPath: String? = null,
    val contentUri: String? = null,
    val displayName: String? = null,
    val downloadMode: DownloadMode = DownloadMode.AUDIO,
    val videoQuality: VideoQuality = VideoQuality.BEST,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progressPercentage: Double = 0.0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val speed: Double? = null,
    val etaSeconds: Long? = null,
    val retryCount: Int = 0,
    val errorCategory: String? = null,
    val errorMessage: String? = null,
    val technicalError: String? = null,
    val currentPhase: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val selected: Boolean = true,
    val titleManuallyEdited: Boolean = false,
    val artistManuallyEdited: Boolean = false,
    val albumManuallyEdited: Boolean = false,
    val trackManuallyEdited: Boolean = false,
)

data class DownloadProgress(
    val percentage: Double,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Double? = null,
    val etaSeconds: Long? = null,
    val phase: String,
)

data class AnalyzedMedia(
    val sourceUrl: String,
    val videoId: String?,
    val playlistId: String?,
    val playlistTitle: String?,
    val playlistIndex: Int?,
    val playlistCount: Int?,
    val title: String,
    val channel: String,
    val uploader: String,
    val uploadDate: String?,
    val duration: Double?,
    val thumbnailUrl: String?,
)
