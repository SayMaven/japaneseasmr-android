package com.saymaven.downloader.japaneseasmr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saymaven.downloader.japaneseasmr.data.local.entity.TranslationCacheEntity

@Dao
interface TranslationCacheDao {

    @Query("SELECT translatedText FROM translation_cache WHERE sourceText = :sourceText AND targetLang = :targetLang LIMIT 1")
    suspend fun getTranslation(sourceText: String, targetLang: String): String?

    @Query("SELECT * FROM translation_cache WHERE targetLang = :targetLang AND sourceText IN (:sources)")
    suspend fun getTranslations(sources: List<String>, targetLang: String): List<TranslationCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(item: TranslationCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslations(items: List<TranslationCacheEntity>)

    @Query("DELETE FROM translation_cache WHERE targetLang = :targetLang")
    suspend fun clearByLang(targetLang: String)

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun getCacheCount(): Int
}
