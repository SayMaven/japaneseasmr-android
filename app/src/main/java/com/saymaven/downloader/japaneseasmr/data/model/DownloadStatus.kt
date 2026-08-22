package com.saymaven.downloader.japaneseasmr.data.model

enum class DownloadStatus {
    PENDING,
    FETCHING_METADATA,
    DOWNLOADING,
    PROCESSING, // Tagging ID3 & merging
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadQueueItem(
    val rjid: String,
    val title: String = "Memuat info...",
    val cv: String = "-",
    val circle: String = "-",
    val genre: String = "-",
    val ageRating: String = "-",
    val coverUrl: String = "https://pic.weeabo0.xyz/${rjid}_img_main.jpg",
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f,
    val statusText: String = "Menunggu antrean",
    val speed: String = "-",
    val eta: String = "-",
    val downloadedSize: String = "-",
    val totalSize: String = "-",
    val error: String? = null,
    val tracks: List<TrackInfo> = emptyList()
)
