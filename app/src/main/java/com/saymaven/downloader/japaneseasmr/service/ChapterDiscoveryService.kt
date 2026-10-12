package com.saymaven.downloader.japaneseasmr.service

import android.media.MediaMetadataRetriever
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import com.saymaven.downloader.japaneseasmr.data.remote.TrackDiscoveryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs

object ChapterDiscoveryService {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val REFERER_JASMR = "https://japaneseasmr.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getAudioDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { mmr.release() } catch (e: Exception) {}
        }
    }

    /**
     * Mengambil daftar penanda bab (chapters / tracklist) langsung dari situs japaneseasmr.com:
     * 1. Cari postingan di japaneseasmr.com/?s={cleanId}
     * 2. Ekstrak tabel .chapter_list (start_time & chapter_title)
     * 3. Fallback pemindaian teks timestamp jika tabel HTML tidak ada.
     */
    suspend fun fetchJapaneseAsmrChapters(rjIdOrUrl: String): List<ChapterItem> = withContext(Dispatchers.IO) {
        val cleanTarget = rjIdOrUrl.trim()
        if (cleanTarget.isBlank()) return@withContext emptyList()

        var targetUrl: String? = null
        if (cleanTarget.startsWith("http://") || cleanTarget.startsWith("https://")) {
            targetUrl = cleanTarget
        } else {
            val cleanId = cleanTarget.uppercase()
            val searchUrl = "https://japaneseasmr.com/?s=$cleanId"
            try {
                val req = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER_JASMR)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val sHtml = resp.body?.string() ?: ""
                        val doc = Jsoup.parse(sHtml)
                        val entries = doc.select(".entry-title, .post-title, article")
                        for (ent in entries) {
                            val a = ent.select("a").first()
                            val href = a?.attr("href") ?: ""
                            if (href.isNotBlank()) {
                                if (ent.text().uppercase().contains(cleanId) || targetUrl == null) {
                                    targetUrl = href
                                    if (ent.text().uppercase().contains(cleanId)) break
                                }
                            }
                        }
                        if (targetUrl == null) {
                            val matcher = Pattern.compile("https://japaneseasmr\\.com/\\d+/").matcher(sHtml)
                            if (matcher.find()) {
                                targetUrl = matcher.group()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                return@withContext emptyList()
            }
        }

        if (targetUrl == null) return@withContext emptyList()

        val rawChapters = mutableListOf<Pair<Long, String>>()
        val seen = mutableSetOf<String>()

        try {
            val req = Request.Builder()
                .url(targetUrl!!)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER_JASMR)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val pHtml = resp.body?.string() ?: return@withContext emptyList()
                val doc = Jsoup.parse(pHtml)

                // 1. Cek tabel .chapter_list resmi
                val rows = doc.select("tr")
                for (tr in rows) {
                    val timeTd = tr.select("td[class*=start_time]").first()
                    val titleTd = tr.select("td[class*=chapter_title]").first()
                    if (timeTd != null && titleTd != null) {
                        var secVal: Double? = null
                        val aVal = timeTd.select("a[data-value]").first()
                        if (aVal != null) {
                            secVal = aVal.attr("data-value").toDoubleOrNull()
                        }
                        if (secVal == null) {
                            secVal = parseTimeStringToSeconds(timeTd.text())
                        }

                        var titleText = titleTd.select("a[data-track-title]").first()?.attr("data-track-title")?.trim() ?: ""
                        if (titleText.isBlank()) {
                            titleText = titleTd.text().trim()
                        }

                        if (secVal != null && titleText.isNotBlank()) {
                            val msVal = (secVal * 1000.0).toLong()
                            if (seen.add("$msVal-$titleText")) {
                                rawChapters.add(Pair(msVal, titleText))
                            }
                        }
                    }
                }

                // 2. Cek list .work_tracklist (contoh: pada pos dengan ul/li work_tracklist_item)
                if (rawChapters.isEmpty()) {
                    val trackItems = doc.select(".work_tracklist_item, .work_tracklist li")
                    if (trackItems.isNotEmpty()) {
                        var durMsAcc = 0L
                        val seenTitles = mutableSetOf<String>()
                        for (item in trackItems) {
                            val titleEl = item.select(".title").first() ?: item.select("div").first()
                            val timeEl = item.select(".time").first() ?: item.select("div").last()
                            val tName = titleEl?.text()?.trim() ?: ""
                            val tTime = timeEl?.text()?.trim() ?: ""
                            val dSec = parseTimeStringToSeconds(tTime)
                            if (tName.isNotBlank() && dSec != null && dSec > 0) {
                                if (seenTitles.contains(tName.lowercase())) {
                                    break
                                }
                                seenTitles.add(tName.lowercase())
                                rawChapters.add(Pair(durMsAcc, tName))
                                durMsAcc += (dSec * 1000.0).toLong()
                            }
                        }
                    }
                }

                // 3. Fallback parsing teks timestamp dari konten pos (Pola 1 & Pola 2)
                if (rawChapters.isEmpty()) {
                    val entryContent = doc.select(".entry-content, .post-content, article").first()
                    if (entryContent != null) {
                        val text = entryContent.text()

                        // Pola 1: Nama track + durasi masing-masing (contoh: "00 intro 2:38  01 title 5:26")
                        // Sesuai implementasi desktop config_manager.py: jika clean_t ada di seen_titles, lakukan BREAK!
                        val pDuration = Pattern.compile("(?:\r?\n|^)\\s*(\\d{1,2}\\s+[^\\s\\d][^\\n\\r\\t]{1,60}?)\\s+(\\d{1,2}:\\d{2})(?=\\s|$)")
                        val mDuration = pDuration.matcher(text)
                        var durMsAcc = 0L
                        val durationChapters = mutableListOf<Pair<Long, String>>()
                        val seenTitles = mutableSetOf<String>()

                        while (mDuration.find()) {
                            val cleanName = mDuration.group(1)?.trim() ?: ""
                            val isNonsensePola1 = cleanName.contains("http://") || cleanName.contains("https://") ||
                                    cleanName.contains("twitter.com") || cleanName.contains("x.com") ||
                                    cleanName.contains("CV:") || cleanName.contains("CV：") ||
                                    cleanName.contains("クレジット") || cleanName.contains("おまけ") ||
                                    cleanName.contains("同時に犯す") || cleanName.contains("妹の処女喪失")
                            if (cleanName.isNotBlank() && !isNonsensePola1) {
                                if (seenTitles.contains(cleanName.lowercase())) {
                                    // Berhenti seketika jika tracklist mengulang kembali ke awal
                                    break
                                }
                                seenTitles.add(cleanName.lowercase())
                                val dSec = parseTimeStringToSeconds(mDuration.group(2) ?: "")
                                if (dSec != null && dSec > 0) {
                                    durationChapters.add(Pair(durMsAcc, cleanName))
                                    durMsAcc += (dSec * 1000.0).toLong()
                                }
                            }
                        }

                        if (durationChapters.size >= 2) {
                            rawChapters.addAll(durationChapters)
                        } else {
                            // Pola 2: Timestamp di baris teks baru (contoh: 00:00:00 Track 1)
                            val p1 = Pattern.compile("(?:\r?\n|^)\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s+([^\\n\\r<]{2,80})")
                            val m1 = p1.matcher(text)
                            val pola2Chapters = mutableListOf<Pair<Long, String>>()
                            while (m1.find()) {
                                val sec = parseTimeStringToSeconds(m1.group(1) ?: "")
                                val tStr = m1.group(2)?.trim() ?: ""
                                val isNonsense = tStr.contains("http://") || tStr.contains("https://") ||
                                        tStr.contains("twitter.com") || tStr.contains("x.com") ||
                                        tStr.contains("pixiv.net") || tStr.contains("dlsite.com") ||
                                        tStr.contains("CV:") || tStr.contains("CV：") ||
                                        tStr.contains("クレジット") || tStr.contains("イラスト") ||
                                        tStr.contains("シナリオ") || tStr.contains("台本") ||
                                        tStr.contains("サークル") || tStr.contains("音声制作") ||
                                        tStr.startsWith("◆トラック")
                                if (sec != null && tStr.isNotBlank() && tStr.length < 120 && !isNonsense) {
                                    val msVal = (sec * 1000.0).toLong()
                                    if (seen.add("$msVal-$tStr")) {
                                        pola2Chapters.add(Pair(msVal, tStr))
                                    }
                                }
                            }
                            // Validasi: tracklist nyata harus mulai dari awal lagu (<= 60 detik)
                            pola2Chapters.sortBy { it.first }
                            if (pola2Chapters.size >= 2 && pola2Chapters.first().first <= 60000L) {
                                rawChapters.addAll(pola2Chapters)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        rawChapters.sortBy { it.first }

        val result = mutableListOf<ChapterItem>()
        for (i in rawChapters.indices) {
            val startMs = rawChapters[i].first
            val nextStartMs = if (i + 1 < rawChapters.size) rawChapters[i + 1].first else 0L
            val cleanTitle = rawChapters[i].second
                .replace(Regex("\\.(?:mp3|wav|m4a|flac|aac|ogg)$", RegexOption.IGNORE_CASE), "")
                .trim()
            result.add(
                ChapterItem(
                    startMs = startMs,
                    endMs = nextStartMs,
                    title = if (cleanTitle.isBlank()) "Track ${i + 1}" else cleanTitle
                )
            )
        }
        result
    }

    /**
     * Memastikan daftar bab tidak mengandung perulangan siklik / duplikasi bab ganda.
     */
    fun deduplicateChapters(chapters: List<ChapterItem>): List<ChapterItem> {
        if (chapters.size <= 2) return chapters
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ChapterItem>()
        for (ch in chapters) {
            val key = ch.title.trim().lowercase()
            if (key.isNotBlank()) {
                if (seen.contains(key)) {
                    // Berhenti jika tracklist mengulang kembali dari awal (duplikasi siklus)
                    break
                }
                seen.add(key)
            }
            result.add(ch)
        }
        return if (result.size >= 2) result else chapters
    }

    /**
     * Menemukan bab secara cerdas:
     * 1. UTAMAKAN DARI asmr.one TREE (SUMBER RESMI & LENGKAP).
     * 2. Fallback hanya jika asmr.one tidak memiliki karya tersebut: scraping dari japaneseasmr.com.
     */
    suspend fun discoverChapters(rjId: String): List<ChapterItem> = withContext(Dispatchers.IO) {
        val cleanTarget = rjId.trim().uppercase()
        if (cleanTarget.isBlank()) return@withContext emptyList()

        // 1. UTAMAKAN BASIS DATA asmr.one (STRUKTUR RESMI & LENGKAP)
        val numId = cleanTarget.replace(Regex("^[A-Za-z]+"), "")
        if (numId.isNotBlank()) {
            try {
                val asmrOneTree = TrackDiscoveryService.fetchAsmrOneTree(numId)
                if (asmrOneTree != null && asmrOneTree.length() > 0) {
                    val candTracks = TrackDiscoveryService.filterSmartAsmrOneTracks(asmrOneTree)
                    if (candTracks.size > 1) {
                        val result = mutableListOf<ChapterItem>()
                        var currMs = 0L
                        for (t in candTracks) {
                            val durMs = (t.duration * 1000.0).toLong()
                            val cleanName = t.name
                                .replace(Regex("\\.(?:mp3|wav|m4a|flac|aac|ogg)$", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("_(?:SE|NoSE)$", RegexOption.IGNORE_CASE), "")
                                .trim()

                            result.add(
                                ChapterItem(
                                    startMs = currMs,
                                    endMs = currMs + durMs,
                                    title = if (cleanName.isBlank()) "Track ${result.size + 1}" else cleanName
                                )
                            )
                            currMs += durMs
                        }
                        val deduped = deduplicateChapters(result)
                        if (deduped.isNotEmpty()) return@withContext deduped
                    }
                }
            } catch (e: Exception) {
            }
        }

        // 2. FALLBACK HANYA JIKA asmr.one TIDAK TERSEDIA: scraping dari japaneseasmr.com
        val jasmrChapters = fetchJapaneseAsmrChapters(cleanTarget)
        if (jasmrChapters.isNotEmpty()) {
            val deduped = deduplicateChapters(jasmrChapters)
            if (deduped.isNotEmpty()) return@withContext deduped
        }

        emptyList()
    }

    /**
     * 4-Tier Smart Hybrid Chapter Mapping:
     * Mengaitkan metadata bab dengan file unduhan fisik (Kasus A, Kasus B, Kasus B2, Kasus C).
     */
    suspend fun generateChapters(
        trackFiles: List<File>,
        trackNames: List<String>? = null,
        rjId: String? = null
    ): List<ChapterItem> = withContext(Dispatchers.IO) {
        if (trackFiles.isEmpty()) return@withContext emptyList()

        val chapters = mutableListOf<ChapterItem>()
        var hybridMatched = false

        val cleanRjid = rjId?.trim()?.uppercase()
        val numId = cleanRjid?.replace(Regex("^[A-Za-z]+"), "")

        if (!numId.isNullOrBlank()) {
            try {
                val asmrOneTree = TrackDiscoveryService.fetchAsmrOneTree(numId)
                if (asmrOneTree != null && asmrOneTree.length() > 0) {
                    val candTracks = TrackDiscoveryService.filterSmartAsmrOneTracks(asmrOneTree)
                    if (candTracks.size > 1) {
                        val sumAsmrMs = (candTracks.sumOf { it.duration } * 1000.0).toLong()
                        val t0DurMs = getAudioDurationMs(trackFiles[0])

                        // KASUS A1: Track 1 JapaneseASMR adalah audio master kompilasi yang durasinya cocok dengan total bab asmr.one
                        val durDiff = abs(t0DurMs - sumAsmrMs)
                        val toleranceMs = maxOf(60000L, (sumAsmrMs * 0.10).toLong())
                        if (t0DurMs > 10000L && durDiff <= toleranceMs) {
                            var currMs = 0L
                            for (t in candTracks) {
                                val durMs = (t.duration * 1000.0).toLong()
                                val cleanName = t.name
                                    .replace(Regex("\\.(?:mp3|wav|m4a|flac|aac|ogg)$", RegexOption.IGNORE_CASE), "")
                                    .replace(Regex("_(?:SE|NoSE)$", RegexOption.IGNORE_CASE), "")
                                    .trim()

                                chapters.add(
                                    ChapterItem(
                                        startMs = currMs,
                                        endMs = currMs + durMs,
                                        title = if (cleanName.isBlank()) "Track ${chapters.size + 1}" else cleanName
                                    )
                                )
                                currMs += durMs
                            }

                            // Jika ada track fisik tambahan (Omake, Bonus, EX di trackFiles[1..])
                            if (trackFiles.size > 1) {
                                for (idx in 1 until trackFiles.size) {
                                    val omakeDurMs = getAudioDurationMs(trackFiles[idx])
                                    val omakeTitle = if (trackNames != null && idx < trackNames.size) {
                                        trackNames[idx]
                                    } else {
                                        "Omake $idx"
                                    }
                                    chapters.add(
                                        ChapterItem(
                                            startMs = currMs,
                                            endMs = currMs + omakeDurMs,
                                            title = omakeTitle
                                        )
                                    )
                                    currMs += omakeDurMs
                                }
                            }
                            hybridMatched = true
                        }
                        // KASUS A2: Prefix matching (contoh RJ01284698 di mana Track 1 weeab0o hanya berisi bab 1..5)
                        else if (t0DurMs > 10000L) {
                            var matchedK = 0
                            for (k in candTracks.size - 1 downTo 2) {
                                val prefixSumMs = (candTracks.take(k).sumOf { it.duration } * 1000.0).toLong()
                                val pDiff = abs(t0DurMs - prefixSumMs)
                                val pTol = maxOf(30000L, (prefixSumMs * 0.10).toLong())
                                if (pDiff <= pTol) {
                                    matchedK = k
                                    break
                                }
                            }

                            if (matchedK > 0) {
                                var currMs = 0L
                                for (idx in 0 until matchedK) {
                                    val t = candTracks[idx]
                                    val durMs = (t.duration * 1000.0).toLong()
                                    val cleanName = t.name
                                        .replace(Regex("\\.(?:mp3|wav|m4a|flac|aac|ogg)$", RegexOption.IGNORE_CASE), "")
                                        .replace(Regex("_(?:SE|NoSE)$", RegexOption.IGNORE_CASE), "")
                                        .trim()
                                    chapters.add(
                                        ChapterItem(
                                            startMs = currMs,
                                            endMs = currMs + durMs,
                                            title = if (cleanName.isBlank()) "Track ${idx + 1}" else cleanName
                                        )
                                    )
                                    currMs += durMs
                                }
                                if (trackFiles.size > 1) {
                                    for (idx in 1 until trackFiles.size) {
                                        val omakeDurMs = getAudioDurationMs(trackFiles[idx])
                                        val omakeTitle = if (trackNames != null && idx < trackNames.size) trackNames[idx] else "Omake $idx"
                                        chapters.add(ChapterItem(startMs = currMs, endMs = currMs + omakeDurMs, title = omakeTitle))
                                        currMs += omakeDurMs
                                    }
                                }
                                hybridMatched = true
                            }
                            // KASUS A3: Kompilasi single file dengan proporsional scaling dari metadata asmr.one
                            else if (trackFiles.size == 1 && sumAsmrMs > 0L) {
                                val scale = t0DurMs.toDouble() / sumAsmrMs.toDouble()
                                var currMs = 0L
                                for (t in candTracks) {
                                    val scaledDurMs = ((t.duration * 1000.0) * scale).toLong()
                                    val cleanName = t.name
                                        .replace(Regex("\\.(?:mp3|wav|m4a|flac|aac|ogg)$", RegexOption.IGNORE_CASE), "")
                                        .replace(Regex("_(?:SE|NoSE)$", RegexOption.IGNORE_CASE), "")
                                        .trim()
                                    chapters.add(
                                        ChapterItem(
                                            startMs = currMs,
                                            endMs = currMs + scaledDurMs,
                                            title = if (cleanName.isBlank()) "Track ${chapters.size + 1}" else cleanName
                                        )
                                    )
                                    currMs += scaledDurMs
                                }
                                hybridMatched = true
                            }
                        }
                        // KASUS B: Jumlah file fisik sama dengan candTracks dan total durasi cocok
                        else if (trackFiles.size == candTracks.size) {
                            val totPhysMs = trackFiles.sumOf { getAudioDurationMs(it) }
                            val toleranceB = maxOf(30000L, (sumAsmrMs * 0.10).toLong())
                            if (abs(totPhysMs - sumAsmrMs) <= toleranceB) {
                                var currMs = 0L
                                for (idx in trackFiles.indices) {
                                    val durMs = getAudioDurationMs(trackFiles[idx])
                                    val tTitle = candTracks[idx].name
                                        .replace(Regex("\\.(?:mp3|wav|m4a|flac|aac|ogg)$", RegexOption.IGNORE_CASE), "")
                                        .replace(Regex("_(?:SE|NoSE)$", RegexOption.IGNORE_CASE), "")
                                        .trim()

                                    chapters.add(
                                        ChapterItem(
                                            startMs = currMs,
                                            endMs = currMs + durMs,
                                            title = if (tTitle.isBlank()) "Track ${idx + 1}" else tTitle
                                        )
                                    )
                                    currMs += durMs
                                }
                                hybridMatched = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }

        // KASUS B2: Jika asmr.one tidak memiliki bab, cek langsung ke postingan japaneseasmr.com
        if (!hybridMatched && !cleanRjid.isNullOrBlank()) {
            val jasmrChapters = fetchJapaneseAsmrChapters(cleanRjid)
            if (jasmrChapters.size > 1) {
                val t0DurMs = getAudioDurationMs(trackFiles[0])
                val validChaps = if (t0DurMs > 0) {
                    jasmrChapters.filter { it.startMs < t0DurMs - 1000L }
                } else {
                    jasmrChapters
                }

                if (validChaps.size > 1) {
                    chapters.clear()
                    for (i in validChaps.indices) {
                        val sMs = validChaps[i].startMs
                        val eMs = if (i + 1 < validChaps.size) validChaps[i + 1].startMs else (if (t0DurMs > 0) t0DurMs else (sMs + 60000L))
                        chapters.add(
                            ChapterItem(
                                startMs = sMs,
                                endMs = maxOf(sMs + 1000L, eMs),
                                title = validChaps[i].title
                            )
                        )
                    }

                    // Jika ada track fisik tambahan di disk (Omake di trackFiles[1:])
                    if (trackFiles.size > 1) {
                        var lastEnd = chapters.last().endMs
                        for (idx in 1 until trackFiles.size) {
                            val omakeDurMs = getAudioDurationMs(trackFiles[idx])
                            val omakeTitle = if (trackNames != null && idx < trackNames.size) {
                                trackNames[idx]
                            } else {
                                "Omake $idx"
                            }
                            chapters.add(
                                ChapterItem(
                                    startMs = lastEnd,
                                    endMs = lastEnd + omakeDurMs,
                                    title = omakeTitle
                                )
                            )
                            lastEnd += omakeDurMs
                        }
                    }
                    hybridMatched = true
                }
            }
        }

        // KASUS C: Fallback multi-track fisik murni jika > 1 file
        if (!hybridMatched && trackFiles.size > 1) {
            var currMs = 0L
            for (idx in trackFiles.indices) {
                val durMs = getAudioDurationMs(trackFiles[idx])
                val tName = if (trackNames != null && idx < trackNames.size) trackNames[idx] else "Track ${idx + 1}"
                chapters.add(
                    ChapterItem(
                        startMs = currMs,
                        endMs = currMs + durMs,
                        title = tName
                    )
                )
                currMs += durMs
            }
        }

        deduplicateChapters(chapters)
    }

    /**
     * Menulis berkas CUE Sheet eksternal (.cue) untuk pendamping berkas audio.
     */
    fun writeCueSheet(
        audioFile: File,
        chapters: List<ChapterItem>,
        workTitle: String? = null,
        performer: String? = null
    ): File? {
        if (!audioFile.exists() || chapters.isEmpty()) return null
        val cueFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.cue")

        return try {
            val sb = StringBuilder()
            sb.appendLine("REM GENRE \"ASMR\"")
            sb.appendLine("REM COMMENT \"Generated by JapaneseASMR Downloader\"")
            if (!performer.isNullOrBlank() && performer != "-") {
                sb.appendLine("PERFORMER \"${escapeCue(performer)}\"")
            }
            if (!workTitle.isNullOrBlank()) {
                sb.appendLine("TITLE \"${escapeCue(workTitle)}\"")
            }
            sb.appendLine("FILE \"${audioFile.name}\" MP3")

            for ((idx, ch) in chapters.withIndex()) {
                val trackNum = String.format(Locale.US, "%02d", idx + 1)
                sb.appendLine("  TRACK $trackNum AUDIO")
                sb.appendLine("    TITLE \"${escapeCue(ch.title)}\"")
                sb.appendLine("    INDEX 01 ${formatCueTimestamp(ch.startMs)}")
            }

            cueFile.writeText(sb.toString(), Charsets.UTF_8)
            cueFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Membaca dan mem-parsing berkas CUE Sheet eksternal (.cue).
     */
    fun parseCueFile(cueFile: File): List<ChapterItem> {
        if (!cueFile.exists() || cueFile.length() == 0L) return emptyList()
        val chapters = mutableListOf<ChapterItem>()

        try {
            val lines = cueFile.readLines(Charsets.UTF_8)
            var currentTitle = ""
            var currentStartMs = 0L

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("TITLE", ignoreCase = true) && !trimmed.startsWith("TITLE \"\"")) {
                    val m = Pattern.compile("TITLE\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(trimmed)
                    if (m.find()) {
                        currentTitle = m.group(1) ?: ""
                    }
                } else if (trimmed.startsWith("INDEX 01", ignoreCase = true)) {
                    val m = Pattern.compile("INDEX 01\\s+(\\d+:\\d+:\\d+)", Pattern.CASE_INSENSITIVE).matcher(trimmed)
                    if (m.find()) {
                        val tsStr = m.group(1) ?: ""
                        currentStartMs = parseCueTimestampToMs(tsStr)
                        chapters.add(ChapterItem(currentStartMs, 0L, currentTitle.ifBlank { "Track ${chapters.size + 1}" }))
                        currentTitle = ""
                    }
                }
            }

            for (i in 0 until chapters.size - 1) {
                val nextStart = chapters[i + 1].startMs
                chapters[i] = chapters[i].copy(endMs = nextStart)
            }
        } catch (e: Exception) {
        }

        return chapters
    }

    private fun escapeCue(str: String): String = str.replace("\"", "'").trim()

    private fun formatCueTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        val f = ((ms % 1000) * 75) / 1000
        return String.format(Locale.US, "%02d:%02d:%02d", m, s, f)
    }

    private fun parseCueTimestampToMs(ts: String): Long {
        val parts = ts.trim().split(":")
        if (parts.size == 3) {
            val m = parts[0].toLongOrNull() ?: 0L
            val s = parts[1].toLongOrNull() ?: 0L
            val f = parts[2].toLongOrNull() ?: 0L
            return (m * 60 + s) * 1000 + (f * 1000) / 75
        }
        return 0L
    }

    private fun parseTimeStringToSeconds(ts: String): Double? {
        val parts = ts.trim().split(":")
        return try {
            if (parts.size == 3) {
                parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
            } else if (parts.size == 2) {
                parts[0].toDouble() * 60 + parts[1].toDouble()
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
