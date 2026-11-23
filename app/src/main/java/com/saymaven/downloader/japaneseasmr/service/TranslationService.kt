package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.dao.TranslationCacheDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.TranslationCacheEntity
import com.saymaven.downloader.japaneseasmr.service.glossary.ChineseAsmrGlossary
import com.saymaven.downloader.japaneseasmr.service.glossary.IndonesianPostFilter
import com.saymaven.downloader.japaneseasmr.service.glossary.JapaneseAsmrGlossary
import com.saymaven.downloader.japaneseasmr.service.translation.LlmTranslationClient
import com.saymaven.downloader.japaneseasmr.service.translation.WebTranslationProviders
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

    val googleRateLimitUntilMs: Long
        get() = WebTranslationProviders.googleRateLimitUntilMs

    val lastNetworkError: String?
        get() = _lastLocalError ?: WebTranslationProviders.lastNetworkError

    @Volatile
    private var _lastLocalError: String? = null

    private val _translationErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val translationErrorEvents: SharedFlow<String> = _translationErrorEvents.asSharedFlow()

    @Volatile
    private var lastErrorEmitMs: Long = 0L

    private fun emitError(message: String) {
        _lastLocalError = message
        val now = System.currentTimeMillis()
        if (now - lastErrorEmitMs >= 3_500L) {
            lastErrorEmitMs = now
            _translationErrorEvents.tryEmit(message)
        }
    }

    fun resetRateLimit() {
        _lastLocalError = null
        WebTranslationProviders.resetRateLimit()
    }

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
                    "microsoft" -> WebTranslationProviders.translateViaMicrosoftEdge(preProcessed, normalizedTl) ?: WebTranslationProviders.translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: WebTranslationProviders.translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                    "youdao" -> WebTranslationProviders.translateViaYoudaoWeb(preProcessed, normalizedTl) ?: WebTranslationProviders.translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: WebTranslationProviders.translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                    "llm" -> translateViaLlmSingle(preProcessed, normalizedTl) ?: WebTranslationProviders.translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: WebTranslationProviders.translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                    else -> WebTranslationProviders.translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: WebTranslationProviders.translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
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
                                        "microsoft" -> WebTranslationProviders.translateViaMicrosoftEdge(taggedPayload, normalizedTl) ?: WebTranslationProviders.translateViaGoogleWeb(taggedPayload, normalizedTl, sourceLang)
                                        "youdao" -> WebTranslationProviders.translateViaYoudaoWeb(taggedPayload, normalizedTl) ?: WebTranslationProviders.translateViaGoogleWeb(taggedPayload, normalizedTl, sourceLang)
                                        else -> WebTranslationProviders.translateViaGoogleWeb(taggedPayload, normalizedTl, sourceLang)
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
                                            "microsoft" -> WebTranslationProviders.translateViaMicrosoftEdge(plainPayload, normalizedTl) ?: WebTranslationProviders.translateViaGoogleWeb(plainPayload, normalizedTl, sourceLang)
                                            "youdao" -> WebTranslationProviders.translateViaYoudaoWeb(plainPayload, normalizedTl) ?: WebTranslationProviders.translateViaGoogleWeb(plainPayload, normalizedTl, sourceLang)
                                            else -> WebTranslationProviders.translateViaGoogleWeb(plainPayload, normalizedTl, sourceLang)
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
                                LlmTranslationClient.callClaudeAnthropic(llmKey, llmModel.ifBlank { "claude-haiku-5-5" }, systemInstruction, userPrompt, ::emitError)
                            } else {
                                LlmTranslationClient.callOpenAiCompatibleLlm(llmUrl, llmKey, llmModel.ifBlank { "gemini-3.5-flash-lite" }, systemInstruction, userPrompt, ::emitError)
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
            LlmTranslationClient.callClaudeAnthropic(config.key, config.model.ifBlank { "claude-haiku-5-5" }, systemInstruction, text, ::emitError)
        } else {
            LlmTranslationClient.callOpenAiCompatibleLlm(config.url, config.key, config.model.ifBlank { "gemini-3.5-flash-lite" }, systemInstruction, text, ::emitError)
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
                            LlmTranslationClient.callClaudeAnthropic(config.key, config.model.ifBlank { "claude-haiku-5-5" }, systemInstruction, userPrompt, ::emitError)
                        } else {
                            LlmTranslationClient.callOpenAiCompatibleLlm(config.url, config.key, config.model.ifBlank { "gemini-3.5-flash-lite" }, systemInstruction, userPrompt, ::emitError)
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

    fun parseGoogleWebResponse(body: String?): String? = WebTranslationProviders.parseGoogleWebResponse(body)

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
}
