package com.saymaven.downloader.japaneseasmr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao
import com.saymaven.downloader.japaneseasmr.data.local.dao.PlaylistDao
import com.saymaven.downloader.japaneseasmr.data.local.dao.TranslationCacheDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.PlaylistItemEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.TranslationCacheEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.UserPlaylistEntity

@Database(
    entities = [
        HistoryEntity::class,
        TranslationCacheEntity::class,
        UserPlaylistEntity::class,
        PlaylistItemEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AsmrDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AsmrDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history_table ADD COLUMN chaptersJson TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history_table ADD COLUMN translatedTitle TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE history_table ADD COLUMN translatedChaptersJson TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `translation_cache` (" +
                    "`sourceText` TEXT NOT NULL, " +
                    "`targetLang` TEXT NOT NULL, " +
                    "`translatedText` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`sourceText`, `targetLang`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_translation_cache_targetLang` ON `translation_cache` (`targetLang`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history_table ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'DL_SITE'")
                db.execSQL("ALTER TABLE history_table ADD COLUMN linkedRjId TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_playlists` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_items` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`playlistId` INTEGER NOT NULL, " +
                    "`rjid` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`coverUrl` TEXT NOT NULL, " +
                    "`circle` TEXT NOT NULL, " +
                    "`cv` TEXT NOT NULL, " +
                    "`addedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`playlistId`) REFERENCES `user_playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId` ON `playlist_items` (`playlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_rjid` ON `playlist_items` (`rjid`)")
            }
        }

        fun getDatabase(context: Context): AsmrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AsmrDatabase::class.java,
                    "japaneseasmr_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
