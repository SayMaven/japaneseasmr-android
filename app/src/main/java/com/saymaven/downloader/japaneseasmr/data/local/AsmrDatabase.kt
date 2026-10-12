package com.saymaven.downloader.japaneseasmr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity

@Database(entities = [HistoryEntity::class], version = 2, exportSchema = false)
abstract class AsmrDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AsmrDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history_table ADD COLUMN chaptersJson TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AsmrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AsmrDatabase::class.java,
                    "japaneseasmr_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
