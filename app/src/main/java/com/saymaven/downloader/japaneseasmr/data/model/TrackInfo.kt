package com.saymaven.downloader.japaneseasmr.data.model

data class TrackInfo(
    val name: String,
    val url: String,
    val isHls: Boolean = url.endsWith(".m3u8", ignoreCase = true),
    val duration: Double = 0.0,
    val referer: String = "https://japaneseasmr.com",
    val source: String = "japaneseasmr"
)
