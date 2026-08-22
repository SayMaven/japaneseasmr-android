package com.saymaven.downloader.japaneseasmr.data.remote

import com.saymaven.downloader.japaneseasmr.data.model.AsmrWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object DLsiteScraper {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val REFERER = "https://japaneseasmr.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchMetadata(rawRjId: String): AsmrWork = withContext(Dispatchers.IO) {
        val cleanId = rawRjId.trim().uppercase()
        var title = cleanId
        var cv = "-"
        var circle = "-"
        var genre = "-"
        var ageRating = "-"
        val coverUrl = "https://pic.weeabo0.xyz/${cleanId}_img_main.jpg"

        val urls = listOf(
            "https://www.dlsite.com/maniax/work/=/product_id/${cleanId}.html",
            "https://www.dlsite.com/home/work/=/product_id/${cleanId}.html",
            "https://www.dlsite.com/girls/work/=/product_id/${cleanId}.html",
            "https://www.dlsite.com/pro/work/=/product_id/${cleanId}.html"
        )

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", "adultchecked=1; locale=ja_JP")
                    .header("Referer", REFERER)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue
                val html = response.body?.string() ?: continue
                val doc = Jsoup.parse(html)

                // 1. Ambil Judul
                val metaTitle = doc.select("meta[itemprop=name]").attr("content")
                val workNameEl = doc.select("#work_name").text()
                val pageTitle = doc.title()

                val rawTitle = when {
                    metaTitle.isNotBlank() -> metaTitle
                    workNameEl.isNotBlank() -> workNameEl
                    else -> pageTitle
                }

                if (rawTitle.isNotBlank()) {
                    title = rawTitle
                        .replace(Regex("\\[[^\\]]+\\]\\s*\\|\\s*DLsite.*$"), "")
                        .replace(" | DLsite", "")
                        .replace(" - DLsite", "")
                        .trim()
                }

                // 2. Ambil Circle / Maker
                val makerRows = doc.select("th:matches(サークル名|ブランド名|メーカー名|著者) + td")
                if (makerRows.isNotEmpty()) {
                    val makerLinks = makerRows.first()?.select("a")?.eachText()
                    if (!makerLinks.isNullOrEmpty()) {
                        circle = makerLinks.joinToString(", ")
                    } else {
                        circle = makerRows.text().trim()
                    }
                }

                if (circle == "-" || circle.isBlank()) {
                    val brandMeta = doc.select("div[itemprop=brand] meta[itemprop=name]").attr("content")
                    if (brandMeta.isNotBlank()) {
                        circle = brandMeta.trim()
                    }
                }

                // 3. Ambil CV / Voice Actor
                val cvRows = doc.select("th:matches(声優|キャラクターボイス|ボイス|キャスト) + td")
                if (cvRows.isNotEmpty()) {
                    val cvLinks = cvRows.first()?.select("a")?.eachText()
                    if (!cvLinks.isNullOrEmpty()) {
                        cv = cvLinks.joinToString(", ")
                    } else {
                        val text = cvRows.text().trim()
                        if (text.isNotBlank()) cv = text
                    }
                }

                // Fallback CV dari deskripsi/teks
                if (cv == "-" || cv.isBlank()) {
                    val desc = doc.select("meta[itemprop=description]").attr("content")
                    val combinedText = "$title $desc ${doc.body().text()}"
                    val cvPattern = Pattern.compile(
                        "(?:CV|声優|キャラクターボイス|ボイス|キャスト|CV\\.|CV：|CV:)\\s*[：:\\s【\\[（(「『]*([^\\n\\r,、/】\\]）)」』\\s]{2,30})",
                        Pattern.CASE_INSENSITIVE
                    )
                    val matcher = cvPattern.matcher(combinedText)
                    val foundCvs = mutableListOf<String>()
                    while (matcher.find()) {
                        val c = matcher.group(1)?.trim()
                        if (!c.isNullOrBlank() && c.length in 2..30 && !c.startsWith("http") && !foundCvs.contains(c)) {
                            foundCvs.add(c)
                        }
                    }
                    if (foundCvs.isNotEmpty()) {
                        cv = foundCvs.take(4).joinToString(", ")
                    }
                }

                // 4. Ambil Genre / Tags
                val genreRows = doc.select("th:matches(ジャンル) + td a")
                if (genreRows.isNotEmpty()) {
                    genre = genreRows.eachText().take(5).joinToString(", ")
                }

                // 5. Ambil Age Rating
                val ageRow = doc.select("th:matches(年齢指定) + td, .work_genre")
                if (ageRow.isNotEmpty()) {
                    val ageText = ageRow.text()
                    ageRating = when {
                        ageText.contains("18") || ageText.contains("R-18") || ageText.contains("R18") -> "R18"
                        ageText.contains("全年齢") || ageText.contains("All") -> "All-Ages"
                        else -> ageText.take(15).trim()
                    }
                }

                // Jika berhasil mendapatkan judul yang valid dari DLsite, hentikan pencarian URL lain
                if (title != cleanId && title.isNotBlank()) {
                    break
                }
            } catch (e: Exception) {
                // Lanjut ke URL berikutnya
            }
        }

        AsmrWork(
            rjid = cleanId,
            title = if (title.isBlank()) cleanId else title,
            cv = if (cv.isBlank()) "-" else cv,
            circle = if (circle.isBlank()) "-" else circle,
            genre = if (genre.isBlank()) "-" else genre,
            ageRating = if (ageRating.isBlank()) "-" else ageRating,
            coverUrl = coverUrl
        )
    }
}
