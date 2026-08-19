package io.github.ytdw.android.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.ytdw.android.data.database.AppDatabase
import io.github.ytdw.android.domain.model.AppSettings
import io.github.ytdw.android.domain.model.DownloadItem
import io.github.ytdw.android.domain.model.DownloadStatus
import io.github.ytdw.android.domain.model.LanguagePreference
import io.github.ytdw.android.domain.model.ThemePreference
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var queue: QueueRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        queue = QueueRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun queuePersistsAndRestoresInterruptedStates() = runTest {
        val ready = queue.add(DownloadItem(sourceUrl = "https://example.test/ready", videoId = "ready", originalTitle = "Ready", status = DownloadStatus.READY), false)
        val downloading = queue.add(DownloadItem(sourceUrl = "https://example.test/downloading", videoId = "downloading", originalTitle = "Downloading", status = DownloadStatus.DOWNLOADING), false)
        val analyzing = queue.add(DownloadItem(sourceUrl = "https://example.test/analyzing", status = DownloadStatus.ANALYZING), false)
        queue.restoreUnfinished()
        assertEquals(DownloadStatus.READY, queue.get(ready.id)?.status)
        assertEquals(DownloadStatus.READY, queue.get(downloading.id)?.status)
        assertEquals(DownloadStatus.PENDING, queue.get(analyzing.id)?.status)
    }

    @Test fun pauseIndependentClaimCancelAndRetryTransitionsPersist() = runTest {
        val item = queue.add(DownloadItem(sourceUrl = "https://example.test/a", videoId = "a", originalTitle = "A", status = DownloadStatus.READY), false)
        val claimed = queue.claimNext(false)
        assertEquals(item.id, claimed?.id)
        assertEquals(DownloadStatus.DOWNLOADING, claimed?.status)
        assertTrue(queue.release(item.id))
        assertEquals(DownloadStatus.READY, queue.get(item.id)?.status)
        assertEquals(item.id, queue.claimNext(false)?.id)
        queue.cancel(item.id)
        assertEquals(DownloadStatus.CANCELLED, queue.get(item.id)?.status)
        assertFalse(queue.processing(item.id, "Processing"))
        assertEquals(
            DownloadStatus.CANCELLED,
            queue.complete(item.id, "content://media/cancelled", "cancelled.m4a")?.status,
        )
        queue.retry(item.id)
        assertEquals(DownloadStatus.READY, queue.get(item.id)?.status)
        queue.fail(item.id, "network", "Network error", "fixture")
        assertEquals(DownloadStatus.FAILED, queue.get(item.id)?.status)
        assertEquals(1, queue.get(item.id)?.retryCount)
        assertEquals(1, queue.retryFailed())
        assertEquals(DownloadStatus.READY, queue.get(item.id)?.status)
    }

    @Test fun completionCreatesHistoryAndArchiveThenDuplicateIsSkipped() = runTest {
        val item = queue.add(DownloadItem(sourceUrl = "https://example.test/a", videoId = "archive", originalTitle = "A", cleanedTitle = "A", artist = "Artist", status = DownloadStatus.READY), false)
        assertNotNull(queue.complete(item.id, "content://media/a", "A.m4a"))
        assertEquals(1, queue.removeCompleted())
        val duplicate = queue.add(DownloadItem(sourceUrl = "https://example.test/a2", videoId = "archive", status = DownloadStatus.READY), true)
        assertEquals(DownloadStatus.SKIPPED, duplicate.status)
        val cleared = queue.clearAll()
        assertTrue(cleared.second >= 2)
    }

    @Test fun settingsRoundTripWithTypedValues() = runTest {
        val repository = SettingsRepository(database.dao())
        val value = AppSettings(
            theme = ThemePreference.LIGHT,
            language = LanguagePreference.RUSSIAN,
            retryCount = 3,
            audioVolumeName = "1234-abcd",
            audioRelativePath = "Music/Tracks",
            videoRelativePath = "Movies/Clips",
        )
        repository.save(value)
        val restored = repository.load()
        assertEquals(ThemePreference.LIGHT, restored.theme)
        assertEquals(LanguagePreference.RUSSIAN, restored.language)
        assertEquals(3, restored.retryCount)
        assertEquals("1234-abcd", restored.audioVolumeName)
        assertEquals("Music/Tracks", restored.audioRelativePath)
        assertEquals("Movies/Clips", restored.videoRelativePath)
        repository.setQueuePaused(false)
        assertTrue(!repository.isQueuePaused())
    }

    @Test fun migrationContractRemainsVersionOneToTwo() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)
    }
}
