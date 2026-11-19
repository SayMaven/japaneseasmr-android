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
            dlCount = dlCount
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
        try {
            val endpoint = "/api/works?page=$page&pageSize=$pageSize&order=$order&sort=$sort&subtitle=$subtitle"
            val responseBody = executeRequest(endpoint)
                ?: return@withContext Result.failure(Exception("Failed to fetch works from server"))

            val list = parseWorksList(responseBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
     * Search works by keyword (CV, RJ code, or title).
     */
    suspend fun searchWorks(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20,
        subtitle: Int = 0
    ): Result<List<AsmrOneWork>> = withContext(Dispatchers.IO) {
        try {
            val encodedKeyword = URLEncoder.encode(keyword.trim(), "UTF-8")
            val endpoint = "/api/search/$encodedKeyword?page=$page&pageSize=$pageSize&subtitle=$subtitle"
            val responseBody = executeRequest(endpoint)
                ?: return@withContext Result.failure(Exception("Failed to search works from server"))

            val list = parseWorksList(responseBody)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
