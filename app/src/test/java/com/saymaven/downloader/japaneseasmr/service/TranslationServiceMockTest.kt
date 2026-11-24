package com.saymaven.downloader.japaneseasmr.service

import com.saymaven.downloader.japaneseasmr.service.translation.LlmTranslationClient
import com.saymaven.downloader.japaneseasmr.service.translation.WebTranslationProviders
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslationServiceMockTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("").toString().trimEnd('/')
        WebTranslationProviders.googleApiBaseUrl = baseUrl
        WebTranslationProviders.clients5BaseUrl = baseUrl
        WebTranslationProviders.resetRateLimit()
    }

    @After
    fun tearDown() {
        WebTranslationProviders.googleApiBaseUrl = "https://translate.googleapis.com"
        WebTranslationProviders.clients5BaseUrl = "https://clients5.google.com"
        WebTranslationProviders.resetRateLimit()
        server.shutdown()
    }

    @Test
    fun `translateViaGoogleWeb parses gtx array response`() {
        val gtxBody = """[[["Selamat pagi","おはようございます",null,null,10]],null,"ja"]"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(gtxBody))

        val result = WebTranslationProviders.translateViaGoogleWeb("おはようございます", tl = "id", sl = "ja")

        assertEquals("Selamat pagi", result)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("client=gtx"))
        assertTrue(recorded.path!!.contains("tl=id"))
    }

    @Test
    fun `translateViaGoogleWeb falls back to clients5 when gtx fails`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""["Pembersihan Telinga"]"""))

        val result = WebTranslationProviders.translateViaGoogleWeb("耳かき", tl = "id", sl = "ja")

        assertEquals("Pembersihan Telinga", result)
        assertEquals(3, server.requestCount)
        val thirdRequest = run {
            server.takeRequest()
            server.takeRequest()
            server.takeRequest()
        }
        assertTrue(thirdRequest.path!!.contains("client=dict-chrome-ex"))
    }

    @Test
    fun `translateViaGoogleWeb activates cooldown on HTTP 429`() {
        server.enqueue(MockResponse().setResponseCode(429))

        val result = WebTranslationProviders.translateViaGoogleWeb("テスト", tl = "id", sl = "ja")

        assertNull(result)
        assertTrue(WebTranslationProviders.googleRateLimitUntilMs > System.currentTimeMillis())
        assertEquals("HTTP 429", WebTranslationProviders.lastNetworkError)

        // Subsequent call must short-circuit without hitting MockWebServer
        val second = WebTranslationProviders.translateViaGoogleWeb("テスト2", tl = "id", sl = "ja")
        assertNull(second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `callOpenAiCompatibleLlm parses chat completions and handles errors`() {
        val okJson = """{"choices":[{"message":{"content":"Bisikan Lembut"}}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(okJson))

        var capturedError: String? = null
        val translated = LlmTranslationClient.callOpenAiCompatibleLlm(
            baseUrl = server.url("/v1").toString(),
            apiKey = "sk-test-key",
            model = "gpt-4o-mini",
            systemPrompt = "Translate to Indonesian",
            userPrompt = "囁き",
            onError = { capturedError = it }
        )

        assertEquals("Bisikan Lembut", translated)
        assertNull(capturedError)

        server.enqueue(MockResponse().setResponseCode(503))
        val failed = LlmTranslationClient.callOpenAiCompatibleLlm(
            baseUrl = server.url("/v1").toString(),
            apiKey = "sk-test-key",
            model = "gpt-4o-mini",
            systemPrompt = "Translate",
            userPrompt = "test",
            onError = { capturedError = it }
        )
        assertNull(failed)
        assertNotNull(capturedError)
        assertTrue(capturedError!!.contains("503"))
    }

    @Test
    fun `detectSourceLanguage distinguishes Japanese, Chinese, and English`() {
        assertEquals("ja", TranslationService.detectSourceLanguage("耳かき・ささやきボイス"))
        assertEquals("zh-CN", TranslationService.detectSourceLanguage("这只小猫很可爱"))
        assertEquals("en", TranslationService.detectSourceLanguage("Gentle Whispering Ear Cleaning"))
    }
}
