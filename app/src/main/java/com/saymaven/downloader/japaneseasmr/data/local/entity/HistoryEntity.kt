package com.saymaven.downloader.japaneseasmr.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_table")
data class HistoryEntity(
    @PrimaryKey val rjid: String,
    val title: String,
    val cv: String = "-",
    val circle: String = "-",
    val genre: String = "-",
    val ageRating: String = "-",
    val coverUrl: String,
    val localFilePath: String,
    val downloadDate: String,
    val fileSize: String
)
