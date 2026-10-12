package com.saymaven.downloader.japaneseasmr.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object UniversalCodeResolver {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Mengekstrak Kode RJ dari URL postingan japaneseasmr.com (misal: https://japaneseasmr.com/150854/).
     */
    suspend fun resolveJapaneseAsmrUrlToRjId(url: String): String? = withContext(Dispatchers.IO) {
        if (!url.contains("japaneseasmr.com", ignoreCase = true)) return@withContext null
        try {
            val req = Request.Builder()
                .url(url.trim())
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val html = resp.body?.string() ?: return@withContext null

                // 1. Cari stream url weeab0o
                val m1 = Pattern.compile("https?://v\\.weeab0o\\.xyz/(RJ\\d+)", Pattern.CASE_INSENSITIVE).matcher(html)
                if (m1.find()) return@withContext m1.group(1)?.uppercase()

                // 2. Cari link dlsite product_id
                val m2 = Pattern.compile("product_id/(RJ\\d+)", Pattern.CASE_INSENSITIVE).matcher(html)
                if (m2.find()) return@withContext m2.group(1)?.uppercase()

                // 3. Cari pola teks RJ6-8 digit
                val m3 = Pattern.compile("\\b(RJ\\d{6,8})\\b", Pattern.CASE_INSENSITIVE).matcher(html)
                if (m3.find()) return@withContext m3.group(1)?.uppercase()
            }
        } catch (e: Exception) {
            // Abaikan kesalahan koneksi
        }
        null
    }

    /**
     * Menyelesaikan satu token (bisa berupa URL asmr.one, japaneseasmr.com, DLsite, kode RJ, atau angka murni).
     */
    suspend fun resolveSingleInputToRjId(token: String): String? = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.isBlank()) return@withContext null

        // 1. Tautan asmr.one (misal https://asmr.one/work/RJ01220411 atau asmr.one/work/386444)
        if (clean.contains("asmr.one", ignoreCase = true)) {
            val p = Pattern.compile("asmr\\.one/(?:work|api/work)/([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE)
            val m = p.matcher(clean)
            if (m.find()) {
                var code = m.group(1)?.uppercase() ?: ""
                if (!code.startsWith("RJ")) {
                    code = "RJ$code"
                }
                return@withContext code
            }
        }

        // 2. Tautan postingan japaneseasmr.com
        if (clean.contains("japaneseasmr.com", ignoreCase = true)) {
            val resolved = resolveJapaneseAsmrUrlToRjId(clean)
            if (resolved != null) return@withContext resolved
        }

        // 3. Tautan DLsite resmi
        if (clean.contains("dlsite.com", ignoreCase = true)) {
            val p = Pattern.compile("product_id/([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE)
            val m = p.matcher(clean)
            if (m.find()) {
                var code = m.group(1)?.uppercase() ?: ""
                if (!code.startsWith("RJ") && code.matches(Regex("\\d+"))) {
                    code = "RJ$code"
                }
                return@withContext code
            }
        }

        // 4. Deteksi kode RJ / VJ / BJ atau angka numerik murni
        val pCode = Pattern.compile("((?:RJ|VJ|BJ)\\d+|\\b\\d{5,8}\\b)", Pattern.CASE_INSENSITIVE)
        val mCode = pCode.matcher(clean)
        if (mCode.find()) {
            var res = mCode.group(1)?.uppercase() ?: ""
            if (res.matches(Regex("^\\d+$"))) {
                res = "RJ$res"
            }
            return@withContext res
        }

        if (clean.matches(Regex("^(?:RJ|VJ|BJ)\\d+$", RegexOption.IGNORE_CASE))) {
            return@withContext clean.uppercase()
        }

        if (clean.matches(Regex("^\\d+$"))) {
            return@withContext "RJ${clean.uppercase()}"
        }

        null
    }

    /**
     * Memproses multi-input teks (bisa campuran URL, koma, spasi, baris baru).
     */
    suspend fun resolveAllInputs(rawInput: String): List<String> = withContext(Dispatchers.IO) {
        val tokens = rawInput.split(Regex("[\\s,;]+")).map { it.trim() }.filter { it.isNotBlank() }
        val results = mutableListOf<String>()

        for (token in tokens) {
            val rjid = resolveSingleInputToRjId(token)
            if (rjid != null && !results.contains(rjid)) {
                results.add(rjid)
            }
        }

        results
    }
}
