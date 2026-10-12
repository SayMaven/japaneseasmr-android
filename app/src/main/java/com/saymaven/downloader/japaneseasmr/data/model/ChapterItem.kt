package com.saymaven.downloader.japaneseasmr.data.model

import org.json.JSONArray
import org.json.JSONObject

data class ChapterItem(
    val startMs: Long,
    val endMs: Long = 0L,
    val title: String
) {
    fun formatTime(): String {
        val totalSec = startMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
    }

    companion object {
        fun toJsonArray(chapters: List<ChapterItem>): String {
            val jsonArray = JSONArray()
            for (ch in chapters) {
                val obj = JSONObject()
                obj.put("startMs", ch.startMs)
                obj.put("endMs", ch.endMs)
                obj.put("title", ch.title)
                jsonArray.put(obj)
            }
            return jsonArray.toString()
        }

        fun fromJsonArray(jsonStr: String?): List<ChapterItem> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            val list = mutableListOf<ChapterItem>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val startMs = obj.optLong("startMs", 0L)
                    val endMs = obj.optLong("endMs", 0L)
                    val title = obj.optString("title", "Chapter ${i + 1}")
                    list.add(ChapterItem(startMs, endMs, title))
                }
            } catch (e: Exception) {
                // Return empty if parsing error
            }
            return list
        }
    }
}
