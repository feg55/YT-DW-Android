package io.github.ytdw.android.download.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.github.ytdw.android.BuildConfig
import io.github.ytdw.android.domain.model.AnalyzedMedia
import io.github.ytdw.android.domain.model.AppSettings
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.domain.model.DownloadMode
import io.github.ytdw.android.domain.model.DownloadProgress
import io.github.ytdw.android.domain.model.VideoQuality
import io.github.ytdw.android.domain.service.ProgressParser
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class YtDlpDownloadEngine(private val context: Context) : DownloadEngine {
    @Volatile private var ytDlpInitialized = false
    @Volatile private var ffmpegInitialized = false
    private val ytDlpCacheDir: File by lazy {
        File(context.noBackupFilesDir, "yt-dlp-cache").also { directory ->
            check(directory.isDirectory || directory.mkdirs()) { "Could not create yt-dlp cache directory" }
        }
    }

    override fun analyze(urls: List<String>): Flow<Result<AnalyzedMedia>> = callbackFlow {
        val processIds = ConcurrentHashMap.newKeySet<String>()
        val sessionId = SystemClock.elapsedRealtimeNanos().toString(36)
        val task = launch(Dispatchers.IO) {
            ensureYtDlpInitialized()
            val analysisSlots = Semaphore(ANALYSIS_PARALLELISM)
            urls.mapIndexed { urlIndex, url ->
                launch {
                    analysisSlots.withPermit {
                        try {
                            currentCoroutineContext().ensureActive()
                            val seenEntries = mutableSetOf<String>()
                            var emittedEntries = 0
                            var attemptIndex = 0
                            fun execute(
                                forceIpv4: Boolean,
                                youtubeClient: String? = null,
                                flatPlaylist: Boolean = true,
                            ): String {
                                val processId = "analyze-$sessionId-$urlIndex-${attemptIndex++}-${url.hashCode()}"
                                val startedAt = SystemClock.elapsedRealtime()
                                val entriesBeforeAttempt = emittedEntries
                                val clientLabel = youtubeClient ?: "default"
                                Log.i(
                                    TAG,
                                    "Analysis attempt started: client=$clientLabel, " +
                                        "ipv4=$forceIpv4, flat=$flatPlaylist",
                                )
                                processIds += processId
                                return try {
                                    YoutubeDL.getInstance().execute(
                                        analysisRequest(url, forceIpv4, youtubeClient, flatPlaylist),
                                        processId,
                                    ) { _, _, line ->
                                        analyzedMediaFromLine(line, url)?.let { media ->
                                            val entryKey = media.videoId ?: media.sourceUrl
                                            if (seenEntries.add(entryKey)) {
                                                emittedEntries++
                                                trySend(Result.success(media))
                                            }
                                        }
                                    }.err
                                } finally {
                                    processIds -= processId
                                    Log.i(
                                        TAG,
                                        "Analysis attempt finished: client=$clientLabel, " +
                                            "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}, " +
                                            "entries=${emittedEntries - entriesBeforeAttempt}",
                                    )
                                }
                            }
                            fun executeIssue(
                                forceIpv4: Boolean,
                                youtubeClient: String? = null,
                                flatPlaylist: Boolean = true,
                            ) = runCatching { execute(forceIpv4, youtubeClient, flatPlaylist) }.fold(
                                onSuccess = { errorOutput ->
                                    errorOutput.takeIf(String::isNotBlank)?.let(::IllegalStateException)
                                },
                                onFailure = { it },
                            )
                            val isYouTube = url.isYouTubeUrl()
                            var issue = executeIssue(
                                forceIpv4 = isYouTube,
                                youtubeClient = "web_embedded".takeIf { isYouTube },
                            )
                            if (emittedEntries == 0 && isYouTube) {
                                issue = executeIssue(forceIpv4 = true, flatPlaylist = false)
                            } else if (emittedEntries == 0 && issue?.isRetryableNetworkError() == true) {
                                issue = executeIssue(forceIpv4 = true, flatPlaylist = false)
                            }
                            if (emittedEntries == 0) {
                                throw issue
                                    ?: IllegalStateException("yt-dlp completed without returning media information")
                            }
                        } catch (error: Throwable) {
                            error.throwIfCancellation()
                            trySend(Result.failure(error))
                        }
                    }
                }
            }.joinAll()
            close()
        }
        awaitClose {
            task.cancel()
            processIds.toList().forEach(YoutubeDL.getInstance()::destroyProcessById)
        }
    }.buffer(Channel.UNLIMITED)

    private fun analysisRequest(
        url: String,
        forceIpv4: Boolean,
        youtubeClient: String?,
        flatPlaylist: Boolean,
    ) = YoutubeDLRequest(url).apply {
        addOption("--dump-json")
        addOption("--skip-download")
        addOption("--ignore-errors")
        addOption("--ignore-no-formats-error")
        addOption("--yes-playlist")
        if (flatPlaylist) addOption("--flat-playlist")
        addOption("--lazy-playlist")
        addOption("--socket-timeout", "15")
        addOption("--retries", "1")
        addOption("--extractor-retries", "1")
        addOption("--cache-dir", ytDlpCacheDir.absolutePath)
        if (forceIpv4) addOption("--force-ipv4")
        if (youtubeClient != null) {
            addOption("--extractor-args", "youtube:player_client=$youtubeClient;player_skip=js")
        }
    }

    override suspend fun download(
        item: DownloadItem,
        settings: AppSettings,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): DownloadedFile = withContext(Dispatchers.IO) {
        onProgress(DownloadProgress(percentage = 0.0, phase = "Preparing downloader"))
        val initializationStartedAt = SystemClock.elapsedRealtime()
        ensureYtDlpInitialized()
        val ytDlpReadyAt = SystemClock.elapsedRealtime()
        ensureFfmpegInitialized()
        Log.i(
            TAG,
            "Download runtimes ready: ytDlpMs=${ytDlpReadyAt - initializationStartedAt}, " +
                "ffmpegMs=${SystemClock.elapsedRealtime() - ytDlpReadyAt}",
        )
        val itemDir = File(context.cacheDir, "downloads/${item.id}")
        val expectedExtension = if (item.downloadMode == DownloadMode.AUDIO) "m4a" else "mp4"
        val reusable = itemDir.resolve("media.$expectedExtension").takeIf(File::isFile)
        if (reusable != null) {
            return@withContext DownloadedFile(
                reusable.absolutePath,
                metadata = readDownloadedMetadata(itemDir, item),
            )
        }
        itemDir.listFiles()?.filter { it.name.startsWith("source-") }?.forEach { it.deleteRecursively() }
        if (itemDir.exists() && !settings.continuePartialDownloads) deleteTransferFiles(itemDir)
        check(itemDir.isDirectory || itemDir.mkdirs()) { "Could not create temporary download directory" }
        val output = File(itemDir, "media.%(ext)s").absolutePath
        fun request(
            forceIpv4: Boolean,
            youtubeClient: String? = null,
            fastYouTubeExtraction: Boolean = false,
        ): YoutubeDLRequest {
            val request = YoutubeDLRequest(item.sourceUrl)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--newline")
            request.addOption("--write-info-json")
            request.addOption("--socket-timeout", settings.socketTimeoutSeconds.coerceIn(5, 15).toString())
            request.addOption(
                "--retries",
                if (fastYouTubeExtraction) "0" else settings.retryCount.coerceIn(0, 2).toString(),
            )
            request.addOption("--fragment-retries", settings.fragmentRetryCount.coerceIn(0, 3).toString())
            request.addOption("--concurrent-fragments", settings.concurrentFragmentDownloads.coerceIn(1, 8).toString())
            request.addOption("--extractor-retries", if (fastYouTubeExtraction) "0" else "1")
            request.addOption(if (settings.continuePartialDownloads) "--continue" else "--no-continue")
            request.addOption("--cache-dir", ytDlpCacheDir.absolutePath)
            if (forceIpv4) request.addOption("--force-ipv4")
            if (youtubeClient != null || fastYouTubeExtraction) {
                request.addOption(
                    "--extractor-args",
                    youtubeExtractorArgs(youtubeClient, skipManifests = fastYouTubeExtraction),
                )
            }
            if (fastYouTubeExtraction) request.addOption("--no-check-formats")
            request.addOption("-o", output)
            if (item.downloadMode == DownloadMode.AUDIO) {
                request.addOption("-f", "bestaudio[ext=m4a]/bestaudio")
                request.addOption("--extract-audio")
                request.addOption("--audio-format", "m4a")
                request.addOption("--audio-quality", "0")
            } else {
                request.addOption("-f", videoFormat(item.videoQuality))
                request.addOption("--merge-output-format", "mp4")
            }
            return request
        }
        suspend fun execute(request: YoutubeDLRequest) = coroutineScope {
            val startedAt = SystemClock.elapsedRealtime()
            val updates = Channel<DownloadProgress>(Channel.CONFLATED)
            var lastSourceStage: String? = null
            val consumer = launch {
                var lastDeliveredAt = 0L
                try {
                    for (received in updates) {
                        val elapsed = SystemClock.elapsedRealtime() - lastDeliveredAt
                        if (lastDeliveredAt != 0L && elapsed < PROGRESS_UPDATE_INTERVAL_MS && received.percentage < 100.0) {
                            delay(PROGRESS_UPDATE_INTERVAL_MS - elapsed)
                        }
                        var latest = received
                        while (true) {
                            latest = updates.tryReceive().getOrNull() ?: break
                        }
                        onProgress(latest)
                        lastDeliveredAt = SystemClock.elapsedRealtime()
                    }
                } catch (error: Throwable) {
                    YoutubeDL.getInstance().destroyProcessById(item.id)
                    throw error
                }
            }
            try {
                YoutubeDL.getInstance().execute(request, item.id) { progress, eta, line ->
                    sourceConnectionStage(line)?.takeIf { it != lastSourceStage }?.let { stage ->
                        lastSourceStage = stage
                        Log.i(
                            TAG,
                            "Source connection stage: item=${item.id}, " +
                                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}, stage=$stage",
                        )
                    }
                    updates.trySend(ProgressParser.fromCallback(progress, eta, line))
                }
            } finally {
                updates.close()
                consumer.join()
                Log.i(TAG, "Download yt-dlp execution finished: elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            }
        }
        val isYouTube = item.sourceUrl.isYouTubeUrl()
        suspend fun executeFresh() {
            if (!isYouTube) {
                try {
                    execute(request(forceIpv4 = false))
                } catch (error: Throwable) {
                    error.throwIfCancellation()
                    if (!error.isRetryableNetworkError()) throw error
                    deleteTransferFiles(itemDir)
                    onProgress(DownloadProgress(percentage = 0.0, phase = "Retrying via IPv4"))
                    execute(request(forceIpv4 = true))
                }
                return
            }
            try {
                execute(
                    request(
                        forceIpv4 = true,
                        youtubeClient = "android_vr",
                        fastYouTubeExtraction = true,
                    ),
                )
                return
            } catch (primaryError: Throwable) {
                primaryError.throwIfCancellation()
                if (!primaryError.shouldTryAlternateYouTubeClient()) throw primaryError
                deleteTransferFiles(itemDir)
                onProgress(DownloadProgress(percentage = 0.0, phase = "Retrying with alternate source client"))
            }
            try {
                execute(
                    request(
                        forceIpv4 = true,
                        youtubeClient = "web_embedded",
                        fastYouTubeExtraction = true,
                    ),
                )
            } catch (fastFallbackError: Throwable) {
                fastFallbackError.throwIfCancellation()
                if (!fastFallbackError.shouldTryAlternateYouTubeClient()) throw fastFallbackError
                deleteTransferFiles(itemDir)
                onProgress(DownloadProgress(percentage = 0.0, phase = "Retrying in compatibility mode"))
                execute(request(forceIpv4 = true))
            }
        }
        onProgress(DownloadProgress(percentage = 0.0, phase = "Connecting to source"))
        executeFresh()
        val media = itemDir.listFiles()?.firstOrNull { it.extension.equals(expectedExtension, true) }
            ?: error("yt-dlp completed without producing a $expectedExtension file")
        DownloadedFile(media.absolutePath, metadata = readDownloadedMetadata(itemDir, item))
    }

    override fun cancel(itemId: String) {
        if (ytDlpInitialized) YoutubeDL.getInstance().destroyProcessById(itemId)
    }

    private fun deleteTransferFiles(itemDir: File) {
        itemDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun youtubeExtractorArgs(client: String?, skipManifests: Boolean): String = buildList {
        client?.let { add("player_client=$it") }
        if (skipManifests) add("skip=hls,dash")
    }.joinToString(separator = ";", prefix = "youtube:")

    private fun sourceConnectionStage(line: String): String? {
        val normalized = line.lowercase()
        return when {
            "downloading webpage" in normalized -> "webpage"
            "player api json" in normalized -> "player_api"
            "downloading initial data api json" in normalized -> "initial_data_api"
            "downloading m3u8 information" in normalized -> "hls_manifest"
            "downloading mpd manifest" in normalized -> "dash_manifest"
            "downloading player" in normalized -> "player_javascript"
            "extracting url" in normalized -> "extracting"
            else -> null
        }
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) throw this
        if (this is YoutubeDL.CanceledException) {
            throw CancellationException(message ?: "yt-dlp process cancelled", this)
        }
    }

    @Synchronized
    private fun ensureYtDlpInitialized() {
        if (ytDlpInitialized) return
        val applicationContext = context.applicationContext
        val preferences = applicationContext.getSharedPreferences("bundled-runtime", Context.MODE_PRIVATE)
        val installedVersion = preferences.getString("yt-dlp-version", null)
        if (installedVersion != BuildConfig.YT_DLP_VERSION) {
            val installedRuntime = File(
                applicationContext.noBackupFilesDir,
                "youtubedl-android/yt-dlp/yt-dlp",
            )
            check(!installedRuntime.exists() || installedRuntime.delete()) {
                "Could not replace the outdated bundled yt-dlp runtime"
            }
        }
        YoutubeDL.getInstance().init(applicationContext)
        preferences.edit { putString("yt-dlp-version", BuildConfig.YT_DLP_VERSION) }
        ytDlpInitialized = true
    }

    @Synchronized
    private fun ensureFfmpegInitialized() {
        if (ffmpegInitialized) return
        FFmpeg.getInstance().init(context.applicationContext)
        ffmpegInitialized = true
    }

    private fun analyzedMediaFromLine(line: String, fallbackUrl: String): AnalyzedMedia? {
        val jsonLine = line.trim()
        if (!jsonLine.startsWith('{') || !jsonLine.endsWith('}')) return null
        return runCatching { toMedia(JSONObject(jsonLine), fallbackUrl, null, null, null, null) }.getOrNull()
    }

    private fun toMedia(
        json: JSONObject,
        fallbackUrl: String,
        playlistId: String?,
        playlistTitle: String?,
        fallbackIndex: Int?,
        fallbackCount: Int?,
    ): AnalyzedMedia {
        val videoId = json.text("id")
        val extractedUrl = json.text("webpage_url") ?: json.text("url")
        val sourceUrl = extractedUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: videoId?.takeIf { fallbackUrl.isYouTubeUrl() }?.let { "https://www.youtube.com/watch?v=$it" }
            ?: fallbackUrl
        return AnalyzedMedia(
            sourceUrl = sourceUrl,
            videoId = videoId,
            playlistId = json.text("playlist_id") ?: playlistId,
            playlistTitle = json.text("playlist_title") ?: playlistTitle,
            playlistIndex = json.integer("playlist_index") ?: fallbackIndex,
            playlistCount = json.integer("playlist_count") ?: json.integer("n_entries") ?: fallbackCount,
            title = json.text("title") ?: json.text("fulltitle") ?: "Untitled",
            channel = json.text("channel") ?: json.text("creator").orEmpty(),
            uploader = json.text("uploader").orEmpty(),
            uploadDate = json.text("upload_date"),
            duration = json.optDouble("duration").takeIf { !it.isNaN() },
            thumbnailUrl = bestThumbnail(json)
                ?: videoId?.takeIf { fallbackUrl.isYouTubeUrl() }
                    ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" },
        )
    }

    private fun bestThumbnail(json: JSONObject): String? {
        val thumbnails = json.optJSONArray("thumbnails") ?: JSONArray()
        for (index in thumbnails.length() - 1 downTo 0) {
            thumbnails.optJSONObject(index)?.text("url")?.let { return it }
        }
        return json.text("thumbnail")
    }

    private fun readDownloadedMetadata(itemDir: File, item: DownloadItem): AnalyzedMedia? {
        val infoFile = itemDir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".info.json") } ?: return null
        return runCatching {
            toMedia(
                JSONObject(infoFile.readText()),
                item.sourceUrl,
                item.playlistId,
                item.playlistTitle,
                item.playlistIndex,
                item.playlistCount,
            )
        }.getOrNull()
    }

    private fun JSONObject.text(name: String): String? = optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }
    private fun JSONObject.integer(name: String): Int? = if (has(name) && !isNull(name)) optInt(name).takeIf { it > 0 } else null

    private fun Throwable.isRetryableNetworkError(): Boolean {
        val details = generateSequence(this) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
            .lowercase()
        return RETRYABLE_NETWORK_ERRORS.any(details::contains)
    }

    private fun Throwable.shouldTryAlternateYouTubeClient(): Boolean {
        val details = generateSequence(this) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
            .lowercase()
        return isRetryableNetworkError() || YOUTUBE_CLIENT_FALLBACK_ERRORS.any(details::contains)
    }

    private fun String.isYouTubeUrl(): Boolean = runCatching { java.net.URI(this).host.orEmpty().lowercase() }
        .getOrDefault("")
        .let { host -> host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") }

    private fun videoFormat(quality: VideoQuality): String {
        if (quality == VideoQuality.BEST) return "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
        val height = quality.value.removeSuffix("p")
        return "bestvideo[height<=$height][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=$height]+bestaudio/best[height<=$height]"
    }

    private companion object {
        const val TAG = "YtDlpEngine"
        const val ANALYSIS_PARALLELISM = 3
        val RETRYABLE_NETWORK_ERRORS = listOf(
            "certificate verify failed",
            "connection aborted",
            "connection refused",
            "connection reset",
            "connection timed out",
            "forbidden",
            "hostname",
            "http error 403",
            "name resolution",
            "network is unreachable",
            "no address associated",
            "ssl",
            "temporary failure",
            "timed out",
            "tls",
            "unable to resolve host",
        )
        val YOUTUBE_CLIENT_FALLBACK_ERRORS = listOf(
            "only images are available",
            "requested format is not available",
            "video unavailable",
        )
        const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    }

}
