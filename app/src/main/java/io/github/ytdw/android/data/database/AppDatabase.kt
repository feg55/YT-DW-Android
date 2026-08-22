package io.github.ytdw.android.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QueueItemEntity::class, HistoryEntity::class, ArchiveEntity::class, SettingsEntity::class, ErrorLogEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queue_items ADD COLUMN contentUri TEXT")
                db.execSQL("ALTER TABLE queue_items ADD COLUMN displayName TEXT")
                db.execSQL("ALTER TABLE download_history ADD COLUMN contentUri TEXT")
                db.execSQL("ALTER TABLE download_history ADD COLUMN displayName TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN audioVolumeName TEXT NOT NULL DEFAULT 'external_primary'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN audioRelativePath TEXT NOT NULL DEFAULT 'Music/YT-DW'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN videoVolumeName TEXT NOT NULL DEFAULT 'external_primary'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN videoRelativePath TEXT NOT NULL DEFAULT 'Movies/YT-DW'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN parallelDownloads INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN concurrentFragmentDownloads INTEGER NOT NULL DEFAULT 4")
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "yt-dw.sqlite3",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
