package com.saymaven.downloader.japaneseasmr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AsmrDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AsmrDatabase? = null

        fun getDatabase(context: Context): AsmrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AsmrDatabase::class.java,
                    "japaneseasmr_database.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
