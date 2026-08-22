package io.github.ytdw.android.domain.model

enum class ThemePreference { DARK, ORIGINAL, LIGHT, SYSTEM }
enum class LanguagePreference { SYSTEM, RUSSIAN, ENGLISH }

data class MetadataSettings(
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
)

data class AppSettings(
    val theme: ThemePreference = ThemePreference.DARK,
    val language: LanguagePreference = LanguagePreference.SYSTEM,
    val rememberLastTab: Boolean = true,
    val lastTab: Int = 0,
    val metadata: MetadataSettings = MetadataSettings(),
    val skipDownloadArchive: Boolean = true,
    val retryCount: Int = 5,
    val fragmentRetryCount: Int = 5,
    val continuePartialDownloads: Boolean = true,
    val socketTimeoutSeconds: Int = 30,
    val parallelDownloads: Int = 2,
    val concurrentFragmentDownloads: Int = 4,
    val audioVolumeName: String = "external_primary",
    val audioRelativePath: String = "Music/YT-DW",
    val videoVolumeName: String = "external_primary",
    val videoRelativePath: String = "Movies/YT-DW",
)
