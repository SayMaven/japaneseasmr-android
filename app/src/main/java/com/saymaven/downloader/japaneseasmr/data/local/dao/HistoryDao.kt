package com.saymaven.downloader.japaneseasmr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_table ORDER BY downloadDate DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_table ORDER BY downloadDate DESC")
    suspend fun getAllHistoryDirect(): List<HistoryEntity>

    @Query("SELECT * FROM history_table WHERE rjid = :rjid LIMIT 1")
    suspend fun getHistoryById(rjid: String): HistoryEntity?

    @Query("SELECT * FROM history_table WHERE rjid LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR cv LIKE '%' || :query || '%' OR circle LIKE '%' || :query || '%' ORDER BY downloadDate DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity)

    @Delete
    suspend fun deleteHistory(item: HistoryEntity)

    @Query("DELETE FROM history_table WHERE rjid = :rjid")
    suspend fun deleteById(rjid: String)

    @Query("UPDATE history_table SET chaptersJson = :chaptersJson WHERE rjid = :rjid")
    suspend fun updateChapters(rjid: String, chaptersJson: String?)

    @Query("UPDATE history_table SET translatedTitle = :translatedTitle, translatedChaptersJson = :translatedChaptersJson WHERE rjid = :rjid")
    suspend fun updateTranslations(rjid: String, translatedTitle: String?, translatedChaptersJson: String?)

    @Query("DELETE FROM history_table")
    suspend fun clearAll()
}
