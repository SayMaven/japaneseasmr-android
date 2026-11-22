package com.saymaven.downloader.japaneseasmr.util

import androidx.compose.ui.graphics.Color

object AgeRatingHelper {
    const val RATING_SAFE = "SAFE"
    const val RATING_R15 = "R-15"
    const val RATING_R18 = "R18"

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "-") return "-"
        val clean = raw.trim().uppercase()
        return when {
            clean.contains("18") || clean.contains("ADULT") -> RATING_R18
            clean.contains("15") -> RATING_R15
            clean.contains("ALL") || clean.contains("SAFE") || clean.contains("GENERAL") || clean.contains("全年齢") -> RATING_SAFE
            else -> clean
        }
    }

    fun getBadgeColor(rating: String): Color {
        return when (normalize(rating)) {
            RATING_R18 -> Color(0xFFD32F2F).copy(alpha = 0.88f)
            RATING_R15 -> Color(0xFFF57C00).copy(alpha = 0.88f)
            RATING_SAFE -> Color(0xFF2E7D32).copy(alpha = 0.88f)
            else -> Color(0xFF757575).copy(alpha = 0.85f)
        }
    }
}
