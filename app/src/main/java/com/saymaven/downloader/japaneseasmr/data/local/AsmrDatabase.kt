package com.saymaven.downloader.japaneseasmr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao
import com.saymaven.downloader.japaneseasmr.data.local.dao.TranslationCacheDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.TranslationCacheEntity

@Database(entities = [HistoryEntity::class, TranslationCacheEntity::class], version = 4, exportSchema = false)
abstract class AsmrDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun translationCacheDao(): TranslationCacheDao

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

        fun getDatabase(context: Context): AsmrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AsmrDatabase::class.java,
                    "japaneseasmr_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
