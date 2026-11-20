package com.saymaven.downloader.japaneseasmr.service.lyrics

import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import java.util.Locale

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

    // --- KikoFlu SubtitleMatcher Port ---
    private val SUBTITLE_EXTS = listOf(".vtt", ".srt", ".lrc", ".txt")
    private val AUDIO_EXTS = listOf(".mp3", ".wav", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wma", ".mp4", ".m4b")
    private val SE_SUFFIXES = listOf(
        "_se无", "_se有", "_seなし", "_se無し", "_se有り", "_seあり", "_se_off", "_nose", "_se",
        "se无", "se有", "seなし", "se無し", "se有り", "seあり", "se_off", "nose"
    )
    private val TRACK_NUM_REGEX = Regex("""(?:track|tr|トラック|#|no\.?)?\s*0*(\d+)""", RegexOption.IGNORE_CASE)

    fun foldFullWidthAscii(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val code = ch.code
            when {
                code == 0x3000 -> sb.append(' ')
                code in 0xFF01..0xFF5E -> sb.append((code - 0xFEE0).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun stripExtensions(fileName: String): String {
        var name = fileName.trim()
        for (ext in SUBTITLE_EXTS) {
            if (name.endsWith(ext, ignoreCase = true)) {
                name = name.substring(0, name.length - ext.length)
                break
            }
        }
        for (ext in AUDIO_EXTS) {
            if (name.endsWith(ext, ignoreCase = true)) {
                name = name.substring(0, name.length - ext.length)
                break
            }
        }
        return name.trim()
    }

    fun normalizeForMatching(baseName: String, stripBrackets: Boolean = true): String {
        var res = foldFullWidthAscii(baseName).lowercase()
        if (stripBrackets) {
            res = res.replace(Regex("""（.*?）"""), "")
                .replace(Regex("""\(.*?\)"""), "")
                .replace(Regex("""\[.*?]"""), "")
                .replace(Regex("""【.*?】"""), "")
        }
        for (suffix in SE_SUFFIXES) {
            res = res.replace(suffix, "")
        }
        return res.replace(Regex("""[^\w\u4e00-\u9fa5\u3040-\u309f\u30a0-\u30ff]"""), "").trim()
    }

    private fun extractLeadingOrTrackNumber(baseName: String): Int? {
        val folded = foldFullWidthAscii(baseName).trim()
        return TRACK_NUM_REGEX.find(folded)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val v0 = IntArray(s2.length + 1) { it }
        val v1 = IntArray(s2.length + 1)
        for (i in s1.indices) {
            v1[0] = i + 1
            for (j in s2.indices) {
                val cost = if (s1[i] == s2[j]) 0 else 1
                v1[j + 1] = minOf(v1[j] + 1, v0[j + 1] + 1, v0[j] + cost)
            }
            v1.copyInto(v0)
        }
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0 - (v0[s2.length].toDouble() / maxLen)
    }

    fun matchBestSubtitle(
        sources: List<SubtitleSource>,
        trackTitle: String,
        trackIndex: Int = -1
    ): SubtitleSource? {
        if (sources.isEmpty()) return null
        val audioBase = stripExtensions(trackTitle)
        val audioKeepBrackets = normalizeForMatching(audioBase, stripBrackets = false)
        val audioStripped = normalizeForMatching(audioBase, stripBrackets = true)
        val audioNum = extractLeadingOrTrackNumber(audioBase) ?: if (trackIndex >= 0) trackIndex + 1 else null

        var bestSource: SubtitleSource? = null
        var bestScore = Int.MIN_VALUE

        for (src in sources) {
            val subBase = stripExtensions(src.title)
            val subKeepBrackets = normalizeForMatching(subBase, stripBrackets = false)
            val subStripped = normalizeForMatching(subBase, stripBrackets = true)
            val subNum = extractLeadingOrTrackNumber(subBase)

            var score = 0
            when {
                audioKeepBrackets.isNotEmpty() && audioKeepBrackets == subKeepBrackets -> score += 1000
                audioStripped.isNotEmpty() && audioStripped == subStripped -> score += 850
                audioStripped.isNotEmpty() && subStripped.isNotEmpty() -> {
                    val sim = calculateSimilarity(audioStripped, subStripped)
                    if (sim >= 0.82) {
                        score += (sim * 800).toInt()
                    } else if (audioStripped.length > 3 && subStripped.length > 3 &&
                        (subStripped.contains(audioStripped) || audioStripped.contains(subStripped))
                    ) {
                        score += 650
                    }
                }
            }

            if (audioNum != null && subNum != null) {
                if (audioNum == subNum) score += 500 else score -= 600
            }

            score += when (src.format.lowercase()) {
                "vtt" -> 15
                "lrc" -> 10
                "srt" -> 5
                else -> 0
            }

            if (score > bestScore) {
                bestScore = score
                bestSource = src
            }
        }

        return when {
            bestScore >= 450 -> bestSource
            sources.size == 1 && trackIndex <= 0 && bestScore >= 0 -> sources.first()
            else -> null
        }
    }

    /**
     * Builds a single merged timeline of LyricLines across chapters (or single work)
     * with timestamps offset by each chapter's startMs so merged audio files play synchronized lyrics.
     */
    suspend fun buildMergedWorkLyrics(
        rjid: String,
        chapters: List<ChapterItem>,
        remoteSources: List<SubtitleSource>,
        fetchContent: suspend (SubtitleSource) -> String?
    ): List<LyricLine> {
        if (remoteSources.isEmpty()) return emptyList()
        if (chapters.size <= 1) {
            val best = matchBestSubtitle(remoteSources, chapters.firstOrNull()?.title ?: rjid, 0)
                ?: remoteSources.first()
            val raw = fetchContent(best) ?: return emptyList()
            return parse(raw, best.format)
        }

        val merged = mutableListOf<LyricLine>()
        chapters.forEachIndexed { idx, chap ->
            val matched = matchBestSubtitle(remoteSources, chap.title, idx) ?: return@forEachIndexed
            val raw = fetchContent(matched) ?: return@forEachIndexed
            val parsed = parse(raw, matched.format)
            for (line in parsed) {
                val shiftedStart = chap.startMs + line.startMs
                val rawEnd = chap.startMs + line.endMs
                val clampedEnd = if (chap.endMs > chap.startMs) minOf(rawEnd, chap.endMs) else rawEnd
                if (clampedEnd >= shiftedStart) {
                    merged.add(line.copy(startMs = shiftedStart, endMs = clampedEnd))
                }
            }
        }
        return merged.sortedBy { it.startMs }
    }

    fun toWebVtt(lines: List<LyricLine>): String {
        val sb = StringBuilder("WEBVTT\n\n")
        for ((idx, line) in lines.withIndex()) {
            sb.append(idx + 1).append('\n')
            sb.append(formatVttTime(line.startMs))
                .append(" --> ")
                .append(formatVttTime(line.endMs))
                .append('\n')
            sb.append(line.text).append("\n\n")
        }
        return sb.toString()
    }

    private fun formatVttTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val mins = (safe % 3_600_000L) / 60_000L
        val secs = (safe % 60_000L) / 1000L
        val millis = safe % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, mins, secs, millis)
    }
}
