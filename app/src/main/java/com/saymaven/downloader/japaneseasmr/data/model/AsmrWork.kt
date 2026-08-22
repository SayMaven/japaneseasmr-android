package com.saymaven.downloader.japaneseasmr.data.model

data class AsmrWork(
    val rjid: String,
    val title: String,
    val cv: String = "-",
    val circle: String = "-",
    val genre: String = "-",
    val ageRating: String = "-",
    val coverUrl: String = "https://pic.weeabo0.xyz/${rjid}_img_main.jpg",
    val tracks: List<TrackInfo> = emptyList()
)
