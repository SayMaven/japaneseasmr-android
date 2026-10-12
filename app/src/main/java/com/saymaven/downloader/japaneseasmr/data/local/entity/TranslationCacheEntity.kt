package com.saymaven.downloader.japaneseasmr.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Cache terjemahan persisten di SQLite Room.
 * Menyimpan pemetaan teks bahasa Jepang asli ke bahasa target (ID / EN)
 * sehingga judul karya dan bab tracklist terbuka instan (0ms) tanpa memanggil Google Translate ulang.
 */
@Entity(
    tableName = "translation_cache",
    primaryKeys = ["sourceText", "targetLang"],
    indices = [Index(value = ["targetLang"])]
)
data class TranslationCacheEntity(
    val sourceText: String,
    val targetLang: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
)
