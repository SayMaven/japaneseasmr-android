package com.saymaven.downloader.japaneseasmr.service.translation

import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object LlmTranslationClient {

    private val llmClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 8
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    fun callClaudeAnthropic(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        onError: (String) -> Unit
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
                    onError("LLM Claude HTTP ${resp.code}")
                    return null
                }
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val contentArray = json.optJSONArray("content") ?: return null
                if (contentArray.length() == 0) return null
                contentArray.getJSONObject(0).optString("text")?.trim()
            }
        } catch (e: Exception) {
            onError("LLM Claude gagal: ${e.javaClass.simpleName}")
            null
        }
    }

    fun callOpenAiCompatibleLlm(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        onError: (String) -> Unit
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
                    onError("LLM API HTTP ${resp.code}")
                    return null
                }
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val choices = json.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                choices.getJSONObject(0).optJSONObject("message")?.optString("content")?.trim()
            }
        } catch (e: Exception) {
            onError("LLM API gagal: ${e.javaClass.simpleName}")
            null
        }
    }
}
