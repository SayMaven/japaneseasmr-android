package com.saymaven.downloader.japaneseasmr.data.remote

import com.saymaven.downloader.japaneseasmr.data.model.AsmrOneWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Service to communicate with asmr.one public REST API.
 * Benchmark & implementation based on KikoFlu (kikoeru_api_service.dart).
 */
object AsmrOneApiService {

    private const val DEFAULT_HOST = "https://api.asmr-200.com"
    private const val FALLBACK_HOST = "https://api.asmr.one"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    private const val REFERER = "https://www.asmr.one/"
    private const val ORIGIN = "https://www.asmr.one"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Origin", ORIGIN)
            .header("Accept", "application/json, text/plain, */*")
            .build()
    }

    private suspend fun executeRequest(endpoint: String): String? = withContext(Dispatchers.IO) {
        val hosts = listOf(DEFAULT_HOST, FALLBACK_HOST)
        for (host in hosts) {
            try {
                val fullUrl = "$host$endpoint"
                val request = buildRequest(fullUrl)
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext response.body?.string()
                    }
                }
            } catch (e: Exception) {
                // Try fallback host
            }
        }
        null
    }

    /**
     * Formats integer id to standard RJ code format (e.g. RJ01234567 or RJ123456)
     */
    fun formatRjCode(id: Int, sourceId: String? = null): String {
        val cleanSource = sourceId?.trim()
        if (!cleanSource.isNullOrEmpty()) {
            return cleanSource.uppercase()
        }
        return if (id < 1000000) {
            String.format("RJ%06d", id)
        } else {
            String.format("RJ%08d", id)
        }
    }

    private fun parseWorkJson(obj: JSONObject): AsmrOneWork? {
        val id = obj.optInt("id", 0)
        if (id == 0) return null

        val sourceId = obj.optString("source_id", "").ifBlank { null }
        val rjid = formatRjCode(id, sourceId)
        val title = obj.optString("title", rjid)

        // Circle name
        var circleName = obj.optString("name", "")
        if (circleName.isBlank() && obj.has("circle")) {
            val circleObj = obj.optJSONObject("circle")
            if (circleObj != null) {
                circleName = circleObj.optString("name", "")
            }
        }
        if (circleName.isBlank()) circleName = "-"

        // Voice Actors (vas)
        val cvList = mutableListOf<String>()
        val vasArray = obj.optJSONArray("vas")
        if (vasArray != null) {
            for (i in 0 until vasArray.length()) {
                val vaObj = vasArray.optJSONObject(i)
                if (vaObj != null) {
                    val vaName = vaObj.optString("name", "").trim()
                    if (vaName.isNotEmpty()) cvList.add(vaName)
                }
            }
        }
        val cv = if (cvList.isNotEmpty()) cvList.joinToString(", ") else "-"

        // Release date
        var releaseDate = obj.optString("release", "").ifBlank {
            obj.optString("release_date", "")
        }.ifBlank { null }

        // Subtitle status
        val hasSubtitle = obj.optBoolean("has_subtitle", false) ||
                obj.optString("lyric_status", "").isNotEmpty()

        // Age rating (R18, ALL, R15)
        val ageRaw = obj.optString("age", "").ifBlank {
            obj.optString("age_category_string", "")
        }
        val ageRating = when {
            ageRaw.contains("18", ignoreCase = true) -> "R-18"
            ageRaw.contains("15", ignoreCase = true) -> "R-15"
            ageRaw.contains("general", ignoreCase = true) || ageRaw.contains("all", ignoreCase = true) -> "ALL"
            else -> ageRaw.ifBlank { "ALL" }.uppercase()
        }

        val price = if (obj.has("price")) obj.optInt("price") else null
        val rating = if (obj.has("rate_average_2dp")) obj.optDouble("rate_average_2dp") else null
        val reviewCount = if (obj.has("review_count")) obj.optInt("review_count") else null
        val dlCount = if (obj.has("dl_count")) obj.optInt("dl_count") else null

        // Tags / Genres (localized to English)
        val tagsList = mutableListOf<String>()
        val tagsArray = obj.optJSONArray("tags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val tagObj = tagsArray.optJSONObject(i)
                if (tagObj != null) {
                    val tagId = if (tagObj.has("id")) tagObj.optInt("id") else null
                    val directEn = tagObj.optJSONObject("i18n")?.optJSONObject("en-us")?.optString("name", "")?.trim()
                    val rawName = tagObj.optString("name", "").trim()
                    val localized = if (!directEn.isNullOrEmpty()) directEn else com.saymaven.downloader.japaneseasmr.service.TagLocalizer.localize(tagId, rawName)
                    if (localized.isNotEmpty()) tagsList.add(localized)
                } else {
                    val rawName = tagsArray.optString(i, "").trim()
                    val localized = com.saymaven.downloader.japaneseasmr.service.TagLocalizer.localize(null, rawName)
                    if (localized.isNotEmpty()) tagsList.add(localized)
                }
            }
        }

        val coverUrl = "$DEFAULT_HOST/api/cover/$id"

        return AsmrOneWork(
            id = id,
            rjid = rjid,
            title = title,
            circle = circleName,
            cv = cv,
            coverUrl = coverUrl,
            releaseDate = releaseDate,
            hasSubtitle = hasSubtitle,
            ageRating = ageRating,
            price = price,
            rating = rating,
            reviewCount = reviewCount,
            dlCount = dlCount,
            tags = tagsList
        )
    }

    private fun parseWorksList(rawJson: String): List<AsmrOneWork> {
        val worksList = mutableListOf<AsmrOneWork>()
        try {
            val root = JSONObject(rawJson)
            val jsonArray = when {
                root.has("works") -> root.optJSONArray("works")
                root.has("data") -> root.optJSONArray("data")
                else -> null
            }

            if (jsonArray != null) {
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i) ?: continue
                    parseWorkJson(item)?.let { worksList.add(it) }
                }
            }
        } catch (e: Exception) {
            // Check if root is direct JSONArray
            try {
                val array = JSONArray(rawJson)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    parseWorkJson(item)?.let { worksList.add(it) }
                }
            } catch (ignored: Exception) {
            }
        }
        return worksList
    }

    private fun parseWorksPage(rawJson: String, defaultPage: Int, pageSize: Int): AsmrOneWorksPage {
        val works = parseWorksList(rawJson)
        var currentPage = defaultPage
        var totalCount = 0
        var totalPages = 1

        try {
            val root = JSONObject(rawJson)
            val pagObj = root.optJSONObject("pagination")
            if (pagObj != null) {
                currentPage = pagObj.optInt("currentPage", defaultPage)
                val pSize = pagObj.optInt("pageSize", pageSize).coerceAtLeast(1)
                totalCount = pagObj.optInt("total", 0)
                if (totalCount > 0) {
                    totalPages = kotlin.math.ceil(totalCount.toDouble() / pSize.toDouble()).toInt().coerceAtLeast(1)
                }
            }
        } catch (ignored: Exception) {
        }

        if (totalCount == 0 && works.isNotEmpty()) {
            totalCount = works.size
            totalPages = currentPage.coerceAtLeast(1)
        }

        return AsmrOneWorksPage(
            works = works,
            currentPage = currentPage,
            totalCount = totalCount,
            totalPages = totalPages
        )
    }

    /**
     * Get works catalog page with real pagination total count.
     */
    suspend fun getWorksPage(
        page: Int = 1,
        pageSize: Int = 20,
        order: String = "create_date",
        sort: String = "desc",
        subtitle: Int = 0
    ): Result<AsmrOneWorksPage> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "/api/works?page=$page&pageSize=$pageSize&order=$order&sort=$sort&subtitle=$subtitle"
            val responseBody = executeRequest(endpoint)
                ?: return@withContext Result.failure(Exception("Failed to fetch works from server"))

            val pageData = parseWorksPage(responseBody, page, pageSize)
            Result.success(pageData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get works catalog with pagination and sorting.
     */
    suspend fun getWorks(
        page: Int = 1,
        pageSize: Int = 20,
        order: String = "create_date",
        sort: String = "desc",
        subtitle: Int = 0
    ): Result<List<AsmrOneWork>> = withContext(Dispatchers.IO) {
        getWorksPage(page, pageSize, order, sort, subtitle).map { it.works }
    }

    /**
     * Get popular works ordered by download count.
     */
    suspend fun getPopularWorks(
        page: Int = 1,
        pageSize: Int = 20,
        subtitle: Int = 0
    ): Result<List<AsmrOneWork>> {
        return getWorks(
            page = page,
            pageSize = pageSize,
            order = "dl_count",
            sort = "desc",
            subtitle = subtitle
        )
    }

    /**
     * Search works page by keyword with real pagination total count.
     */
    suspend fun searchWorksPage(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20,
        subtitle: Int = 0
    ): Result<AsmrOneWorksPage> = withContext(Dispatchers.IO) {
        try {
            val encodedKeyword = URLEncoder.encode(keyword.trim(), "UTF-8")
            val endpoint = "/api/search/$encodedKeyword?page=$page&pageSize=$pageSize&subtitle=$subtitle"
            val responseBody = executeRequest(endpoint)
                ?: return@withContext Result.failure(Exception("Failed to search works from server"))

            val pageData = parseWorksPage(responseBody, page, pageSize)
            Result.success(pageData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search works by keyword (CV, RJ code, or title).
     */
    suspend fun searchWorks(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20,
        subtitle: Int = 0
    ): Result<List<AsmrOneWork>> = withContext(Dispatchers.IO) {
        searchWorksPage(keyword, page, pageSize, subtitle).map { it.works }
    }

    /**
     * Get file and folder tree for a work for KikoFlu-style Resource Files view.
     */
    suspend fun getWorkFileTree(workId: Int): Result<List<AsmrOneFileNode>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "/api/tracks/$workId"
            val responseBody = executeRequest(endpoint)
                ?: return@withContext Result.failure(Exception("Failed to fetch file tree for work $workId"))

            val array = JSONArray(responseBody)
            val tree = parseFileNodes(array, DEFAULT_HOST)
            Result.success(tree)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFileNodes(jsonArray: JSONArray, host: String): List<AsmrOneFileNode> {
        val list = mutableListOf<AsmrOneFileNode>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val rawType = obj.optString("type", "file").lowercase()
            val title = obj.optString("title", "").ifBlank {
                obj.optString("name", "Item")
            }
            val hash = obj.optString("hash", "").ifBlank { null }
            var streamUrl = obj.optString("mediaStreamUrl", "")
            if (streamUrl.isBlank() && hash != null) {
                streamUrl = "$host/api/media/stream/$hash"
            }
            if (streamUrl.isNotEmpty() && !streamUrl.startsWith("http")) {
                streamUrl = if (streamUrl.startsWith("/")) "$host$streamUrl" else "$host/$streamUrl"
            }

            if (rawType == "folder") {
                val rawChildren = obj.optJSONArray("children")
                val children = if (rawChildren != null) parseFileNodes(rawChildren, host) else emptyList()
                list.add(
                    AsmrOneFileNode(
                        title = title,
                        type = "folder",
                        hash = hash,
                        streamUrl = streamUrl,
                        children = children
                    )
                )
            } else {
                val ext = title.substringAfterLast('.', "").lowercase()
                val detectedType = when {
                    rawType.contains("audio") || ext in listOf("mp3", "wav", "flac", "m4a", "aac", "ogg") -> "audio"
                    rawType.contains("image") || ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> "image"
                    rawType.contains("text") || ext in listOf("txt", "pdf", "lrc", "srt", "vtt") -> "text"
                    else -> "other"
                }
                val duration = obj.optLong("duration", 0L)
                val size = obj.optLong("size", 0L)
                list.add(
                    AsmrOneFileNode(
                        title = title,
                        type = detectedType,
                        hash = hash,
                        streamUrl = streamUrl,
                        duration = duration,
                        size = size
                    )
                )
            }
        }
        return list
    }

    /**
     * Get track list for a work for streaming or offline playback.
     */
    suspend fun getWorkTracks(workId: Int): Result<List<AsmrOneTrack>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "/api/tracks/$workId"
            val responseBody = executeRequest(endpoint)
                ?: return@withContext Result.failure(Exception("Failed to fetch tracks for work $workId"))

            val tracks = mutableListOf<AsmrOneTrack>()
            val array = JSONArray(responseBody)
            extractAudioTracks(array, DEFAULT_HOST, tracks)
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractAudioTracks(
        jsonArray: JSONArray,
        host: String,
        output: MutableList<AsmrOneTrack>
    ) {
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val type = obj.optString("type", "file")
            if (type.equals("folder", ignoreCase = true)) {
                val children = obj.optJSONArray("children")
                if (children != null) {
                    extractAudioTracks(children, host, output)
                }
            } else {
                val title = obj.optString("title", "").ifBlank {
                    obj.optString("name", "Track ${output.size + 1}")
                }
                val hash = obj.optString("hash", "").ifBlank { null }
                var streamUrl = obj.optString("mediaStreamUrl", "")
                if (streamUrl.isBlank() && hash != null) {
                    streamUrl = "$host/api/media/stream/$hash"
                }
                if (streamUrl.isNotEmpty() && !streamUrl.startsWith("http")) {
                    streamUrl = if (streamUrl.startsWith("/")) "$host$streamUrl" else "$host/$streamUrl"
                }

                val isAudio = type.contains("audio", ignoreCase = true) ||
                        title.endsWith(".mp3", ignoreCase = true) ||
                        title.endsWith(".wav", ignoreCase = true) ||
                        title.endsWith(".flac", ignoreCase = true) ||
                        title.endsWith(".m4a", ignoreCase = true) ||
                        title.endsWith(".aac", ignoreCase = true) ||
                        title.endsWith(".ogg", ignoreCase = true) ||
                        (type == "file" && streamUrl.isNotEmpty())

                if (isAudio && streamUrl.isNotEmpty()) {
                    val duration = obj.optLong("duration", 0L)
                    val size = obj.optLong("size", 0L)
                    output.add(
                        AsmrOneTrack(
                            title = title,
                            hash = hash,
                            streamUrl = streamUrl,
                            duration = duration,
                            size = size,
                            type = type
                        )
                    )
                }
            }
        }
    }
}

data class AsmrOneWorksPage(
    val works: List<AsmrOneWork>,
    val currentPage: Int,
    val totalCount: Int,
    val totalPages: Int
)

/**
 * Audio track representation from asmr.one API
 */
data class AsmrOneTrack(
    val title: String,
    val hash: String? = null,
    val streamUrl: String,
    val duration: Long = 0L,
    val size: Long = 0L,
    val type: String = "audio"
)

/**
 * File node representation (folder or file) for KikoFlu-style Resource Files view
 */
data class AsmrOneFileNode(
    val title: String,
    val type: String, // "folder", "audio", "image", "text", "other"
    val hash: String? = null,
    val streamUrl: String = "",
    val duration: Long = 0L,
    val size: Long = 0L,
    val children: List<AsmrOneFileNode> = emptyList()
)
