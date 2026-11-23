package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.dao.TranslationCacheDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.TranslationCacheEntity
import com.saymaven.downloader.japaneseasmr.service.glossary.ChineseAsmrGlossary
import com.saymaven.downloader.japaneseasmr.service.glossary.IndonesianPostFilter
import com.saymaven.downloader.japaneseasmr.service.glossary.JapaneseAsmrGlossary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TranslationService Engine v3:
 * 1. Multi-Provider Router: Google Translate (Default), Microsoft Edge, Youdao, dan LLM (Claude, Gemini, DeepSeek, dll.).
 * 2. Delimited Numbered Tag Batching ([[idx]]) anti-gagal, mencegah lonjakan puluhan request paralel.
 * 3. Modular Clean Pre/Post-Processor (JapaneseAsmrGlossary, ChineseAsmrGlossary, IndonesianPostFilter).
 * 4. Dedicated OkHttp Client dengan tuning dispatcher (maxRequests=32, maxPerHost=16) dan timeout pendek.
 * 5. Semaphore Rate Limiter untuk mencegah saturasi antrean jaringan aplikasi.
 * 6. High-Performance Persistent Room Database & Memory Cache (0ms Instant Loading).
 */
object TranslationService {

    @Volatile
    private var cacheDao: TranslationCacheDao? = null
    @Volatile
    private var appContext: Context? = null
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val cacheClearedListeners = java.util.Collections.synchronizedList(mutableListOf<() -> Unit>())

    // Jalur cepat terpisah untuk klik tunggal agar tidak pernah antre di belakang batch massal
    private val singleTextSemaphore = Semaphore(4)
    private val batchSemaphore = Semaphore(6)

    @Volatile
    var googleRateLimitUntilMs: Long = 0L
        private set

    @Volatile
    var lastNetworkError: String? = null
        private set

    private val _translationErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val translationErrorEvents: SharedFlow<String> = _translationErrorEvents.asSharedFlow()

    @Volatile
    private var lastErrorEmitMs: Long = 0L

    private fun emitError(message: String) {
        lastNetworkError = message
        val now = System.currentTimeMillis()
        if (now - lastErrorEmitMs >= 3_500L) {
            lastErrorEmitMs = now
            _translationErrorEvents.tryEmit(message)
        }
    }

    fun resetRateLimit() {
        googleRateLimitUntilMs = 0L
        lastNetworkError = null
    }

    private val webClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 12
        })
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val llmClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 8
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val EDGE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0"

    fun init(dao: TranslationCacheDao) {
        this.cacheDao = dao
    }

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        if (cacheDao == null) {
            synchronized(this) {
                if (cacheDao == null) {
                    cacheDao = AsmrDatabase.getDatabase(context.applicationContext).translationCacheDao()
                }
            }
        }
        // One-time purge of poisoned/stiff cache entries (v4: clears Mandarin-as-Japanese poisoned rows)
        try {
            val sp = context.applicationContext.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
            if (sp.getInt("translation_cache_version", 0) < 4) {
                sp.edit().putInt("translation_cache_version", 4).apply()
                memoryCache.clear()
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    try {
                        cacheDao?.clearAll()
                        AsmrDatabase.getDatabase(context.applicationContext).historyDao().clearAllTranslations()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Memeriksa apakah hasil cache terjemahan terindikasi beracun (gagal diterjemahkan / masih teks Jepang atau Hanzi).
     */
    fun isPoisonedTranslation(source: String, translated: String?, targetLang: String): Boolean {
        if (translated.isNullOrBlank()) return true
        val cleanSrc = source.trim()
        val cleanTr = translated.trim()
        if (cleanTr == cleanSrc) return true

        val lowerTl = targetLang.lowercase().trim()
        if (lowerTl.startsWith("id") || lowerTl.startsWith("in") || lowerTl.startsWith("en")) {
            val srcKana = countJapaneseKana(cleanSrc)
            val trKana = countJapaneseKana(cleanTr)
            if (srcKana >= 3 && trKana >= 3 && trKana >= (srcKana / 2)) {
                return true
            }
            val srcCjk = countCjkIdeographs(cleanSrc)
            val trCjk = countCjkIdeographs(cleanTr)
            if ((srcCjk > 0 || srcKana > 0) && trCjk >= 1) {
                return true
            }
        }
        return false
    }

    private fun countCjkIdeographs(text: String): Int {
        var count = 0
        for (char in text) {
            if (Character.UnicodeBlock.of(char) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                count++
            }
        }
        return count
    }

    private fun countJapaneseKana(text: String): Int {
        var count = 0
        for (char in text) {
            val ub = Character.UnicodeBlock.of(char)
            if (ub == Character.UnicodeBlock.HIRAGANA || ub == Character.UnicodeBlock.KATAKANA) {
                count++
            }
        }
        return count
    }

    fun addOnCacheClearedListener(listener: () -> Unit) {
        synchronized(cacheClearedListeners) {
            if (!cacheClearedListeners.contains(listener)) {
                cacheClearedListeners.add(listener)
            }
        }
    }

    fun clearMemoryCache() {
        memoryCache.clear()
    }

    suspend fun clearAllCache(context: Context? = null) {
        memoryCache.clear()
        val ctx = context ?: appContext
        try {
            withContext(Dispatchers.IO) {
                cacheDao?.clearAll()
                if (ctx != null) {
                    val db = AsmrDatabase.getDatabase(ctx.applicationContext)
                    db.historyDao().clearAllTranslations()
                    val fastPlaylistFile = java.io.File(ctx.applicationContext.filesDir, "fast_playlist.json")
                    if (fastPlaylistFile.exists()) {
                        fastPlaylistFile.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        synchronized(cacheClearedListeners) {
            cacheClearedListeners.forEach {
                try { it.invoke() } catch (_: Exception) {}
            }
        }
    }

    fun getActiveTargetLanguage(context: Context): String {
        val configured = PreferencesManager.getFastCachedTranslationLanguage(context).lowercase().trim()
        if (configured.isNotBlank() && configured != "auto") {
            return configured
        }

        val lang = PreferencesManager.getFastCachedLanguage(context).lowercase().trim()
        val effectiveLang = if (lang == "system" || lang.isBlank()) {
            val appLocale = java.util.Locale.getDefault()
            appLocale.language.lowercase().trim()
        } else lang

        return when {
            effectiveLang.startsWith("in") || effectiveLang.startsWith("id") -> "id"
            effectiveLang.startsWith("zh") -> "zh-CN"
            effectiveLang.startsWith("ja") -> "ja"
            else -> "en"
        }
    }

    /**
     * Mendeteksi bahasa sumber teks ASMR secara presisi.
     * Mencegah kanji judul Jepang salah terdeteksi sebagai Mandarin, dan mencegah
     * teks Mandarin atau Inggris dipaksa sebagai bahasa Jepang.
     */
    fun detectSourceLanguage(text: String, isSubtitleMode: Boolean = false): String {
        if (ChineseAsmrGlossary.containsJapaneseKana(text)) return "ja"
        if (ChineseAsmrGlossary.isDefinitivelyChinese(text, isSubtitleMode)) return "zh-CN"
        val hasCjk = countCjkIdeographs(text) > 0
        if (!hasCjk) {
            return if (text.any { it in 'a'..'z' || it in 'A'..'Z' }) "en" else "auto"
        }
        return if (isSubtitleMode) "auto" else "ja"
    }

    fun getActiveProvider(context: Context): String {
        return PreferencesManager.getFastCachedTranslationProvider(context)
    }

    private val CHAPTER_PREFIX_REGEX = Regex(
        "^([#◆【\\[]?\\s*(?:(?:TR|Track|TRACK|トラック|T)?\\.?\\s*\\d+(?:-\\d+)?[\\.\\:、_～~】\\]]?\\s*|\\d+(?:-\\d+)?[\\.\\:、_～~】\\]]?\\s*))(.*)$",
        RegexOption.IGNORE_CASE
    )

    private val FILE_EXT_REGEX = Regex("^(.+?)(\\.[a-zA-Z0-9]{2,5})$")

    private fun stripFilenameForTranslation(raw: String): Triple<String, String, String> {
        val extMatch = FILE_EXT_REGEX.find(raw)
        val noExt = extMatch?.groupValues?.get(1) ?: raw
        val ext = extMatch?.groupValues?.get(2) ?: ""
        val prefixMatch = CHAPTER_PREFIX_REGEX.find(noExt)
        return if (prefixMatch != null && prefixMatch.groupValues[2].isNotBlank()) {
            Triple(prefixMatch.groupValues[1], prefixMatch.groupValues[2].trim(), ext)
        } else {
            Triple("", noExt.trim(), ext)
        }
    }

    /**
     * Menerjemahkan satu teks dengan pipeline modular:
     * 1. Memory cache check (0ms).
     * 2. Direct dictionary check (0ms).
     * 3. Room database cache check (<2ms).
     * 4. Pre-processing di memori (Jepang / Mandarin).
     * 5. Network call ke provider aktif dengan failover otomatis ke Google.
     * 6. Post-processing filter bahasa Indonesia.
     * 7. Save ke memory & DB cache.
     */
    suspend fun translateText(
        sourceText: String,
        targetLang: String = "",
        provider: String = ""
    ): String = withContext(Dispatchers.IO) {
        val trimmed = sourceText.trim()
        if (trimmed.isBlank()) return@withContext ""

        val effectiveTargetLang = if (targetLang.isNotBlank() && targetLang != "auto") targetLang else {
            appContext?.let { getActiveTargetLanguage(it) } ?: "id"
        }
        val effectiveProvider = if (provider.isNotBlank()) provider else {
            appContext?.let { getActiveProvider(it) } ?: "google"
        }

        val cacheKey = "$effectiveTargetLang:$trimmed"
        memoryCache[cacheKey]?.let { return@withContext it }

        // 1. Cek kecocokan langsung kamus ASMR
        JapaneseAsmrGlossary.GLOSSARY_ID[trimmed]?.let {
            val res = if (effectiveTargetLang.startsWith("id") || effectiveTargetLang.startsWith("in")) it else trimmed
            if (res != trimmed) {
                memoryCache[cacheKey] = res
                return@withContext res
            }
        }

        // 1b. Cek kecocokan nomor bab/track prefix & ekstensi file
        val (filePrefix, coreBody, fileExt) = stripFilenameForTranslation(trimmed)
        if (coreBody.isNotBlank() && (filePrefix.isNotEmpty() || fileExt.isNotEmpty())) {
            JapaneseAsmrGlossary.GLOSSARY_ID[coreBody]?.let {
                val res = if (effectiveTargetLang.startsWith("id") || effectiveTargetLang.startsWith("in")) {
                    "$filePrefix$it$fileExt"
                } else trimmed
                if (res != trimmed) {
                    memoryCache[cacheKey] = res
                    return@withContext res
                }
            }
        }

        // 1c. Cek Room Database Cache persisten
        try {
            val cachedInDb = cacheDao?.getTranslation(trimmed, effectiveTargetLang)
            if (!cachedInDb.isNullOrBlank()) {
                if (!isPoisonedTranslation(trimmed, cachedInDb, effectiveTargetLang)) {
                    memoryCache[cacheKey] = cachedInDb
                    return@withContext cachedInDb
                } else {
                    // Cache teracuni teks asli/setengah Jepang, bersihkan
                    cacheDao?.deleteTranslation(trimmed, effectiveTargetLang)
                }
            }
        } catch (_: Exception) {
        }

        val detectedSl = detectSourceLanguage(trimmed)
        val isLlm = effectiveProvider.equals("llm", ignoreCase = true)
        val bodyToTranslate = if (coreBody.isNotBlank() && (filePrefix.isNotEmpty() || fileExt.isNotEmpty())) coreBody else trimmed

        // 2. Pre-processing di Dispatchers.Default (Non-blocking UI thread)
        val preProcessed = withContext(Dispatchers.Default) {
            when (detectedSl) {
                "ja" -> JapaneseAsmrGlossary.preProcessJapanese(bodyToTranslate, forLlm = isLlm)
                "zh-CN" -> ChineseAsmrGlossary.preProcessChinese(bodyToTranslate, forLlm = isLlm)
                else -> bodyToTranslate
            }
        }

        val normalizedTl = normalizeTargetLang(effectiveTargetLang)
        val timeoutMs = if (isLlm) 45_000L else 12_000L

        // 3. Panggil Provider Aktif via Fast-Lane Semaphore
        val rawOnline = withTimeoutOrNull(timeoutMs) {
            singleTextSemaphore.withPermit {
                when (effectiveProvider.lowercase()) {
                    "microsoft" -> translateViaMicrosoftEdge(preProcessed, normalizedTl) ?: translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                    "youdao" -> translateViaYoudaoWeb(preProcessed, normalizedTl) ?: translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                    "llm" -> translateViaLlmSingle(preProcessed, normalizedTl) ?: translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                    else -> translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                }
            }
        }

        if (rawOnline.isNullOrBlank()) {
            if (System.currentTimeMillis() < googleRateLimitUntilMs) {
                emitError("Terlalu banyak permintaan terjemahan (HTTP 429). Coba lagi dalam beberapa detik.")
            } else if (!lastNetworkError.isNullOrBlank()) {
                emitError("Gagal menerjemahkan: $lastNetworkError")
            }
            return@withContext trimmed
        }

        // 4. Post-processing filter bahasa Indonesia alami
        val refinedBody = withContext(Dispatchers.Default) {
            if (effectiveTargetLang.startsWith("id") || effectiveTargetLang.startsWith("in")) {
                IndonesianPostFilter.refineIndonesian(rawOnline)
            } else rawOnline
        }

        val finalResult = if (coreBody.isNotBlank() && (filePrefix.isNotEmpty() || fileExt.isNotEmpty())) {
            "$filePrefix$refinedBody$fileExt"
        } else {
            refinedBody
        }

        // Simpan ke cache HANYA jika terjemahan online berhasil dan bukan teks asli yang gagal
        if (finalResult.isNotBlank() && !isPoisonedTranslation(trimmed, finalResult, effectiveTargetLang)) {
            memoryCache[cacheKey] = finalResult
            try {
                cacheDao?.insertTranslation(
                    TranslationCacheEntity(
                        sourceText = trimmed,
                        targetLang = effectiveTargetLang,
                        translatedText = finalResult
                    )
                )
            } catch (_: Exception) {
            }
        }

        finalResult
    }

    /**
     * Menerjemahkan sekumpulan teks sekaligus menggunakan protokol batching delimiter anti-gagal [[idx]].
     * 25-40 item diterjemahkan dalam 1 request HTTP tunggal tanpa pernah pecah menjadi puluhan request paralel.
     */
    suspend fun translateBatch(
        texts: List<String>,
        targetLang: String = "",
        provider: String = "",
        isSubtitleMode: Boolean = false
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val unique = texts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (unique.isEmpty()) return@withContext emptyMap()

        val effectiveTargetLang = if (targetLang.isNotBlank() && targetLang != "auto") targetLang else {
            appContext?.let { getActiveTargetLanguage(it) } ?: "id"
        }
        val effectiveProvider = if (provider.isNotBlank()) provider else {
            appContext?.let { getActiveProvider(it) } ?: "google"
        }

        val result = ConcurrentHashMap<String, String>()
        val normalizedTl = normalizeTargetLang(effectiveTargetLang)

        // 1. Cek memory cache
        val missingAfterMem = mutableListOf<String>()
        for (t in unique) {
            val memVal = memoryCache["$effectiveTargetLang:$t"]
            if (!memVal.isNullOrBlank()) {
                result[t] = memVal
            } else {
                missingAfterMem.add(t)
            }
        }

        // 2. Query Room DB sekaligus (Batch SELECT IN)
        val missingAfterDb = mutableListOf<String>()
        if (missingAfterMem.isNotEmpty()) {
            val dbItems = try {
                cacheDao?.getTranslations(missingAfterMem, effectiveTargetLang) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val dbMap = dbItems.associate { it.sourceText to it.translatedText }
            for (t in missingAfterMem) {
                val dbVal = dbMap[t]
                if (!dbVal.isNullOrBlank() && !isPoisonedTranslation(t, dbVal, effectiveTargetLang)) {
                    memoryCache["$effectiveTargetLang:$t"] = dbVal
                    result[t] = dbVal
                } else {
                    missingAfterDb.add(t)
                }
            }
        }

        // 3. Batching Delimiter Anti-Gagal untuk sisa yang belum ada di cache
        if (missingAfterDb.isNotEmpty()) {
            val newEntities = java.util.Collections.synchronizedList(mutableListOf<TranslationCacheEntity>())

            var toTranslateOnline: List<String> = missingAfterDb
            if (effectiveProvider.lowercase() == "llm") {
                val llmRes = translateBatchViaLlm(missingAfterDb, normalizedTl)
                if (llmRes.isNotEmpty()) {
                    for ((src, tr) in llmRes) {
                        if (!isPoisonedTranslation(src, tr, effectiveTargetLang)) {
                            result[src] = tr
                            memoryCache["$effectiveTargetLang:$src"] = tr
                            newEntities.add(
                                TranslationCacheEntity(
                                    sourceText = src,
                                    targetLang = effectiveTargetLang,
                                    translatedText = tr
                                )
                            )
                        }
                    }
                    toTranslateOnline = missingAfterDb.filter { !result.containsKey(it) }
                }
            }

            if (toTranslateOnline.isNotEmpty()) {
                val chunkPairs = toTranslateOnline
                    .groupBy { detectSourceLanguage(it, isSubtitleMode) }
                    .flatMap { (lang, items) -> items.chunked(10).map { it to lang } }

                val completedOrTimedOut = withTimeoutOrNull(35_000L) {
                    coroutineScope {
                        chunkPairs.map { (chunk, sourceLang) ->
                            async(Dispatchers.IO) {
                                // Pre-process chunk di Dispatchers.Default
                                val preparedChunk = withContext(Dispatchers.Default) {
                                    chunk.map { item ->
                                        val cleanBody = if (isSubtitleMode) item else stripFilenameForTranslation(item).second
                                        val pre = when (sourceLang) {
                                            "ja" -> if (isSubtitleMode) JapaneseAsmrGlossary.preProcessForSubtitle(cleanBody)
                                                    else JapaneseAsmrGlossary.preProcessJapanese(cleanBody)
                                            "zh-CN" -> ChineseAsmrGlossary.preProcessChinese(cleanBody)
                                            else -> cleanBody
                                        }
                                        // Escape tag kurung siku ganda yang mungkin ada di teks sumber
                                        pre.replace("[[", "⟦").replace("]]", "⟧").replace('\n', ' ').trim().ifEmpty { item }
                                    }
                                }

                                // Susun paket tag bernomor [[0]] Teks 0\n[[1]] Teks 1
                                val taggedPayload = preparedChunk.mapIndexed { idx, str -> "[[$idx]] $str" }.joinToString("\n")

                                val batchResponse = batchSemaphore.withPermit {
                                    when (effectiveProvider.lowercase()) {
                                        "microsoft" -> translateViaMicrosoftEdge(taggedPayload, normalizedTl) ?: translateViaGoogleWeb(taggedPayload, normalizedTl, sourceLang)
                                        "youdao" -> translateViaYoudaoWeb(taggedPayload, normalizedTl) ?: translateViaGoogleWeb(taggedPayload, normalizedTl, sourceLang)
                                        else -> translateViaGoogleWeb(taggedPayload, normalizedTl, sourceLang)
                                    }
                                }

                                // Ekstrak hasil via Regex deterministik (mendukung bracket ASCII, fullwidth, dan spasi)
                                val parsedMap = mutableMapOf<Int, String>()
                                if (!batchResponse.isNullOrBlank()) {
                                    val tagRegex = Regex("""(?:\[\[|［［|\[\s*\[)(\d+)(?:\]\]|］］|\]\s*\])\s*([\s\S]*?)(?=(?:\[\[|［［|\[\s*\[)\d+(?:\]\]|］］|\]\s*\])|$)""")
                                    val matches = tagRegex.findAll(batchResponse)
                                    for (m in matches) {
                                        val idx = m.groupValues[1].toIntOrNull()
                                        val body = m.groupValues[2].trim()
                                        if (idx != null && body.isNotEmpty()) {
                                            parsedMap[idx] = body
                                        }
                                    }
                                }

                                // Fallback newline-only jika tag [[idx]] dirusak oleh mesin terjemah (khususnya mode subtitle)
                                if (parsedMap.size < chunk.size / 2 && chunk.size > 1 && System.currentTimeMillis() >= googleRateLimitUntilMs) {
                                    val plainPayload = preparedChunk.joinToString("\n")
                                    val plainResponse = batchSemaphore.withPermit {
                                        when (effectiveProvider.lowercase()) {
                                            "microsoft" -> translateViaMicrosoftEdge(plainPayload, normalizedTl) ?: translateViaGoogleWeb(plainPayload, normalizedTl, sourceLang)
                                            "youdao" -> translateViaYoudaoWeb(plainPayload, normalizedTl) ?: translateViaGoogleWeb(plainPayload, normalizedTl, sourceLang)
                                            else -> translateViaGoogleWeb(plainPayload, normalizedTl, sourceLang)
                                        }
                                    }
                                    val respLines = plainResponse?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                                    if (respLines.size == chunk.size) {
                                        for (i in chunk.indices) {
                                            if (!parsedMap.containsKey(i)) {
                                                parsedMap[i] = respLines[i]
                                            }
                                        }
                                    }
                                }

                                // Simpan semua item yang berhasil di-parse (jangan buang hasil parsial!)
                                val missingIndices = mutableListOf<Int>()
                                for (i in chunk.indices) {
                                    val src = chunk[i]
                                    val rawParsed = parsedMap[i]
                                    if (!rawParsed.isNullOrBlank()) {
                                        val rawTr = rawParsed.replace("⟦", "[[").replace("⟧", "]]")
                                        val refinedBody = if (effectiveTargetLang.startsWith("id") || effectiveTargetLang.startsWith("in")) {
                                            IndonesianPostFilter.refineIndonesian(rawTr)
                                        } else rawTr
                                        val finalTr = if (isSubtitleMode) {
                                            refinedBody
                                        } else {
                                            val (p, _, e) = stripFilenameForTranslation(src)
                                            if (p.isNotEmpty() || e.isNotEmpty()) "$p$refinedBody$e" else refinedBody
                                        }

                                        if (finalTr.isNotBlank() && !isPoisonedTranslation(src, finalTr, effectiveTargetLang)) {
                                            result[src] = finalTr
                                            memoryCache["$effectiveTargetLang:$src"] = finalTr
                                            newEntities.add(
                                                TranslationCacheEntity(
                                                    sourceText = src,
                                                    targetLang = effectiveTargetLang,
                                                    translatedText = finalTr
                                                )
                                            )
                                        } else {
                                            missingIndices.add(i)
                                        }
                                    } else {
                                        missingIndices.add(i)
                                    }
                                }

                                // Fallback HANYA untuk maksimal 3 item yang benar-benar gagal, dan hentikan jika Google sedang rate-limited
                                if (missingIndices.isNotEmpty() && System.currentTimeMillis() >= googleRateLimitUntilMs) {
                                    for (idx in missingIndices.take(3)) {
                                        if (System.currentTimeMillis() < googleRateLimitUntilMs) break
                                        val src = chunk[idx]
                                        val tr = translateText(src, effectiveTargetLang, effectiveProvider)
                                        if (tr.isNotBlank() && !isPoisonedTranslation(src, tr, effectiveTargetLang)) {
                                            result[src] = tr
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
                if (completedOrTimedOut == null) {
                    emitError("Waktu terjemahan habis (timeout). Sebagian hasil mungkin belum lengkap.")
                }
            }

            if (newEntities.isNotEmpty()) {
                try {
                    cacheDao?.insertTranslations(newEntities.toList())
                } catch (_: Exception) {
                }
            }
            if (result.isEmpty() && missingAfterDb.isNotEmpty()) {
                if (System.currentTimeMillis() < googleRateLimitUntilMs) {
                    emitError("Batas permintaan tercapai (HTTP 429). Coba lagi dalam beberapa detik.")
                } else {
                    emitError("Gagal menerjemahkan${if (!lastNetworkError.isNullOrBlank()) ": $lastNetworkError" else ". Periksa koneksi internet."}")
                }
            }
        }

        result
    }

    /**
     * Menerjemahkan lirik/subtitle secara alami.
     * Menggunakan model LLM (Claude, Gemini, DeepSeek, Groq, OpenAI, Ollama) jika API Key terpasang,
     * dengan pembatasan konkurensi (Semaphore) dan failover otomatis ke Google Translate jika NSFW ditolak.
     */
    suspend fun translateLyricsNaturally(
        lines: List<String>,
        targetLang: String,
        llmUrl: String,
        llmKey: String,
        llmModel: String,
        llmPrompt: String,
        concurrency: Int = 2,
        provider: String = ""
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val unique = lines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (unique.isEmpty()) return@withContext emptyMap()

        val result = ConcurrentHashMap<String, String>()
        val missingAfterMem = mutableListOf<String>()
        for (line in unique) {
            val cached = memoryCache["$targetLang:$line"]
            if (!cached.isNullOrBlank() && !isPoisonedTranslation(line, cached, targetLang)) {
                result[line] = cached
            } else {
                missingAfterMem.add(line)
            }
        }
        if (missingAfterMem.isEmpty()) return@withContext result

        val missingAfterDb = mutableListOf<String>()
        try {
            val dbItems = cacheDao?.getTranslations(missingAfterMem, targetLang) ?: emptyList()
            val dbMap = dbItems.associate { it.sourceText to it.translatedText }
            for (line in missingAfterMem) {
                val dbVal = dbMap[line]
                if (!dbVal.isNullOrBlank() && !isPoisonedTranslation(line, dbVal, targetLang)) {
                    memoryCache["$targetLang:$line"] = dbVal
                    result[line] = dbVal
                } else {
                    missingAfterDb.add(line)
                }
            }
        } catch (_: Exception) {
            missingAfterDb.addAll(missingAfterMem)
        }
        if (missingAfterDb.isEmpty()) return@withContext result

        if (llmKey.isNotBlank() && llmUrl.isNotBlank()) {
            val langName = when {
                targetLang.startsWith("id") || targetLang.startsWith("in") -> "id"
                targetLang.startsWith("zh") -> "zh-CN"
                targetLang.startsWith("ja") -> "ja"
                else -> "en"
            }

            val defaultSystem = PreferencesManager.DEFAULT_LLM_PROMPT.replace("into id", "into $langName")
            val systemInstruction = if (llmPrompt.isNotBlank()) llmPrompt.trim() else defaultSystem

            val llmSemaphore = Semaphore(concurrency.coerceIn(1, 5))
            val chunks = missingAfterDb.chunked(25)
            val newEntities = java.util.Collections.synchronizedList(mutableListOf<TranslationCacheEntity>())

            coroutineScope {
                chunks.map { chunk ->
                    async(Dispatchers.IO) {
                        val userPrompt = chunk.mapIndexed { idx, s -> "[$idx] $s" }.joinToString("\n")
                        val response = llmSemaphore.withPermit {
                            if (llmUrl.contains("anthropic.com", ignoreCase = true)) {
                                callClaudeAnthropic(llmKey, llmModel.ifBlank { "claude-haiku-5-5" }, systemInstruction, userPrompt)
                            } else {
                                callOpenAiCompatibleLlm(llmUrl, llmKey, llmModel.ifBlank { "gemini-3.5-flash-lite" }, systemInstruction, userPrompt)
                            }
                        }

                        if (response.isNullOrBlank()) return@async

                        val parsed = mutableMapOf<Int, String>()
                        val lineRegex = Regex("^\\[\\[?(\\d+)\\]\\]?\\s*(.+)$")
                        for (rawLine in response.lines()) {
                            val m = lineRegex.find(rawLine.trim())
                            if (m != null) {
                                val idx = m.groupValues[1].toIntOrNull()
                                val text = m.groupValues[2].trim()
                                if (idx != null && text.isNotEmpty()) {
                                    parsed[idx] = text
                                }
                            }
                        }

                        for (i in chunk.indices) {
                            val src = chunk[i]
                            val tr = parsed[i]
                            if (!tr.isNullOrBlank()) {
                                val cleaned = if (targetLang.startsWith("id") || targetLang.startsWith("in")) {
                                    IndonesianPostFilter.refineIndonesian(tr)
                                } else tr
                                if (!isPoisonedTranslation(src, cleaned, targetLang)) {
                                    result[src] = cleaned
                                    memoryCache["$targetLang:$src"] = cleaned
                                    newEntities.add(
                                        TranslationCacheEntity(
                                            sourceText = src,
                                            targetLang = targetLang,
                                            translatedText = cleaned
                                        )
                                    )
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            if (newEntities.isNotEmpty()) {
                try {
                    cacheDao?.insertTranslations(newEntities.toList())
                } catch (_: Exception) {}
            }

            val remaining = missingAfterDb.filter { !result.containsKey(it) }
            if (remaining.isEmpty()) return@withContext result

            // Fallback hanya untuk baris yang gagal/ditolak NSFW oleh LLM
            result.putAll(translateBatch(remaining, targetLang, provider, isSubtitleMode = true))
            return@withContext result
        }

        // Auto-fallback ke web translator + Smart Glossary jika LLM tidak terkonfigurasi
        result.putAll(translateBatch(missingAfterDb, targetLang, provider, isSubtitleMode = true))
        result
    }

    private suspend fun translateViaLlmSingle(
        text: String,
        targetLang: String
    ): String? {
        val ctx = appContext ?: return null
        val config = PreferencesManager.getFastCachedLlmConfig(ctx)
        if (config.key.isBlank() || config.url.isBlank()) return null

        val langName = when {
            targetLang.startsWith("id") || targetLang.startsWith("in") -> "id"
            targetLang.startsWith("zh") -> "zh-CN"
            targetLang.startsWith("ja") -> "ja"
            else -> "en"
        }

        val defaultSystem = PreferencesManager.DEFAULT_LLM_PROMPT.replace("into id", "into $langName")
        val systemInstruction = if (config.prompt.isNotBlank()) config.prompt.trim() else defaultSystem

        val res = if (config.url.contains("anthropic.com", ignoreCase = true)) {
            callClaudeAnthropic(config.key, config.model.ifBlank { "claude-haiku-5-5" }, systemInstruction, text)
        } else {
            callOpenAiCompatibleLlm(config.url, config.key, config.model.ifBlank { "gemini-3.5-flash-lite" }, systemInstruction, text)
        }

        return res?.trim()?.takeIf { it.isNotBlank() }
    }

    private suspend fun translateBatchViaLlm(
        texts: List<String>,
        targetLang: String
    ): Map<String, String> {
        val ctx = appContext ?: return emptyMap()
        val config = PreferencesManager.getFastCachedLlmConfig(ctx)
        if (config.key.isBlank() || config.url.isBlank()) return emptyMap()

        val llmResult = ConcurrentHashMap<String, String>()
        val langName = when {
            targetLang.startsWith("id") || targetLang.startsWith("in") -> "id"
            targetLang.startsWith("zh") -> "zh-CN"
            targetLang.startsWith("ja") -> "ja"
            else -> "en"
        }

        val defaultSystem = PreferencesManager.DEFAULT_LLM_PROMPT.replace("into id", "into $langName")
        val systemInstruction = if (config.prompt.isNotBlank()) config.prompt.trim() else defaultSystem

        val llmSemaphore = Semaphore(config.concurrency.coerceIn(1, 5))
        val chunks = texts.chunked(25)

        coroutineScope {
            chunks.map { chunk ->
                async(Dispatchers.IO) {
                    val userPrompt = chunk.mapIndexed { idx, s -> "[[$idx]] $s" }.joinToString("\n")
                    val response = llmSemaphore.withPermit {
                        if (config.url.contains("anthropic.com", ignoreCase = true)) {
                            callClaudeAnthropic(config.key, config.model.ifBlank { "claude-haiku-5-5" }, systemInstruction, userPrompt)
                        } else {
                            callOpenAiCompatibleLlm(config.url, config.key, config.model.ifBlank { "gemini-3.5-flash-lite" }, systemInstruction, userPrompt)
                        }
                    }

                    if (!response.isNullOrBlank()) {
                        val tagRegex = Regex("\\[\\[?(\\d+)\\]\\]?\\s*([\\s\\S]*?)(?=\\[\\[?\\d+\\]\\]?|$)")
                        val matches = tagRegex.findAll(response)
                        val parsed = mutableMapOf<Int, String>()
                        for (m in matches) {
                            val idx = m.groupValues[1].toIntOrNull()
                            val body = m.groupValues[2].trim()
                            if (idx != null && body.isNotEmpty()) {
                                parsed[idx] = body
                            }
                        }

                        for (i in chunk.indices) {
                            val tr = parsed[i]
                            if (!tr.isNullOrBlank()) {
                                val cleaned = if (targetLang.startsWith("id") || targetLang.startsWith("in")) {
                                    IndonesianPostFilter.refineIndonesian(tr)
                                } else tr
                                llmResult[chunk[i]] = cleaned
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        return llmResult
    }

    private fun callClaudeAnthropic(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String
    ): String? {
        return try {
            val endpoint = "https://api.anthropic.com/v1/messages"
            val payload = JSONObject().apply {
                put("model", model)
                put("max_tokens", 4096)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
            }

            val req = Request.Builder()
                .url(endpoint)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            llmClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    emitError("LLM Claude HTTP ${resp.code}")
                    return null
                }
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val contentArray = json.optJSONArray("content") ?: return null
                if (contentArray.length() == 0) return null
                contentArray.getJSONObject(0).optString("text")?.trim()
            }
        } catch (e: Exception) {
            emitError("LLM Claude gagal: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun callOpenAiCompatibleLlm(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String
    ): String? {
        return try {
            val endpoint = baseUrl.trimEnd('/') + if (baseUrl.trimEnd('/').endsWith("/chat/completions")) "" else "/chat/completions"
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.3)
            }
            val req = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            llmClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    emitError("LLM API HTTP ${resp.code}")
                    return null
                }
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val choices = json.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                choices.getJSONObject(0).optJSONObject("message")?.optString("content")?.trim()
            }
        } catch (e: Exception) {
            emitError("LLM API gagal: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun translateViaGoogleWeb(text: String, tl: String, sl: String = "auto"): String? {
        if (System.currentTimeMillis() < googleRateLimitUntilMs) {
            return null
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val encoded = try {
            URLEncoder.encode(trimmed, "UTF-8")
        } catch (_: Exception) {
            return null
        }

        // 1. Jika payload pendek/sedang (<= 1200 chars encoded): prioritaskan GET translate.googleapis.com gtx
        if (encoded.length <= 1200) {
            val gtxUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
            tryGet(gtxUrl)?.let { return it }
            if (System.currentTimeMillis() < googleRateLimitUntilMs) return null

            if (sl != "auto") {
                val gtxAutoUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t&q=$encoded"
                tryGet(gtxAutoUrl)?.let { return it }
                if (System.currentTimeMillis() < googleRateLimitUntilMs) return null
            }

            if (trimmed.length <= 500) {
                val clients5Url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sl&tl=$tl&q=$encoded"
                tryGet(clients5Url)?.let { return it }
                if (System.currentTimeMillis() < googleRateLimitUntilMs) return null
            }
        }

        // 2. Untuk payload batch panjang (> 1200 chars) atau fallback jika GET gagal: POST FormBody ke gtx
        val postUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t"
        tryPost(postUrl, trimmed)?.let { return it }
        if (System.currentTimeMillis() < googleRateLimitUntilMs) return null

        if (sl != "auto") {
            val postAutoUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t"
            tryPost(postAutoUrl, trimmed)?.let { return it }
        }

        return null
    }

    private fun tryGet(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            webClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    lastNetworkError = null
                    parseGoogleWebResponse(resp.body?.string())
                } else {
                    if (resp.code == 429 || resp.code == 403) {
                        googleRateLimitUntilMs = System.currentTimeMillis() + 15_000L
                    }
                    lastNetworkError = "HTTP ${resp.code}"
                    null
                }
            }
        } catch (e: Exception) {
            lastNetworkError = e.javaClass.simpleName
            null
        }
    }

    private fun tryPost(url: String, text: String): String? {
        return try {
            val formBody = FormBody.Builder()
                .add("q", text)
                .build()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .post(formBody)
                .build()

            webClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    lastNetworkError = null
                    parseGoogleWebResponse(resp.body?.string())
                } else {
                    if (resp.code == 429 || resp.code == 403) {
                        googleRateLimitUntilMs = System.currentTimeMillis() + 15_000L
                    }
                    lastNetworkError = "HTTP ${resp.code}"
                    null
                }
            }
        } catch (e: Exception) {
            lastNetworkError = e.javaClass.simpleName
            null
        }
    }

    fun parseGoogleWebResponse(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val array = JSONArray(body)
            // 1. Format clients5.google.com: ["translated text 1", "translated text 2", ...]
            val firstStr = array.optString(0, null)
            if (firstStr != null && array.optJSONArray(0) == null) {
                val sb = StringBuilder()
                for (i in 0 until array.length()) {
                    val s = array.optString(i)
                    if (!s.isNullOrBlank()) sb.append(s)
                }
                val res = sb.toString().trim()
                return if (res.isNotBlank()) cleanHtmlEntities(res) else null
            }

            // 2. Format single?client=gtx: [[["translated", "source", ...]], ...]
            val parts = array.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val item = parts.optJSONArray(i)
                val segment = item?.optString(0)
                if (!segment.isNullOrBlank()) {
                    sb.append(segment)
                }
            }
            val res = sb.toString().trim()
            if (res.isNotBlank()) cleanHtmlEntities(res) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun translateViaMyMemory(text: String, targetLang: String, sourceLang: String = "ja"): String? {
        if (text.length > 400) return null
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val tl = if (targetLang.startsWith("id") || targetLang.startsWith("in")) "id" else targetLang
            val sl = if (sourceLang.startsWith("zh")) "zh" else "ja"
            val langPair = "$sl|$tl"
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$langPair"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            webClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val status = json.optInt("responseStatus", 0)
                if (status == 200) {
                    val resData = json.optJSONObject("responseData")
                    val trans = resData?.optString("translatedText")?.trim()
                    if (!trans.isNullOrBlank() && !trans.contains("MYMEMORY WARNING")) {
                        cleanHtmlEntities(trans)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun translateViaMicrosoftEdge(text: String, tl: String): String? {
        return try {
            val target = if (tl.startsWith("zh")) "zh-Hans" else tl
            val url = "https://api-edge.cognitive.microsofttranslator.com/translate?from=auto&to=$target&api-version=3.0"
            val payload = JSONArray().apply {
                put(JSONObject().apply { put("Text", text) })
            }
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", EDGE_USER_AGENT)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            webClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val array = JSONArray(body)
                if (array.length() == 0) return null
                val translations = array.getJSONObject(0).optJSONArray("translations") ?: return null
                if (translations.length() == 0) return null
                val trans = translations.getJSONObject(0).optString("text")?.trim()
                if (!trans.isNullOrBlank()) cleanHtmlEntities(trans) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun translateViaYoudaoWeb(text: String, tl: String): String? {
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val target = if (tl.startsWith("id")) "id" else tl
            val url = "https://aidemo.youdao.com/trans?q=$encoded&from=auto&to=$target"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            webClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val transArray = json.optJSONArray("translation")
                if (transArray != null && transArray.length() > 0) {
                    val res = transArray.optString(0)
                    if (!res.isNullOrBlank()) cleanHtmlEntities(res) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeTargetLang(targetLang: String): String {
        val lower = targetLang.lowercase()
        return when {
            lower.startsWith("id") || lower.startsWith("in") -> "id"
            lower.startsWith("zh") -> "zh-CN"
            lower.startsWith("ja") -> "ja"
            lower.startsWith("en") -> "en"
            else -> lower
        }
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
