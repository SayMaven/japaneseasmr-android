package com.saymaven.downloader.japaneseasmr.data.remote

import com.saymaven.downloader.japaneseasmr.data.model.AsmrWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object DLsiteScraper {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val REFERER_JASMR = "https://japaneseasmr.com"
    private const val REFERER_DLSITE = "https://www.dlsite.com"
    private const val REFERER_ASMRONE = "https://asmr.one/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun checkUrlAccessible(url: String, referer: String, timeoutSec: Long = 4): Boolean {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .header("Range", "bytes=0-10")
                .build()
            client.newCall(req).execute().use { resp ->
                resp.code in 200..206
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Menentukan URL gambar cover terbaik dengan alur resolusi bertingkat:
     * 1. pic.weeabo0.xyz (JapaneseASMR) jika tersedia.
     * 2. DLsite HD Cover (og:image / modpub) jika tersedia.
     * 3. asmr.one cover art (mainCoverUrl).
     * 4. Fallback default ke pic.weeabo0.xyz.
     */
    fun resolveBestCoverUrl(cleanId: String, dlsiteCover: String? = null): String {
        val weeaboUrl = "https://pic.weeabo0.xyz/${cleanId}_img_main.jpg"
        if (checkUrlAccessible(weeaboUrl, referer = REFERER_JASMR)) {
            return weeaboUrl
        }

        if (!dlsiteCover.isNullOrBlank() && checkUrlAccessible(dlsiteCover, referer = REFERER_DLSITE)) {
            return dlsiteCover
        }

        // Fallback ke asmr.one
        val numId = cleanId.replace(Regex("^[A-Za-z]+"), "")
        if (numId.isNotBlank()) {
            val asmrOneCover = "https://api.asmr.one/api/cover/${numId}.jpg?type=main"
            if (checkUrlAccessible(asmrOneCover, referer = REFERER_ASMRONE)) {
                return asmrOneCover
            }
        }

        return weeaboUrl
    }

    /**
     * Fallback untuk karya yang terkena geo-blocking DLsite atau yang tabel outlinenya tidak ter-render.
     * Mengambil Judul, CV/Seiyuu, Genre/Tag, Circle, dan Rating dari asmr.one API yang tidak dibatasi region.
     */
    private fun enrichMetadataFromAsmrOne(
        cleanId: String,
        title: String,
        cv: String,
        circle: String,
        genre: String,
        ageRating: String
    ): Array<String> {
        var curTitle = title
        var curCv = cv
        var curCircle = circle
        var curGenre = genre
        var curAgeRating = ageRating

        val numId = cleanId.replace(Regex("^[A-Za-z]+"), "")
        if (numId.isBlank()) return arrayOf(curTitle, curCv, curCircle, curGenre, curAgeRating)

        val url = "https://api.asmr.one/api/work/$numId"
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER_ASMRONE)
                .header("Origin", "https://asmr.one")
                .header("Accept-Language", "ja-JP,ja;q=0.9,en;q=0.8")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: return arrayOf(curTitle, curCv, curCircle, curGenre, curAgeRating)
                    val data = JSONObject(bodyStr)

                    // 1. Judul karya jika masih kode RJ
                    if (curTitle == cleanId || curTitle.isBlank()) {
                        val name = data.optString("title").ifBlank { data.optString("name") }
                        if (name.isNotBlank()) curTitle = name.trim()
                    }

                    // 2. CV / Seiyuu
                    if (curCv == "-" || curCv.isBlank()) {
                        val vasArr = data.optJSONArray("vas")
                        if (vasArr != null && vasArr.length() > 0) {
                            val vaNames = mutableListOf<String>()
                            for (i in 0 until vasArr.length()) {
                                val vaObj = vasArr.optJSONObject(i)
                                val vaName = vaObj?.optString("name")?.trim() ?: ""
                                if (vaName.isNotBlank() && !vaNames.contains(vaName)) {
                                    vaNames.add(vaName)
                                }
                            }
                            if (vaNames.isNotEmpty()) {
                                curCv = vaNames.joinToString(", ")
                            }
                        }
                    }

                    // 3. Genre / Tags (utamakan tag Jepang melalui i18n jika ada agar seragam dengan DLsite)
                    if (curGenre == "-" || curGenre.isBlank()) {
                        val tagsArr = data.optJSONArray("tags")
                        if (tagsArr != null && tagsArr.length() > 0) {
                            val tagNames = mutableListOf<String>()
                            for (i in 0 until tagsArr.length()) {
                                val tObj = tagsArr.optJSONObject(i) ?: continue
                                val i18nJa = tObj.optJSONObject("i18n")?.optJSONObject("ja-jp")?.optString("name")
                                val tName = if (!i18nJa.isNullOrBlank()) i18nJa.trim() else tObj.optString("name").trim()
                                if (tName.isNotBlank() && !tagNames.contains(tName)) {
                                    tagNames.add(tName)
                                }
                            }
                            if (tagNames.isNotEmpty()) {
                                curGenre = tagNames.take(6).joinToString(", ")
                            }
                        }
                    }

                    // 4. Circle / Maker
                    if (curCircle == "-" || curCircle.isBlank()) {
                        val cObj = data.optJSONObject("circle")
                        val cName = cObj?.optString("name")?.trim() ?: ""
                        if (cName.isNotBlank()) {
                            curCircle = cName
                        } else {
                            val name = data.optString("name").trim()
                            if (name.isNotBlank() && name != curTitle) {
                                curCircle = name
                            }
                        }
                    }

                    // 5. Age rating
                    if (curAgeRating == "-" || curAgeRating.isBlank()) {
                        val age = data.optString("age_category_string").trim()
                        if (age.isNotBlank()) {
                            curAgeRating = age
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Abaikan kesalahan request fallback asmr.one
        }

        return arrayOf(curTitle, curCv, curCircle, curGenre, curAgeRating)
    }

    suspend fun fetchMetadata(rawRjId: String): AsmrWork = withContext(Dispatchers.IO) {
        val cleanId = rawRjId.trim().uppercase()
        var title = cleanId
        var cv = "-"
        var circle = "-"
        var genre = "-"
        var ageRating = "-"
        var dlsiteCoverUrl: String? = null

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
                    .header("Referer", REFERER_JASMR)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue
                val html = response.body?.string() ?: continue
                val doc = Jsoup.parse(html)

                // 0. Ambil Cover HD Resmi dari og:image / twitter:image DLsite
                val ogImg = doc.select("meta[property=og:image], meta[name=twitter:image]").attr("content")
                if (ogImg.isNotBlank() && dlsiteCoverUrl == null) {
                    dlsiteCoverUrl = ogImg.trim()
                }

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
                    genre = genreRows.eachText().take(6).joinToString(", ")
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

        // =========================================================================
        // FALLBACK: Ambil data dari asmr.one jika info masih ada yang kosong
        // =========================================================================
        if (title == cleanId || cv == "-" || circle == "-" || genre == "-" || ageRating == "-") {
            val enriched = enrichMetadataFromAsmrOne(cleanId, title, cv, circle, genre, ageRating)
            title = enriched[0]
            cv = enriched[1]
            circle = enriched[2]
            genre = enriched[3]
            ageRating = enriched[4]
        }

        // =========================================================================
        // RESOLUSI COVER TERBAIK (Multi-Tier HD Cover)
        // =========================================================================
        val bestCoverUrl = resolveBestCoverUrl(cleanId, dlsiteCoverUrl)

        AsmrWork(
            rjid = cleanId,
            title = if (title.isBlank()) cleanId else title,
            cv = if (cv.isBlank()) "-" else cv,
            circle = if (circle.isBlank()) "-" else circle,
            genre = if (genre.isBlank()) "-" else genre,
            ageRating = if (ageRating.isBlank()) "-" else ageRating,
            coverUrl = bestCoverUrl
        )
    }
}
