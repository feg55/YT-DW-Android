package io.github.ytdw.android.download.engine

import android.content.Context
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class YtDlpDownloadEngine(private val context: Context) : DownloadEngine {
    @Volatile private var ytDlpInitialized = false
    @Volatile private var ffmpegInitialized = false

    override fun analyze(urls: List<String>): Flow<Result<AnalyzedMedia>> = callbackFlow {
        val processIds = ConcurrentHashMap.newKeySet<String>()
        val task = launch(Dispatchers.IO) {
            ensureYtDlpInitialized()
            urls.forEach { url ->
                try {
                    currentCoroutineContext().ensureActive()
                    val seenEntries = mutableSetOf<String>()
                    var emittedEntries = 0
                    fun execute(forceIpv4: Boolean, youtubeClient: String? = null): String {
                        val processId = "analyze-${url.hashCode()}"
                        processIds += processId
                        return try {
                            YoutubeDL.getInstance().execute(
                                analysisRequest(url, forceIpv4, youtubeClient),
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
                        }
                    }
                    fun executeIssue(forceIpv4: Boolean, youtubeClient: String? = null) =
                        runCatching { execute(forceIpv4, youtubeClient) }.fold(
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
                        issue = executeIssue(forceIpv4 = true)
                    } else if (emittedEntries == 0 && issue?.isRetryableNetworkError() == true) {
                        issue = executeIssue(forceIpv4 = true)
                    }
                    if (emittedEntries == 0) {
                        throw issue
                            ?: IllegalStateException("yt-dlp completed without returning media information")
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    trySend(Result.failure(error))
                }
            }
            close()
        }
        awaitClose {
            task.cancel()
            processIds.toList().forEach(YoutubeDL.getInstance()::destroyProcessById)
        }
    }.buffer(Channel.UNLIMITED)

    private fun analysisRequest(url: String, forceIpv4: Boolean, youtubeClient: String?) = YoutubeDLRequest(url).apply {
        addOption("--dump-json")
        addOption("--skip-download")
        addOption("--ignore-errors")
        addOption("--yes-playlist")
        addOption("--lazy-playlist")
        addOption("--socket-timeout", "15")
        addOption("--retries", "1")
        addOption("--extractor-retries", "1")
        if (forceIpv4) addOption("--force-ipv4")
        if (youtubeClient != null) addOption("--extractor-args", "youtube:player_client=$youtubeClient")
    }

    override suspend fun download(
        item: DownloadItem,
        settings: AppSettings,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): DownloadedFile = withContext(Dispatchers.IO) {
        onProgress(DownloadProgress(percentage = 0.0, phase = "Preparing downloader"))
        ensureYtDlpInitialized()
        ensureFfmpegInitialized()
        val itemDir = File(context.cacheDir, "downloads/${item.id}")
        val reusable = itemDir.resolve("media.m4a")
            .takeIf { item.errorCategory == "metadata" && it.isFile }
        if (reusable != null) return@withContext DownloadedFile(reusable.absolutePath)
        if (itemDir.exists()) itemDir.deleteRecursively()
        check(itemDir.mkdirs()) { "Could not create temporary download directory" }
        val output = File(itemDir, "media.%(ext)s").absolutePath
        fun request(
            forceIpv4: Boolean,
            youtubeClient: String? = null,
        ): YoutubeDLRequest {
            val request = YoutubeDLRequest(item.sourceUrl)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--newline")
            request.addOption("--socket-timeout", settings.socketTimeoutSeconds.coerceIn(5, 15).toString())
            request.addOption("--retries", settings.retryCount.coerceIn(0, 2).toString())
            request.addOption("--fragment-retries", settings.fragmentRetryCount.coerceIn(0, 3).toString())
            request.addOption("--extractor-retries", "1")
            request.addOption(if (settings.continuePartialDownloads) "--continue" else "--no-continue")
            if (forceIpv4) request.addOption("--force-ipv4")
            if (youtubeClient != null) {
                request.addOption("--extractor-args", "youtube:player_client=$youtubeClient")
            }
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
        fun execute(request: YoutubeDLRequest) {
            YoutubeDL.getInstance().execute(request, item.id) { progress, eta, line ->
                kotlinx.coroutines.runBlocking { onProgress(ProgressParser.fromCallback(progress, eta, line)) }
            }
        }
        onProgress(DownloadProgress(percentage = 0.0, phase = "Connecting to source"))
        val isYouTube = item.sourceUrl.isYouTubeUrl()
        try {
            execute(
                request(
                    forceIpv4 = isYouTube,
                    youtubeClient = "web_embedded".takeIf { isYouTube },
                ),
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (isYouTube) {
                if (!error.shouldTryDefaultYouTubeClient()) throw error
                itemDir.listFiles()?.forEach { it.deleteRecursively() }
                onProgress(DownloadProgress(percentage = 0.0, phase = "Retrying with default source client"))
                execute(request(forceIpv4 = true))
            } else {
                if (!error.isRetryableNetworkError()) throw error
                onProgress(DownloadProgress(percentage = 0.0, phase = "Retrying via IPv4"))
                execute(request(forceIpv4 = true))
            }
        }
        val expectedExtension = if (item.downloadMode == DownloadMode.AUDIO) "m4a" else "mp4"
        val media = itemDir.listFiles()?.firstOrNull { it.extension.equals(expectedExtension, true) }
            ?: error("yt-dlp completed without producing a $expectedExtension file")
        DownloadedFile(media.absolutePath)
    }

    override fun cancel(itemId: String) {
        if (ytDlpInitialized) YoutubeDL.getInstance().destroyProcessById(itemId)
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
    ) = AnalyzedMedia(
        sourceUrl = json.text("webpage_url") ?: json.text("url") ?: fallbackUrl,
        videoId = json.text("id"),
        playlistId = json.text("playlist_id") ?: playlistId,
        playlistTitle = json.text("playlist_title") ?: playlistTitle,
        playlistIndex = json.integer("playlist_index") ?: fallbackIndex,
        playlistCount = json.integer("playlist_count") ?: json.integer("n_entries") ?: fallbackCount,
        title = json.text("title") ?: json.text("fulltitle") ?: "Untitled",
        channel = json.text("channel") ?: json.text("creator").orEmpty(),
        uploader = json.text("uploader").orEmpty(),
        uploadDate = json.text("upload_date"),
        duration = json.optDouble("duration").takeIf { !it.isNaN() },
        thumbnailUrl = bestThumbnail(json),
    )

    private fun bestThumbnail(json: JSONObject): String? {
        val thumbnails = json.optJSONArray("thumbnails") ?: JSONArray()
        for (index in thumbnails.length() - 1 downTo 0) {
            thumbnails.optJSONObject(index)?.text("url")?.let { return it }
        }
        return json.text("thumbnail")
    }

    private fun JSONObject.text(name: String): String? = optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }
    private fun JSONObject.integer(name: String): Int? = if (has(name) && !isNull(name)) optInt(name).takeIf { it > 0 } else null

    private fun Throwable.isRetryableNetworkError(): Boolean {
        val details = generateSequence(this) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
            .lowercase()
        return RETRYABLE_NETWORK_ERRORS.any(details::contains)
    }

    private fun Throwable.shouldTryDefaultYouTubeClient(): Boolean {
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
    }

}
