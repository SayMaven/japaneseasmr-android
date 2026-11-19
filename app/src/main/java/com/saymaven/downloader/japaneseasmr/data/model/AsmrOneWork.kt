package com.saymaven.downloader.japaneseasmr.data.model

/**
 * Data model for works obtained from asmr.one API catalog.
 * Follows the data structure and schema of KikoFlu's Work model.
 */
data class AsmrOneWork(
    val id: Int,
    val rjid: String,
    val title: String,
    val circle: String = "-",
    val cv: String = "-",
    val coverUrl: String,
    val releaseDate: String? = null,
    val hasSubtitle: Boolean = false,
    val ageRating: String = "ALL",
    val price: Int? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val dlCount: Int? = null,
    val tags: List<String> = emptyList()
)
