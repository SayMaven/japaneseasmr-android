package com.saymaven.downloader.japaneseasmr.data.model

data class TrackInfo(
    val name: String,
    val url: String,
    val isHls: Boolean = url.endsWith(".m3u8", ignoreCase = true)
)
