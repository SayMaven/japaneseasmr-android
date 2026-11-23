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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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

    private val networkSemaphore = Semaphore(8)

    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        })
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
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
        // One-time purge of poisoned cache entries created during d4930d9/07cbd71
        try {
            val sp = context.applicationContext.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
            if (sp.getInt("translation_cache_version", 0) < 2) {
                sp.edit().putInt("translation_cache_version", 2).apply()
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
     * Memeriksa apakah hasil cache terjemahan terindikasi beracun (gagal diterjemahkan / masih teks Jepang).
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
        }
        return false
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
     * Mencegah kanji judul Jepang salah terdeteksi sebagai Mandarin oleh Google.
     */
    fun detectSourceLanguage(text: String): String {
        if (ChineseAsmrGlossary.containsJapaneseKana(text)) return "ja"
        if (ChineseAsmrGlossary.isDefinitivelyChinese(text)) return "zh-CN"
        return "ja"
    }

    fun getActiveProvider(context: Context): String {
        return PreferencesManager.getFastCachedTranslationProvider(context)
    }

    private val CHAPTER_PREFIX_REGEX = Regex(
        "^([#◆【\\[]?\\s*(?:(?:TR|Track|TRACK|トラック|T)?\\.?\\s*\\d+(?:-\\d+)?[\\.\\:、_～~】\\]]?\\s*|\\d+(?:-\\d+)?[\\.\\:、_～~】\\]]?\\s*))(.*)$",
        RegexOption.IGNORE_CASE
    )

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

        // 1b. Cek kecocokan nomor bab/track prefix
        val prefixMatch = CHAPTER_PREFIX_REGEX.find(trimmed)
        if (prefixMatch != null) {
            val prefix = prefixMatch.groupValues[1]
            val body = prefixMatch.groupValues[2].trim()
            JapaneseAsmrGlossary.GLOSSARY_ID[body]?.let {
                val combined = "$prefix$it"
                memoryCache[cacheKey] = combined
                return@withContext combined
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

        // 2. Pre-processing di Dispatchers.Default (Non-blocking UI thread)
        val preProcessed = withContext(Dispatchers.Default) {
            if (detectedSl == "ja") {
                JapaneseAsmrGlossary.preProcessJapanese(trimmed)
            } else {
                ChineseAsmrGlossary.preProcessChinese(trimmed)
            }
        }

        val normalizedTl = normalizeTargetLang(effectiveTargetLang)

        // 3. Panggil Provider Aktif via Network Semaphore
        val rawOnline = networkSemaphore.withPermit {
            when (effectiveProvider.lowercase()) {
                "microsoft" -> translateViaMicrosoftEdge(preProcessed, normalizedTl) ?: translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                "youdao" -> translateViaYoudaoWeb(preProcessed, normalizedTl) ?: translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                "llm" -> translateViaLlmSingle(preProcessed, normalizedTl) ?: translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
                else -> translateViaGoogleWeb(preProcessed, normalizedTl, detectedSl) ?: translateViaMyMemory(preProcessed, normalizedTl, detectedSl)
            }
        }

        if (rawOnline.isNullOrBlank()) {
            return@withContext trimmed
        }

        // 4. Post-processing filter bahasa Indonesia alami
        val finalResult = withContext(Dispatchers.Default) {
            if (effectiveTargetLang.startsWith("id") || effectiveTargetLang.startsWith("in")) {
                IndonesianPostFilter.refineIndonesian(rawOnline)
            } else rawOnline
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
        provider: String = ""
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
                    toTranslateOnline = missingAfterDb.filter { !result.containsKey(it) }
                }
            }

            if (toTranslateOnline.isNotEmpty()) {
                val (jaItems, zhItems) = toTranslateOnline.partition { detectSourceLanguage(it) == "ja" }
                val chunkPairs = jaItems.chunked(15).map { it to "ja" } + zhItems.chunked(15).map { it to "zh-CN" }

                coroutineScope {
                    chunkPairs.map { (chunk, sourceLang) ->
                        async(Dispatchers.IO) {
                            // Pre-process chunk di Dispatchers.Default
                            val preparedChunk = withContext(Dispatchers.Default) {
                                chunk.map { item ->
                                    val pre = if (sourceLang == "ja") {
                                        JapaneseAsmrGlossary.preProcessJapanese(item)
                                    } else {
                                        ChineseAsmrGlossary.preProcessChinese(item)
                                    }
                                    // Escape tag kurung siku ganda yang mungkin ada di teks sumber
                                    pre.replace("[[", "⟦").replace("]]", "⟧").replace('\n', ' ').trim().ifEmpty { item }
                                }
                            }

                            // Susun paket tag bernomor [[0]] Teks 0\n[[1]] Teks 1
                            val taggedPayload = preparedChunk.mapIndexed { idx, str -> "[[$idx]] $str" }.joinToString("\n")

                            val batchResponse = networkSemaphore.withPermit {
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

                            // Jika parsing berhasil untuk chunk
                            if (parsedMap.size == chunk.size) {
                                for (i in chunk.indices) {
                                    val src = chunk[i]
                                    val rawTr = parsedMap[i]?.replace("⟦", "[[")?.replace("⟧", "]]") ?: src
                                    val finalTr = if (effectiveTargetLang.startsWith("id") || effectiveTargetLang.startsWith("in")) {
                                        IndonesianPostFilter.refineIndonesian(rawTr)
                                    } else rawTr

                                    if (finalTr.isNotBlank() && finalTr != src) {
                                        result[src] = finalTr
                                        memoryCache["$effectiveTargetLang:$src"] = finalTr
                                        newEntities.add(
                                            TranslationCacheEntity(
                                                sourceText = src,
                                                targetLang = effectiveTargetLang,
                                                translatedText = finalTr
                                            )
                                        )
                                    }
                                }
                            } else {
                                // Fallback bertahap: jika ada format delimiter yang rusak, fallback secara paralel
                                coroutineScope {
                                    chunk.map { src ->
                                        async(Dispatchers.IO) {
                                            val tr = translateText(src, effectiveTargetLang, effectiveProvider)
                                            if (tr.isNotBlank() && tr != src) {
                                                result[src] = tr
                                            }
                                        }
                                    }.awaitAll()
                                }
                            }
                        }
                    }.awaitAll()
                }
            }

            if (newEntities.isNotEmpty()) {
                try {
                    cacheDao?.insertTranslations(newEntities.toList())
                } catch (_: Exception) {
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

        if (llmKey.isNotBlank() && llmUrl.isNotBlank()) {
            val llmResult = ConcurrentHashMap<String, String>()
            val langName = when {
                targetLang.startsWith("id") || targetLang.startsWith("in") -> "id"
                targetLang.startsWith("zh") -> "zh-CN"
                targetLang.startsWith("ja") -> "ja"
                else -> "en"
            }

            val defaultSystem = PreferencesManager.DEFAULT_LLM_PROMPT.replace("into id", "into $langName")
            val systemInstruction = if (llmPrompt.isNotBlank()) llmPrompt.trim() else defaultSystem

            val llmSemaphore = Semaphore(concurrency.coerceIn(1, 5))
            val chunks = unique.chunked(25)
            var allSucceeded = true

            coroutineScope {
                chunks.map { chunk ->
                    async(Dispatchers.IO) {
                        if (!allSucceeded) return@async

                        val userPrompt = chunk.mapIndexed { idx, s -> "[$idx] $s" }.joinToString("\n")
                        val response = llmSemaphore.withPermit {
                            if (llmUrl.contains("anthropic.com", ignoreCase = true)) {
                                callClaudeAnthropic(llmKey, llmModel.ifBlank { "claude-haiku-5-5" }, systemInstruction, userPrompt)
                            } else {
                                callOpenAiCompatibleLlm(llmUrl, llmKey, llmModel.ifBlank { "gemini-3.5-flash-lite" }, systemInstruction, userPrompt)
                            }
                        }

                        if (response.isNullOrBlank()) {
                            allSucceeded = false
                            return@async
                        }

                        val parsed = mutableMapOf<Int, String>()
                        val lineRegex = Regex("^\\[(\\d+)\\]\\s*(.+)$")
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

                        if (parsed.size < chunk.size / 2) {
                            allSucceeded = false
                            return@async
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
                }.awaitAll()
            }

            if (allSucceeded && llmResult.isNotEmpty()) {
                val missing = unique.filter { !llmResult.containsKey(it) }
                if (missing.isNotEmpty()) {
                    llmResult.putAll(translateBatch(missing, targetLang, provider))
                }
                return@withContext llmResult
            }
        }

        // Auto-fallback ke Google Translate + Smart Glossary jika LLM kosong / gagal / ditolak NSFW
        translateBatch(unique, targetLang, provider)
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

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val contentArray = json.optJSONArray("content") ?: return null
                if (contentArray.length() == 0) return null
                contentArray.getJSONObject(0).optString("text")?.trim()
            }
        } catch (_: Exception) {
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

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val choices = json.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                choices.getJSONObject(0).optJSONObject("message")?.optString("content")?.trim()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun translateViaGoogleWeb(text: String, tl: String, sl: String = "auto"): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val encoded = try {
            URLEncoder.encode(trimmed, "UTF-8")
        } catch (_: Exception) {
            return null
        }

        // 1. Jika payload pendek/sedang (<= 1200 chars encoded): prioritaskan GET translate.googleapis.com gtx
        //    (paling stabil, tidak memotong tag [[idx]], latensi ~150ms)
        if (encoded.length <= 1200) {
            val gtxUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
            tryGet(gtxUrl)?.let { return it }

            if (sl != "auto") {
                val gtxAutoUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t&q=$encoded"
                tryGet(gtxAutoUrl)?.let { return it }
            }

            if (trimmed.length <= 500) {
                val clients5Url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sl&tl=$tl&q=$encoded"
                tryGet(clients5Url)?.let { return it }
            }
        }

        // 2. Untuk payload batch panjang (> 1200 chars) atau fallback jika GET gagal: POST FormBody ke gtx
        val postUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t"
        tryPost(postUrl, trimmed)?.let { return it }

        if (sl != "auto") {
            val postAutoUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t"
            tryPost(postAutoUrl, trimmed)?.let { return it }
        }

        // 3. Fallback GET gtx terakhir jika panjang masih wajar
        if (encoded.length in 1201..2500) {
            val gtxUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
            tryGet(gtxUrl)?.let { return it }
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

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    parseGoogleWebResponse(resp.body?.string())
                } else null
            }
        } catch (_: Exception) {
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

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    parseGoogleWebResponse(resp.body?.string())
                } else null
            }
        } catch (_: Exception) {
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

            client.newCall(req).execute().use { resp ->
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

            client.newCall(req).execute().use { resp ->
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

            client.newCall(req).execute().use { resp ->
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
