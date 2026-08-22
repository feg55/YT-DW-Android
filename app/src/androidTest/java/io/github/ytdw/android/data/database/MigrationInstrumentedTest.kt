package io.github.ytdw.android.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {
    @Test fun migrationAddsMediaStoreColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.sqlite3"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE queue_items (id TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("CREATE TABLE download_history (id INTEGER NOT NULL PRIMARY KEY)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_1_2.migrate(db)
        fun columns(table: String): Set<String> = db.query("PRAGMA table_info($table)").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        assertTrue(columns("queue_items").containsAll(setOf("contentUri", "displayName")))
        assertTrue(columns("download_history").containsAll(setOf("contentUri", "displayName")))
        helper.close()
        context.deleteDatabase(name)
    }

    @Test fun migrationAddsOutputLocations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-output-test.sqlite3"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE app_settings (id INTEGER NOT NULL PRIMARY KEY)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_2_3.migrate(db)
        db.execSQL("INSERT INTO app_settings (id) VALUES (1)")
        val values = db.query(
            "SELECT audioVolumeName, audioRelativePath, videoVolumeName, videoRelativePath FROM app_settings",
        ).use { cursor ->
            check(cursor.moveToFirst())
            List(4) { cursor.getString(it) }
        }
        assertTrue(values == listOf("external_primary", "Music/YT-DW", "external_primary", "Movies/YT-DW"))
        helper.close()
        context.deleteDatabase(name)
    }

    @Test fun migrationAddsDownloadConcurrencySettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-concurrency-test.sqlite3"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE app_settings (id INTEGER NOT NULL PRIMARY KEY)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_3_4.migrate(db)
        db.execSQL("INSERT INTO app_settings (id) VALUES (1)")
        val values = db.query(
            "SELECT parallelDownloads, concurrentFragmentDownloads FROM app_settings",
        ).use { cursor ->
            check(cursor.moveToFirst())
            listOf(cursor.getInt(0), cursor.getInt(1))
        }
        assertTrue(values == listOf(2, 4))
        helper.close()
        context.deleteDatabase(name)
    }
}
