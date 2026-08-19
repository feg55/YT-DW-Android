package io.github.ytdw.android.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM queue_items ORDER BY position, createdAt, id")
    fun observeQueue(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items ORDER BY position, createdAt, id")
    suspend fun listQueue(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE id = :id")
    suspend fun getQueueItem(id: String): QueueItemEntity?

    @Query("SELECT * FROM queue_items WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): QueueItemEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM queue_items")
    suspend fun nextPosition(): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQueueItem(item: QueueItemEntity): Long

    @Update
    suspend fun updateQueueItem(item: QueueItemEntity)

    @Query("DELETE FROM queue_items WHERE id = :id")
    suspend fun deleteQueueItem(id: String): Int

    @Query("DELETE FROM queue_items WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted(): Int

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue(): Int

    @Query("SELECT * FROM queue_items WHERE status = 'READY' AND selected = 1 ORDER BY position LIMIT 1")
    suspend fun nextReady(): QueueItemEntity?

    @Query("UPDATE queue_items SET status = :status, currentPhase = :phase, updatedAt = :now WHERE id = :id AND status = :expected")
    suspend fun compareAndSetStatus(id: String, expected: String, status: String, phase: String, now: Long): Int

    @Query("UPDATE queue_items SET status = 'PROCESSING', currentPhase = :phase, speed = NULL, etaSeconds = NULL, updatedAt = :now WHERE id = :id AND status IN ('DOWNLOADING', 'PROCESSING')")
    suspend fun markProcessing(id: String, phase: String, now: Long): Int

    @Query("UPDATE queue_items SET status = 'CANCELLED', currentPhase = 'Cancelled', speed = NULL, etaSeconds = NULL, updatedAt = :now WHERE id = :id AND status IN ('DOWNLOADING', 'PROCESSING')")
    suspend fun cancelActive(id: String, now: Long): Int

    @Query("UPDATE queue_items SET status = 'READY', currentPhase = '', speed = NULL, etaSeconds = NULL, updatedAt = :now WHERE id = :id AND status IN ('DOWNLOADING', 'PROCESSING')")
    suspend fun releaseActive(id: String, now: Long): Int

    @Query("UPDATE queue_items SET status = 'READY', currentPhase = '', speed = NULL, etaSeconds = NULL, updatedAt = :now WHERE status IN ('DOWNLOADING', 'PROCESSING')")
    suspend fun restoreDownloads(now: Long): Int

    @Query("UPDATE queue_items SET status = 'PENDING', currentPhase = '', speed = NULL, etaSeconds = NULL, updatedAt = :now WHERE status = 'ANALYZING'")
    suspend fun restoreAnalysis(now: Long): Int

    @Insert
    suspend fun insertHistory(item: HistoryEntity): Long

    @Query("SELECT * FROM download_history ORDER BY id DESC")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM download_history")
    suspend fun clearHistory(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArchive(item: ArchiveEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM download_archive WHERE archiveKey = :key)")
    suspend fun archiveContains(key: String): Boolean

    @Query("DELETE FROM download_archive")
    suspend fun clearArchive(): Int

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: SettingsEntity)

    @Insert
    suspend fun insertError(error: ErrorLogEntity)

    @Query("SELECT * FROM error_log ORDER BY id DESC LIMIT :limit")
    suspend fun recentErrors(limit: Int = 100): List<ErrorLogEntity>

    @Query("DELETE FROM error_log WHERE id NOT IN (SELECT id FROM error_log ORDER BY id DESC LIMIT 500)")
    suspend fun trimErrors()

    @Transaction
    suspend fun clearDownloadState(): Triple<Int, Int, Int> =
        Triple(clearQueue(), clearHistory(), clearArchive())
}
