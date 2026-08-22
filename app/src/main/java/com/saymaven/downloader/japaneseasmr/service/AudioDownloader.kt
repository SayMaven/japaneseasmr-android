package com.saymaven.downloader.japaneseasmr.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AudioDownloader {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val REFERER = "https://japaneseasmr.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadFile(
        url: String,
        destFile: File,
        onProgress: (progress: Float, speedStr: String, etaStr: String, downloadedStr: String, totalStr: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Referer", REFERER)
                .header("Origin", REFERER)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()
            val totalStr = formatFileSize(totalBytes)

            destFile.parentFile?.mkdirs()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(destFile)

            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            var totalRead = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLast = 0L
            var speedStr = "-"
            var etaStr = "-"

            outputStream.use { out ->
                inputStream.use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        bytesSinceLast += bytesRead

                        val now = System.currentTimeMillis()
                        val diff = now - lastTime
                        if (diff >= 500) {
                            val speedBytesPerSec = (bytesSinceLast * 1000) / diff
                            speedStr = "${formatFileSize(speedBytesPerSec)}/s"

                            if (totalBytes > 0 && speedBytesPerSec > 0) {
                                val remainingBytes = totalBytes - totalRead
                                val etaSeconds = remainingBytes / speedBytesPerSec
                                etaStr = formatTimeSeconds(etaSeconds)
                            }

                            val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes.toFloat() else 0f
                            val downloadedStr = formatFileSize(totalRead)

                            onProgress(progress, speedStr, etaStr, downloadedStr, totalStr)
                            lastTime = now
                            bytesSinceLast = 0
                        }
                    }
                }
            }

            onProgress(1f, "Selesai", "00:00", formatFileSize(totalRead), totalStr)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun downloadImage(url: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Referer", REFERER)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false
            val bytes = response.body?.bytes() ?: return@withContext false
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatTimeSeconds(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }
}
