package io.github.ytdw.android

import android.app.Application
import io.github.ytdw.android.data.database.AppDatabase
import io.github.ytdw.android.data.repository.QueueRepository
import io.github.ytdw.android.data.repository.SettingsRepository
import io.github.ytdw.android.download.engine.DownloadEngine
import io.github.ytdw.android.download.engine.FfmpegMetadataService
import io.github.ytdw.android.download.engine.ThumbnailProcessor
import io.github.ytdw.android.download.engine.YtDlpDownloadEngine
import io.github.ytdw.android.util.ErrorLogger
import io.github.ytdw.android.util.MediaStorePublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class YtdwApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runBlocking(Dispatchers.IO) {
            container.queueRepository.restoreUnfinished()
        }
    }
}

class AppContainer(application: Application) {
    val database = AppDatabase.create(application)
    val queueRepository = QueueRepository(database)
    val settingsRepository = SettingsRepository(database.dao())
    val downloadEngine: DownloadEngine = YtDlpDownloadEngine(application)
    val thumbnailProcessor = ThumbnailProcessor(application.cacheDir)
    val metadataService = FfmpegMetadataService(application)
    val mediaStorePublisher = MediaStorePublisher(application)
    val errorLogger = ErrorLogger(database.dao(), application.filesDir.resolve("logs"))
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as YtdwApplication).container
