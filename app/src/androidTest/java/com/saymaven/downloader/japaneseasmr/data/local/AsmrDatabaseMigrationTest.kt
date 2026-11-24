package com.saymaven.downloader.japaneseasmr.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saymaven.downloader.japaneseasmr.data.local.entity.UserPlaylistEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AsmrDatabaseMigrationTest {

    private val testDbName = "migration-test-asmr.db"
    private val schemaDbName = "schema-test-asmr.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AsmrDatabase::class.java
    )

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDbName)
        context.deleteDatabase(schemaDbName)
    }

    @Test
    fun createV6Database_matchesExportedSchema() {
        helper.createDatabase(schemaDbName, 6).apply {
            close()
        }
    }

    @Test
    fun migrate1To6_preservesHistoryAndCreatesNewTables() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDbName)

        // Create a v1 SQLite database manually with initial columns
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `history_table` (" +
                            "`rjid` TEXT NOT NULL PRIMARY KEY, " +
                            "`title` TEXT NOT NULL, " +
                            "`cv` TEXT NOT NULL, " +
                            "`circle` TEXT NOT NULL, " +
                            "`genre` TEXT NOT NULL, " +
                            "`ageRating` TEXT NOT NULL, " +
                            "`coverUrl` TEXT NOT NULL, " +
                            "`localFilePath` TEXT NOT NULL, " +
                            "`downloadDate` TEXT NOT NULL, " +
                            "`fileSize` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO `history_table` VALUES (" +
                            "'RJ01234567', 'Sample Title', 'CV', 'Circle', 'ASMR', 'R18', " +
                            "'https://example.com/cover.jpg', '/tmp/a', '2026-01-01', '100 MB')"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, AsmrDatabase::class.java, testDbName)
            .addMigrations(
                AsmrDatabase.MIGRATION_1_2,
                AsmrDatabase.MIGRATION_2_3,
                AsmrDatabase.MIGRATION_3_4,
                AsmrDatabase.MIGRATION_4_5,
                AsmrDatabase.MIGRATION_5_6
            )
            .build()

        runBlocking {
            val history = migratedDb.historyDao().getHistoryById("RJ01234567")
            assertNotNull(history)
            assertEquals("Sample Title", history!!.title)
            assertEquals("DL_SITE", history.sourceType)

            val playlistId = migratedDb.playlistDao().insertPlaylist(
                UserPlaylistEntity(name = "Favorites", createdAt = 1_700_000_000_000L)
            )
            val playlists = migratedDb.playlistDao().getAllPlaylists().first()
            assertEquals(1, playlists.size)
            assertEquals(playlistId, playlists.first().id)
        }

        migratedDb.close()
    }
}
