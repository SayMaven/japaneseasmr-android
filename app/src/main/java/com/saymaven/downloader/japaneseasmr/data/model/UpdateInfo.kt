package com.saymaven.downloader.japaneseasmr.data.model

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val isDebugAsset: Boolean,
    val publishedAt: String
)

