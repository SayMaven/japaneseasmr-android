package com.saymaven.downloader.japaneseasmr.service.translation

import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal object WebTranslationProviders {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val EDGE_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0"

    @Volatile
    var googleRateLimitUntilMs: Long = 0L
        private set

    @Volatile
    var lastNetworkError: String? = null
        private set

    @androidx.annotation.VisibleForTesting
    internal var googleApiBaseUrl: String = "https://translate.googleapis.com"

    @androidx.annotation.VisibleForTesting
    internal var clients5BaseUrl: String = "https://clients5.google.com"

    private val webClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 12
        })
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    fun resetRateLimit() {
        googleRateLimitUntilMs = 0L
        lastNetworkError = null
    }

    fun translateViaGoogleWeb(text: String, tl: String, sl: String = "auto"): String? {
        if (System.currentTimeMillis() < googleRateLimitUntilMs) return null

        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val encoded = try {
            URLEncoder.encode(trimmed, "UTF-8")
        } catch (_: Exception) {
            return null
        }

        if (encoded.length <= 1200) {
            val gtxUrl = "$googleApiBaseUrl/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
            tryGet(gtxUrl)?.let { return it }
            if (System.currentTimeMillis() < googleRateLimitUntilMs) return null

            if (sl != "auto") {
                val gtxAutoUrl = "$googleApiBaseUrl/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t&q=$encoded"
                tryGet(gtxAutoUrl)?.let { return it }
                if (System.currentTimeMillis() < googleRateLimitUntilMs) return null
            }

            if (trimmed.length <= 500) {
                val clients5Url = "$clients5BaseUrl/translate_a/t?client=dict-chrome-ex&sl=$sl&tl=$tl&q=$encoded"
                tryGet(clients5Url)?.let { return it }
                if (System.currentTimeMillis() < googleRateLimitUntilMs) return null
            }
        }

        val postUrl = "$googleApiBaseUrl/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t"
        tryPost(postUrl, trimmed)?.let { return it }
        if (System.currentTimeMillis() < googleRateLimitUntilMs) return null

        if (sl != "auto") {
            val postAutoUrl = "$googleApiBaseUrl/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t"
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

    fun translateViaMyMemory(text: String, targetLang: String, sourceLang: String = "ja"): String? {
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

    fun translateViaMicrosoftEdge(text: String, tl: String): String? {
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

    fun translateViaYoudaoWeb(text: String, tl: String): String? {
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

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
