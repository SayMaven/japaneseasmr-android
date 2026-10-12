package com.saymaven.downloader.japaneseasmr.data.remote

import com.saymaven.downloader.japaneseasmr.data.model.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object TrackDiscoveryService {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val REFERER_JAPANESEASMR = "https://japaneseasmr.com"
    private const val REFERER_ASMRONE = "https://asmr.one/"

    private val ASMRONE_HEADERS = mapOf(
        "User-Agent" to USER_AGENT,
        "Referer" to REFERER_ASMRONE,
        "Origin" to "https://asmr.one",
        "Accept-Language" to "zh-CN,zh;q=0.9,ja;q=0.8,en;q=0.7"
    )

    private val AUDIO_EXTENSIONS = setOf(".mp3", ".wav", ".m4a", ".flac", ".aac", ".ogg")

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun checkUrlExists(
        url: String,
        referer: String = REFERER_JAPANESEASMR,
        customHeaders: Map<String, String>? = null
    ): Boolean {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Referer", referer)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-10")

            customHeaders?.forEach { (k, v) ->
                reqBuilder.header(k, v)
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                response.code in 200..206
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isPrologueName(name: String): Boolean {
        val low = name.lowercase()
        return low.contains("プロローグ") || low.contains("prologue") || low.contains("オープニング") ||
                low.contains("はじめに") || low.matches(Regex("^(?:op|intro)\\b.*", RegexOption.IGNORE_CASE))
    }

    private fun isEpilogueName(name: String): Boolean {
        val low = name.lowercase()
        return low.contains("エピローグ") || low.contains("epilogue") || low.contains("おまけ") ||
                low.contains("omake") || low.contains("特典") || low.contains("bonus") ||
                low.contains("アフター") || low.contains("after") || low.contains("エンディング") ||
                low.matches(Regex("^(?:ed|outro)\\b.*", RegexOption.IGNORE_CASE))
    }

    private fun getTrackTier(name: String): Int {
        val normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
        if (isPrologueName(normalized)) return 0
        if (isEpilogueName(normalized)) return 2
        return 1
    }

    /**
     * Comparator untuk Natural Sorting cerdas:
     * 1. Tiering narasi: Prologue (0) -> Main chapters (1) -> Epilogue/Bonus (2)
     * 2. Normalisasi Unicode NFKC (mengubah angka full-width １,２,３... menjadi ASCII 1,2,3...)
     * 3. Pembandingan token angka numerik murni vs teks
     */
    val naturalOrderComparator = Comparator<String> { aRaw, bRaw ->
        val a = java.text.Normalizer.normalize(aRaw, java.text.Normalizer.Form.NFKC).trim()
        val b = java.text.Normalizer.normalize(bRaw, java.text.Normalizer.Form.NFKC).trim()

        val tierA = getTrackTier(a)
        val tierB = getTrackTier(b)
        if (tierA != tierB) {
            return@Comparator tierA.compareTo(tierB)
        }

        val re = Regex("(\\d+)|(\\D+)")
        val tokensA = re.findAll(a).map { it.value }.iterator()
        val tokensB = re.findAll(b).map { it.value }.iterator()
        while (tokensA.hasNext() && tokensB.hasNext()) {
            val tA = tokensA.next()
            val tB = tokensB.next()
            val numA = tA.toLongOrNull()
            val numB = tB.toLongOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else if (numA != null) {
                -1 // Angka selalu mendahului teks
            } else if (numB != null) {
                1
            } else {
                tA.compareTo(tB, ignoreCase = true)
            }
            if (cmp != 0) return@Comparator cmp
        }
        a.length.compareTo(b.length)
    }

    suspend fun discoverAllTracks(rjid: String, preferredFormat: String = "auto"): List<TrackInfo> = withContext(Dispatchers.IO) {
        val cleanId = rjid.trim()
        if (cleanId.startsWith("http://") || cleanId.startsWith("https://")) {
            return@withContext listOf(
                TrackInfo(
                    name = "Track 1",
                    url = cleanId,
                    isHls = cleanId.contains(".m3u8", ignoreCase = true),
                    source = "direct"
                )
            )
        }

        // =========================================================================
        // 1. TAHAP 1: PROBING SUMBER UTAMA (JapaneseASMR weeab0o)
        // =========================================================================
        val jasmrTracks = probeJapaneseAsmrTracks(cleanId, preferredFormat)
        if (jasmrTracks.isNotEmpty()) {
            return@withContext jasmrTracks
        }

        // =========================================================================
        // 2. TAHAP 2: FALLBACK KE SUMBER KEDUA (asmr.one API via Smart Filter)
        // =========================================================================
        val numericId = cleanId.replace(Regex("^[A-Za-z]+"), "")
        if (numericId.isNotBlank()) {
            val asmrOneTree = fetchAsmrOneTree(numericId)
            if (asmrOneTree != null && asmrOneTree.length() > 0) {
                val filteredTracks = filterSmartAsmrOneTracks(asmrOneTree, preferredFormat)
                if (filteredTracks.isNotEmpty()) {
                    return@withContext filteredTracks
                }
            }
        }

        // =========================================================================
        // 3. FALLBACK TERAKHIR: Single candidate Track 1
        // =========================================================================
        listOf(
            TrackInfo(
                name = "Track 1",
                url = "https://v.weeab0o.xyz/${cleanId}.mp3",
                isHls = false,
                referer = REFERER_JAPANESEASMR,
                source = "japaneseasmr"
            )
        )
    }

    /**
     * Memeriksa keberadaan stream dan multi-track pada JapaneseASMR (v.weeab0o.xyz).
     * Jika preferredFormat adalah "mp3", stream MP3 diprioritaskan sebelum fallback ke HLS .m3u8.
     */
    private suspend fun probeJapaneseAsmrTracks(cleanId: String, preferredFormat: String = "auto"): List<TrackInfo> = coroutineScope {
        val pref = preferredFormat.lowercase()
        val m3u8Url = "https://v.weeab0o.xyz/${cleanId}.m3u8"

        suspend fun probeMp3Streams(): List<TrackInfo> {
            val foundTracks = mutableListOf<TrackInfo>()
            val seenUrls = mutableSetOf<String>()

            val t1Candidates = listOf(
                "https://v.weeab0o.xyz/${cleanId}.mp3",
                "https://v.weeab0o.xyz/${cleanId}%201.mp3",
                "https://v.weeab0o.xyz/${cleanId}_1.mp3",
                "https://v.weeab0o.xyz/${cleanId}-1.mp3"
            )

            var t1Url: String? = null
            for (url in t1Candidates) {
                if (checkUrlExists(url)) {
                    t1Url = url
                    break
                }
            }

            if (t1Url != null) {
                foundTracks.add(
                    TrackInfo(
                        name = "Track 1",
                        url = t1Url,
                        isHls = false,
                        referer = REFERER_JAPANESEASMR,
                        source = "japaneseasmr"
                    )
                )
                seenUrls.add(t1Url)

                // Cek Multi-track 2 s/d 20 secara paralel
                val trackJobs = (2..20).map { num ->
                    async {
                        val candidates = listOf(
                            "https://v.weeab0o.xyz/${cleanId}%20${num}.mp3",
                            "https://v.weeab0o.xyz/${cleanId}_${num}.mp3",
                            "https://v.weeab0o.xyz/${cleanId}-${num}.mp3",
                            "https://v.weeab0o.xyz/${cleanId}${num}.mp3"
                        )
                        var matched: String? = null
                        for (c in candidates) {
                            if (checkUrlExists(c)) {
                                matched = c
                                break
                            }
                        }
                        if (matched != null) {
                            TrackInfo(
                                name = "Track $num",
                                url = matched,
                                isHls = false,
                                referer = REFERER_JAPANESEASMR,
                                source = "japaneseasmr"
                            )
                        } else null
                    }
                }

                val numberedResults = trackJobs.awaitAll().filterNotNull()
                foundTracks.addAll(numberedResults)
                numberedResults.forEach { seenUrls.add(it.url) }

                // Cek Omake, Bonus, Tokuten, EX secara paralel
                val omakePatterns = listOf(
                    "Omake" to "https://v.weeab0o.xyz/${cleanId}omake.mp3",
                    "Omake" to "https://v.weeab0o.xyz/${cleanId}%20omake.mp3",
                    "Omake" to "https://v.weeab0o.xyz/${cleanId}_omake.mp3",
                    "Omake" to "https://v.weeab0o.xyz/${cleanId}-omake.mp3",
                    "Omake 1" to "https://v.weeab0o.xyz/${cleanId}omake1.mp3",
                    "Omake 1" to "https://v.weeab0o.xyz/${cleanId}%20omake%201.mp3",
                    "Omake 2" to "https://v.weeab0o.xyz/${cleanId}omake2.mp3",
                    "Omake 2" to "https://v.weeab0o.xyz/${cleanId}%20omake%202.mp3",
                    "Bonus" to "https://v.weeab0o.xyz/${cleanId}bonus.mp3",
                    "Bonus" to "https://v.weeab0o.xyz/${cleanId}%20bonus.mp3",
                    "Bonus" to "https://v.weeab0o.xyz/${cleanId}_bonus.mp3",
                    "Tokuten" to "https://v.weeab0o.xyz/${cleanId}tokuten.mp3",
                    "Tokuten" to "https://v.weeab0o.xyz/${cleanId}%20tokuten.mp3",
                    "EX" to "https://v.weeab0o.xyz/${cleanId}%20ex.mp3",
                    "EX" to "https://v.weeab0o.xyz/${cleanId}ex.mp3",
                    "EX" to "https://v.weeab0o.xyz/${cleanId}_ex.mp3",
                    "EX" to "https://v.weeab0o.xyz/${cleanId}-ex.mp3",
                    "EX 1" to "https://v.weeab0o.xyz/${cleanId}%20ex%201.mp3",
                    "EX 1" to "https://v.weeab0o.xyz/${cleanId}%20ex1.mp3",
                    "EX 2" to "https://v.weeab0o.xyz/${cleanId}%20ex%202.mp3",
                    "EX 2" to "https://v.weeab0o.xyz/${cleanId}%20ex2.mp3"
                )

                val omakeJobs = omakePatterns.map { (name, url) ->
                    async {
                        if (!seenUrls.contains(url) && checkUrlExists(url)) {
                            TrackInfo(
                                name = name,
                                url = url,
                                isHls = false,
                                referer = REFERER_JAPANESEASMR,
                                source = "japaneseasmr"
                            )
                        } else null
                    }
                }

                val omakeResults = omakeJobs.awaitAll().filterNotNull()
                omakeResults.forEach { t ->
                    if (!seenUrls.contains(t.url)) {
                        foundTracks.add(t)
                        seenUrls.add(t.url)
                    }
                }
            }

            return foundTracks
        }

        // 1. Jika preferensi MP3: cari MP3 terlebih dahulu
        if (pref == "mp3") {
            val mp3Tracks = probeMp3Streams()
            if (mp3Tracks.isNotEmpty()) {
                return@coroutineScope mp3Tracks
            }
        }

        // 2. Cek Master HLS (.m3u8) jika bukan preferensi MP3 (atau fallback jika MP3 tidak ada)
        if (checkUrlExists(m3u8Url)) {
            return@coroutineScope listOf(
                TrackInfo(
                    name = "Track 1",
                    url = m3u8Url,
                    isHls = true,
                    referer = REFERER_JAPANESEASMR,
                    source = "japaneseasmr"
                )
            )
        }

        // 3. Fallback jika preferensi bukan MP3 namun hanya ada file MP3
        if (pref != "mp3") {
            return@coroutineScope probeMp3Streams()
        }

        emptyList()
    }

    /**
     * Mengambil pohon berkas audio dari API asmr.one dengan header bypass WAF.
     */
    suspend fun fetchAsmrOneTree(numericId: String): JSONArray? = withContext(Dispatchers.IO) {
        val url = "https://api.asmr.one/api/tracks/$numericId"
        try {
            val reqBuilder = Request.Builder().url(url)
            ASMRONE_HEADERS.forEach { (k, v) -> reqBuilder.header(k, v) }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: return@withContext null
                    return@withContext JSONArray(bodyStr)
                }
            }
        } catch (e: Exception) {
            // Gagal request API asmr.one
        }
        null
    }

    /**
     * Smart Track Filter Heuristics:
     * Menyaring folder asmr.one hanya untuk cerita utama (本編) dengan eliminasi varian efek dan soundbite.
     */
    fun filterSmartAsmrOneTracks(tree: JSONArray, preferredFormat: String = "auto"): List<TrackInfo> {
        val rootAudios = mutableListOf<TrackInfo>()
        val pref = preferredFormat.lowercase()

        // 1. Kumpulkan audio yang berada di root level
        for (i in 0 until tree.length()) {
            val item = tree.optJSONObject(i) ?: continue
            if (item.optString("type") == "audio") {
                val streamUrl = item.optString("mediaDownloadUrl").ifBlank { item.optString("mediaStreamUrl") }
                val title = item.optString("title")
                if (streamUrl.isNotBlank() && isAudioFile(title)) {
                    val cleanName = title.replace(Regex("\\.[a-zA-Z0-9]+$"), "")
                    rootAudios.add(
                        TrackInfo(
                            name = cleanName,
                            url = streamUrl,
                            isHls = streamUrl.contains(".m3u8", ignoreCase = true),
                            duration = item.optDouble("duration", 0.0),
                            referer = REFERER_ASMRONE,
                            source = "asmr.one"
                        )
                    )
                }
            }
        }

        // Cek pola penomoran berurutan di root (contoh: 01, 02, ...)
        val numberedRoot = rootAudios.filter { it.name.matches(Regex("^\\s*0?\\d+.*")) }
        if (numberedRoot.size >= 2 || (rootAudios.isNotEmpty() && numberedRoot.size == rootAudios.size)) {
            // Jika root audios seluruhnya berupa WAV/lossless berukuran gigantis, cek apakah ada folder MP3/M4A resmi
            val allLossless = numberedRoot.all { it.name.endsWith(".wav", ignoreCase = true) || it.name.endsWith(".flac", ignoreCase = true) }
            if (allLossless && pref != "flac" && pref != "wav") {
                for (i in 0 until tree.length()) {
                    val item = tree.optJSONObject(i) ?: continue
                    if (item.optString("type") == "folder") {
                        val fTitle = item.optString("title")
                        if (fTitle.contains("本編") && (fTitle.contains("mp3", ignoreCase = true) || fTitle.contains("m4a", ignoreCase = true))) {
                            val mp3Tracks = extractAudiosFromNode(item).filter { it.url.contains(".mp3", ignoreCase = true) || it.url.contains(".m4a", ignoreCase = true) }
                            if (mp3Tracks.size >= 2) {
                                return mp3Tracks.sortedWith { t1, t2 -> naturalOrderComparator.compare(t1.name, t2.name) }
                            }
                        }
                    }
                }
            }

            return numberedRoot.sortedWith { t1, t2 -> naturalOrderComparator.compare(t1.name, t2.name) }
        }

        // 2. Skoring folder jika karya terbagi dalam subdirektori
        val folders = mutableListOf<JSONObject>()
        for (i in 0 until tree.length()) {
            val item = tree.optJSONObject(i) ?: continue
            if (item.optString("type") == "folder") {
                folders.add(item)
            }
        }

        fun folderScore(fTitle: String): Int {
            var score = 0
            val lower = fTitle.lowercase()
            if (fTitle.contains("本編") || lower.contains("main") || lower.contains("track")) {
                score += 100
            }
            if (fTitle.contains("おすすめ") || fTitle.contains("お勧め") || lower.contains("seあり") || lower.contains("se有り") || 
                lower.contains("効果音あり") || lower.contains("効果音有り") || (fTitle.contains("効果音") && !fTitle.contains("効果音なし") && !fTitle.contains("効果音無")) || fTitle.contains("ボイス")) {
                score += 50
            }
            if (pref == "flac" || pref == "wav") {
                if (lower.contains("wav") || lower.contains("flac")) score += 30
            } else {
                if (lower.contains("mp3")) score += 30
            }

            // Penalti varian tanpa efek suara (voice-only)
            val noSePenalties = listOf("se無し", "se無", "seなし", "nose", "効果音なし", "効果音無", "音声原本", "原本")
            if (noSePenalties.any { lower.contains(it) }) {
                score -= 80
            }

            // Penalti sound effect / BGM murni jika bukan bagian dari cerita utama (本編)
            val sfxPenalties = listOf("システムボイス", "システム音", "昭和音質", "木枯らし", "雨降り", "se強化")
            if (sfxPenalties.any { lower.contains(it) }) {
                score -= 80
            }
            if (!fTitle.contains("本編") && (lower.contains("効果音") || lower.contains("bgm"))) {
                score -= 80
            }
            if (fTitle.contains("おまけ") && !fTitle.contains("本編")) {
                score -= 20
            }
            return score
        }

        folders.sortByDescending { folderScore(it.optString("title")) }

        for (bestFolder in folders) {
            var targetNode = bestFolder
            while (true) {
                val children = targetNode.optJSONArray("children") ?: break
                val subFolders = mutableListOf<JSONObject>()
                for (c in 0 until children.length()) {
                    val child = children.optJSONObject(c) ?: continue
                    if (child.optString("type") == "folder") {
                        subFolders.add(child)
                    }
                }
                if (subFolders.isEmpty()) break
                subFolders.sortByDescending { folderScore(it.optString("title")) }
                targetNode = subFolders[0]
            }

            val tracks = extractAudiosFromNode(targetNode)
            if (tracks.isNotEmpty()) {
                // Deduplikasi format: jika ada format MP3 dan WAV untuk nama yang sama, pilih MP3
                val seenBases = mutableMapOf<String, TrackInfo>()
                val filtered = mutableListOf<TrackInfo>()

                for (tr in tracks) {
                    val rawBase = tr.name.lowercase().replace(Regex("_(?:se|nose)$"), "")
                    val existing = seenBases[rawBase]
                    if (existing == null) {
                        seenBases[rawBase] = tr
                        filtered.add(tr)
                    } else {
                        val trIsPref = when (pref) {
                            "flac", "wav" -> tr.url.contains(".flac", ignoreCase = true) || tr.url.contains(".wav", ignoreCase = true)
                            "m4a" -> tr.url.contains(".m4a", ignoreCase = true)
                            "mp3" -> tr.url.contains(".mp3", ignoreCase = true)
                            else -> tr.url.contains(".mp3", ignoreCase = true) || tr.url.contains(".m4a", ignoreCase = true)
                        }
                        val exIsPref = when (pref) {
                            "flac", "wav" -> existing.url.contains(".flac", ignoreCase = true) || existing.url.contains(".wav", ignoreCase = true)
                            "m4a" -> existing.url.contains(".m4a", ignoreCase = true)
                            "mp3" -> existing.url.contains(".mp3", ignoreCase = true)
                            else -> existing.url.contains(".mp3", ignoreCase = true) || existing.url.contains(".m4a", ignoreCase = true)
                        }
                        if (trIsPref && !exIsPref) {
                            filtered.remove(existing)
                            seenBases[rawBase] = tr
                            filtered.add(tr)
                        }
                    }
                }

                return filtered.sortedWith { t1, t2 -> naturalOrderComparator.compare(t1.name, t2.name) }
            }
        }

        // Fallback terakhir: Ambil semua audio unik dari seluruh pohon
        val allAudios = mutableListOf<TrackInfo>()
        for (i in 0 until tree.length()) {
            val item = tree.optJSONObject(i) ?: continue
            allAudios.addAll(extractAudiosFromNode(item))
        }

        val uniqueAudios = mutableListOf<TrackInfo>()
        val seen = mutableSetOf<String>()
        for (a in allAudios) {
            val key = a.name.lowercase()
            if (!seen.contains(key)) {
                seen.add(key)
                uniqueAudios.add(a)
            }
        }

        return uniqueAudios.sortedWith { t1, t2 -> naturalOrderComparator.compare(t1.name, t2.name) }
    }

    private fun extractAudiosFromNode(node: JSONObject): List<TrackInfo> {
        val audios = mutableListOf<TrackInfo>()
        if (node.optString("type") == "audio") {
            val streamUrl = node.optString("mediaDownloadUrl").ifBlank { node.optString("mediaStreamUrl") }
            val title = node.optString("title")
            if (streamUrl.isNotBlank() && isAudioFile(title)) {
                val cleanName = title.replace(Regex("\\.[a-zA-Z0-9]+$"), "")
                audios.add(
                    TrackInfo(
                        name = cleanName,
                        url = streamUrl,
                        isHls = streamUrl.contains(".m3u8", ignoreCase = true),
                        duration = node.optDouble("duration", 0.0),
                        referer = REFERER_ASMRONE,
                        source = "asmr.one"
                    )
                )
            }
        }

        val children = node.optJSONArray("children")
        if (children != null) {
            for (i in 0 until children.length()) {
                val child = children.optJSONObject(i) ?: continue
                audios.addAll(extractAudiosFromNode(child))
            }
        }

        return audios
    }

    private fun isAudioFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return AUDIO_EXTENSIONS.any { lower.endsWith(it) }
    }
}
