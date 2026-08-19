package io.github.ytdw.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ytdw.android.appContainer
import io.github.ytdw.android.domain.model.AppSettings
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.domain.model.DownloadMode
import io.github.ytdw.android.domain.model.DownloadStatus
import io.github.ytdw.android.domain.model.VideoQuality
import io.github.ytdw.android.domain.service.ErrorMapper
import io.github.ytdw.android.domain.service.MetadataCleaner
import io.github.ytdw.android.download.worker.DownloadScheduler
import java.net.URI
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    val queue: StateFlow<List<DownloadItem>> = container.queueRepository.queue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = container.queueRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val currentTab = MutableStateFlow(0)
    val urlDraft = MutableStateFlow("")
    val analysisRunning = MutableStateFlow(false)
    val analysisFoundCount = MutableStateFlow(0)
    val analysisErrors = MutableStateFlow<List<String>>(emptyList())
    val queueError = MutableStateFlow<String?>(null)
    val selectedMode = MutableStateFlow(DownloadMode.AUDIO)
    val selectedQuality = MutableStateFlow(VideoQuality.BEST)
    private var analysisJob: Job? = null
    private val thumbnailSlots = Semaphore(4)

    init {
        viewModelScope.launch {
            val restored = container.settingsRepository.load()
            if (restored.rememberLastTab) currentTab.value = restored.lastTab.coerceIn(0, 2)
        }
    }

    fun selectTab(index: Int) {
        currentTab.value = index.coerceIn(0, 2)
        viewModelScope.launch {
            if (settings.value.rememberLastTab) saveSettings(settings.value.copy(lastTab = currentTab.value))
        }
    }

    fun analyze() {
        if (analysisRunning.value) return
        val urls = parseUrls(urlDraft.value)
        if (urls.isFailure) {
            analysisErrors.value = listOf(
                if (isRussian(settings.value.language)) "Введите корректные HTTP или HTTPS URL, по одному в строке"
                else urls.exceptionOrNull()?.message.orEmpty(),
            )
            return
        }
        analysisErrors.value = emptyList()
        analysisFoundCount.value = 0
        analysisRunning.value = true
        val analysisSettings = settings.value
        val analysisMode = selectedMode.value
        val analysisQuality = selectedQuality.value
        analysisJob = viewModelScope.launch {
            try {
                container.downloadEngine.analyze(urls.getOrThrow()).collect { result ->
                    result.fold(
                        onSuccess = { media ->
                            val metadata = analysisSettings.metadata
                            val channelName = media.channel.ifEmpty { media.uploader }
                            val cleaned = MetadataCleaner.cleanTrackTitle(
                                media.title,
                                channelName.takeIf { metadata.removeChannelFromTitle }.orEmpty(),
                                metadata.removeLabels,
                            )
                            val item = container.queueRepository.add(
                                DownloadItem(
                                    sourceUrl = media.sourceUrl, videoId = media.videoId,
                                    playlistId = media.playlistId, playlistTitle = media.playlistTitle,
                                    playlistIndex = media.playlistIndex, playlistCount = media.playlistCount,
                                    originalTitle = media.title, cleanedTitle = cleaned,
                                    channel = media.channel, uploader = media.uploader,
                                    artist = channelName.takeIf { metadata.useChannelAsArtist }.orEmpty(),
                                    albumArtist = channelName.takeIf { metadata.useChannelAsAlbumArtist }.orEmpty(),
                                    album = media.playlistTitle.takeIf { metadata.usePlaylistTitleAsAlbum }.orEmpty(),
                                    trackNumber = media.playlistIndex.takeIf { metadata.usePlaylistIndexAsTrack },
                                    uploadDate = media.uploadDate, duration = media.duration,
                                    thumbnailUrl = media.thumbnailUrl, downloadMode = analysisMode,
                                    videoQuality = analysisQuality, status = DownloadStatus.READY,
                                ),
                                analysisSettings.skipDownloadArchive,
                            )
                            analysisFoundCount.value++
                            if (item.thumbnailUrl != null && item.cachedThumbnailPath == null) cachePreview(item)
                        },
                        onFailure = { error ->
                            val mapped = ErrorMapper.map(error)
                            analysisErrors.value += localizedError(mapped.category.name, mapped.userMessage, settings.value.language)
                            container.errorLogger.log(mapped.category.name, mapped.userMessage, mapped.technical)
                        },
                    )
                }
                if (analysisFoundCount.value > 0) selectTab(1)
            } finally {
                analysisRunning.value = false
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisRunning.value = false
    }

    private fun cachePreview(item: DownloadItem) {
        viewModelScope.launch {
            runCatching {
                thumbnailSlots.withPermit {
                    container.thumbnailProcessor.download(item.thumbnailUrl!!, item.id, false)
                }
            }
                .onSuccess { container.queueRepository.save(item.copy(cachedThumbnailPath = it.absolutePath)) }
        }
    }

    fun updateItem(item: DownloadItem) = viewModelScope.launch { container.queueRepository.save(item) }

    fun startQueue() = viewModelScope.launch {
        queueError.value = null
        container.settingsRepository.setQueuePaused(false)
        runCatching { DownloadScheduler.start(getApplication()) }
            .onFailure {
                container.settingsRepository.setQueuePaused(true)
                val mapped = ErrorMapper.map(it)
                queueError.value = localizedError(mapped.category.name, mapped.userMessage, settings.value.language)
                container.errorLogger.log(mapped.category.name, mapped.userMessage, mapped.technical)
            }
    }

    fun pauseQueue() = viewModelScope.launch { container.settingsRepository.setQueuePaused(true) }

    fun cancelCurrent() = viewModelScope.launch {
        val item = queue.value.firstOrNull { it.status in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING) } ?: return@launch
        container.downloadEngine.cancel(item.id)
        container.queueRepository.cancel(item.id)
    }

    fun retryFailed() = viewModelScope.launch { container.queueRepository.retryFailed() }
    fun retry(item: DownloadItem) = viewModelScope.launch { container.queueRepository.retry(item.id) }
    fun removeCompleted() = viewModelScope.launch { container.queueRepository.removeCompleted() }
    fun clearAll() = viewModelScope.launch { container.queueRepository.clearAll() }

    fun saveSettings(value: AppSettings) = viewModelScope.launch { container.settingsRepository.save(value) }

    fun open(item: DownloadItem) {
        val uri = item.contentUri?.let(Uri::parse) ?: return
        val mime = if (item.downloadMode == DownloadMode.AUDIO) "audio/mp4" else "video/mp4"
        runCatching { container.mediaStorePublisher.open(uri, mime) }
            .onFailure { error ->
                val mapped = ErrorMapper.map(error)
                queueError.value = localizedError(mapped.category.name, mapped.userMessage, settings.value.language)
                viewModelScope.launch {
                    container.errorLogger.log(mapped.category.name, mapped.userMessage, mapped.technical)
                }
            }
    }

    companion object {
        fun parseUrls(text: String): Result<List<String>> = runCatching {
            val urls = text.lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
            require(urls.isNotEmpty()) { "Enter at least one HTTP URL" }
            require(urls.all { runCatching { URI(it) }.getOrNull()?.let { uri -> uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() } == true }) {
                "Enter valid HTTP or HTTPS URLs, one per line"
            }
            urls
        }
    }
}
