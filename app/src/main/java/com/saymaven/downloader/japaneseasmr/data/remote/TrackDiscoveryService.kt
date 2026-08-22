package com.saymaven.downloader.japaneseasmr.data.remote

import com.saymaven.downloader.japaneseasmr.data.model.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object TrackDiscoveryService {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val REFERER = "https://japaneseasmr.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun checkUrlExists(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Referer", REFERER)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-10")
                .build()
            client.newCall(request).execute().use { response ->
                response.code in 200..206
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun discoverAllTracks(rjid: String): List<TrackInfo> = withContext(Dispatchers.IO) {
        val cleanId = rjid.trim()
        if (cleanId.startsWith("http://") || cleanId.startsWith("https://")) {
            return@withContext listOf(TrackInfo("Track 1", cleanId))
        }

        // 1. Cek Master HLS (.m3u8)
        val m3u8Url = "https://v.weeab0o.xyz/${cleanId}.m3u8"
        if (checkUrlExists(m3u8Url)) {
            return@withContext listOf(TrackInfo("Track 1", m3u8Url, isHls = true))
        }

        val foundTracks = mutableListOf<TrackInfo>()
        val seenUrls = mutableSetOf<String>()

        // 2. Cek Track 1 (.mp3)
        val t1Candidates = listOf(
            "https://v.weeab0o.xyz/${cleanId}.mp3",
            "https://v.weeab0o.xyz/${cleanId}%201.mp3",
            "https://v.weeab0o.xyz/${cleanId}_1.mp3",
            "https://v.weeab0o.xyz/${cleanId}-1.mp3"
        )

        var t1Url: String? = null
        for (url in t1Candidates) {
            if (checkUrlExists(url)) {
                t1Url = url
                break
            }
        }

        if (t1Url != null) {
            foundTracks.add(TrackInfo("Track 1", t1Url, isHls = false))
            seenUrls.add(t1Url)
        } else {
            foundTracks.add(TrackInfo("Track 1", t1Candidates[0], isHls = false))
            seenUrls.add(t1Candidates[0])
        }

        // 3. Cek Multi-track 2 s/d 20 secara paralel
        coroutineScope {
            val trackJobs = (2..20).map { num ->
                async {
                    val candidates = listOf(
                        "https://v.weeab0o.xyz/${cleanId}%20${num}.mp3",
                        "https://v.weeab0o.xyz/${cleanId}_${num}.mp3",
                        "https://v.weeab0o.xyz/${cleanId}-${num}.mp3",
                        "https://v.weeab0o.xyz/${cleanId}${num}.mp3"
                    )
                    var matched: String? = null
                    for (c in candidates) {
                        if (checkUrlExists(c)) {
                            matched = c
                            break
                        }
                    }
                    if (matched != null) TrackInfo("Track $num", matched, isHls = false) else null
                }
            }

            val numberedResults = trackJobs.awaitAll().filterNotNull()
            foundTracks.addAll(numberedResults)
            numberedResults.forEach { seenUrls.add(it.url) }
        }

        // 4. Cek Omake, Bonus, Tokuten, EX secara paralel
        val omakePatterns = listOf(
            "Omake" to "https://v.weeab0o.xyz/${cleanId}omake.mp3",
            "Omake" to "https://v.weeab0o.xyz/${cleanId}%20omake.mp3",
            "Omake" to "https://v.weeab0o.xyz/${cleanId}_omake.mp3",
            "Omake" to "https://v.weeab0o.xyz/${cleanId}-omake.mp3",
            "Omake 1" to "https://v.weeab0o.xyz/${cleanId}omake1.mp3",
            "Omake 1" to "https://v.weeab0o.xyz/${cleanId}%20omake%201.mp3",
            "Omake 2" to "https://v.weeab0o.xyz/${cleanId}omake2.mp3",
            "Omake 2" to "https://v.weeab0o.xyz/${cleanId}%20omake%202.mp3",
            "Bonus" to "https://v.weeab0o.xyz/${cleanId}bonus.mp3",
            "Bonus" to "https://v.weeab0o.xyz/${cleanId}%20bonus.mp3",
            "Bonus" to "https://v.weeab0o.xyz/${cleanId}_bonus.mp3",
            "Tokuten" to "https://v.weeab0o.xyz/${cleanId}tokuten.mp3",
            "Tokuten" to "https://v.weeab0o.xyz/${cleanId}%20tokuten.mp3",
            "EX" to "https://v.weeab0o.xyz/${cleanId}%20ex.mp3",
            "EX" to "https://v.weeab0o.xyz/${cleanId}ex.mp3",
            "EX" to "https://v.weeab0o.xyz/${cleanId}_ex.mp3",
            "EX" to "https://v.weeab0o.xyz/${cleanId}-ex.mp3",
            "EX 1" to "https://v.weeab0o.xyz/${cleanId}%20ex%201.mp3",
            "EX 1" to "https://v.weeab0o.xyz/${cleanId}%20ex1.mp3",
            "EX 2" to "https://v.weeab0o.xyz/${cleanId}%20ex%202.mp3",
            "EX 2" to "https://v.weeab0o.xyz/${cleanId}%20ex2.mp3"
        )

        coroutineScope {
            val omakeJobs = omakePatterns.map { (name, url) ->
                async {
                    if (!seenUrls.contains(url) && checkUrlExists(url)) {
                        TrackInfo(name, url, isHls = false)
                    } else null
                }
            }

            val omakeResults = omakeJobs.awaitAll().filterNotNull()
            omakeResults.forEach { t ->
                if (!seenUrls.contains(t.url)) {
                    foundTracks.add(t)
                    seenUrls.add(t.url)
                }
            }
        }

        foundTracks
    }
}
