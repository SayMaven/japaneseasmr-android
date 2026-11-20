package com.saymaven.downloader.japaneseasmr.service.lyrics

/**
 * Single parsed subtitle/lyric line with millisecond timing.
 */
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val translatedText: String? = null
)

/**
 * Represents a discovered subtitle file (either local File path or remote HTTP URL).
 */
data class SubtitleSource(
    val title: String,
    val pathOrUrl: String,
    val format: String,
    val isRemote: Boolean
)

/**
 * Multi-format subtitle/lyrics parser supporting WebVTT (.vtt), LRC (.lrc), and SubRip (.srt).
 */
object LyricsParser {

    private val VTT_SRT_TIME_LINE = Regex(
        """(\d{1,2}:)?(\d{1,2}):(\d{2})[.,](\d{2,3})\s*-->\s*(\d{1,2}:)?(\d{1,2}):(\d{2})[.,](\d{2,3})"""
    )
    private val LRC_TIME_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")

    fun parse(rawContent: String, formatHint: String = ""): List<LyricLine> {
        val cleaned = rawContent.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        val hint = formatHint.lowercase().trim()
        return when {
            hint == "vtt" || cleaned.trimStart().startsWith("WEBVTT", ignoreCase = true) -> parseVttOrSrt(cleaned)
            hint == "srt" -> parseVttOrSrt(cleaned)
            hint == "lrc" || LRC_TIME_TAG.containsMatchIn(cleaned) -> parseLrc(cleaned)
            VTT_SRT_TIME_LINE.containsMatchIn(cleaned) -> parseVttOrSrt(cleaned)
            else -> emptyList()
        }
    }

    private fun parseVttOrSrt(content: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val blocks = content.split(Regex("\n\\s*\n"))

        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            // Skip WEBVTT header / NOTE / STYLE blocks
            val first = lines.first().trim()
            if (first.startsWith("WEBVTT", ignoreCase = true) ||
                first.startsWith("NOTE", ignoreCase = true) ||
                first.startsWith("STYLE", ignoreCase = true)
            ) {
                continue
            }

            val timingIdx = lines.indexOfFirst { VTT_SRT_TIME_LINE.containsMatchIn(it) }
            if (timingIdx < 0) continue

            val match = VTT_SRT_TIME_LINE.find(lines[timingIdx]) ?: continue
            val startMs = parseTimestampMs(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4]
            )
            val endMs = parseTimestampMs(
                match.groupValues[5],
                match.groupValues[6],
                match.groupValues[7],
                match.groupValues[8]
            )

            val textBody = lines.drop(timingIdx + 1)
                .joinToString("\n") { HTML_TAG_REGEX.replace(it, "").trim() }
                .trim()

            if (textBody.isNotEmpty() && endMs >= startMs) {
                result.add(LyricLine(startMs = startMs, endMs = endMs, text = textBody))
            }
        }
        return result.sortedBy { it.startMs }
    }

    private fun parseLrc(content: String): List<LyricLine> {
        val rawEntries = mutableListOf<Pair<Long, String>>()
        for (rawLine in content.lines()) {
            val matches = LRC_TIME_TAG.findAll(rawLine).toList()
            if (matches.isEmpty()) continue
            val text = LRC_TIME_TAG.replace(rawLine, "").trim()
            if (text.isEmpty()) continue

            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = m.groupValues[3]
                val ms = when (fracStr.length) {
                    0 -> 0L
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    else -> fracStr.take(3).toLongOrNull() ?: 0L
                }
                val startMs = (min * 60_000L) + (sec * 1000L) + ms
                rawEntries.add(startMs to text)
            }
        }

        val sorted = rawEntries.sortedBy { it.first }
        if (sorted.isEmpty()) return emptyList()

        return sorted.mapIndexed { idx, (startMs, text) ->
            val endMs = if (idx + 1 < sorted.size) {
                maxOf(startMs + 100L, sorted[idx + 1].first)
            } else {
                startMs + 5000L
            }
            LyricLine(startMs = startMs, endMs = endMs, text = text)
        }
    }

    private fun parseTimestampMs(hourGroup: String, minStr: String, secStr: String, msStr: String): Long {
        val hours = hourGroup.trimEnd(':').toLongOrNull() ?: 0L
        val mins = minStr.toLongOrNull() ?: 0L
        val secs = secStr.toLongOrNull() ?: 0L
        val ms = when (msStr.length) {
            1 -> (msStr.toLongOrNull() ?: 0L) * 100L
            2 -> (msStr.toLongOrNull() ?: 0L) * 10L
            else -> msStr.take(3).toLongOrNull() ?: 0L
        }
        return (hours * 3_600_000L) + (mins * 60_000L) + (secs * 1000L) + ms
    }

    fun findActiveIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < 0L) return -1
        for (i in lines.indices) {
            val line = lines[i]
            if (positionMs in line.startMs..line.endMs) {
                return i
            }
        }
        // Fallback: latest line whose startMs <= positionMs
        val lastStarted = lines.indexOfLast { it.startMs <= positionMs }
        if (lastStarted >= 0 && positionMs - lines[lastStarted].endMs <= 1200L) {
            return lastStarted
        }
        return -1
    }
}
