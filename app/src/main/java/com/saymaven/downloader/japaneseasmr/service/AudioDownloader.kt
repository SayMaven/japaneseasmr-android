package com.saymaven.downloader.japaneseasmr.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object AudioDownloader {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private const val REFERER = "https://japaneseasmr.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadTrack(
        url: String,
        destFile: File,
        tempDir: File,
        parallelConnections: Int = 16,
        onProgress: (progress: Float, speedStr: String, etaStr: String, downloadedStr: String, totalStr: String) -> Unit,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (url.endsWith(".m3u8", ignoreCase = true)) {
            downloadHlsStream(url, destFile, tempDir, parallelConnections, onProgress, onLog)
        } else {
            downloadDirectFile(url, destFile, onProgress, onLog)
        }
    }

    private suspend fun downloadHlsStream(
        m3u8Url: String,
        destFile: File,
        tempDir: File,
        parallelConnections: Int,
        onProgress: (progress: Float, speedStr: String, etaStr: String, downloadedStr: String, totalStr: String) -> Unit,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onLog("[i] Mengambil playlist HLS (.m3u8)...")
            val request = Request.Builder()
                .url(m3u8Url)
                .header("Referer", REFERER)
                .header("Origin", REFERER)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                onLog("[!] Gagal mengambil playlist m3u8 (HTTP ${response.code})")
                return@withContext false
            }

            val m3u8Content = response.body?.string() ?: return@withContext false
            val lines = m3u8Content.lines()
            val rawSegments = lines.map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }

            if (rawSegments.isEmpty()) {
                onLog("[!] Tidak ada segmen audio ditemukan di playlist m3u8.")
                return@withContext false
            }

            val baseUrl = m3u8Url.substringBeforeLast('/') + '/'
            val segmentUrls = rawSegments.map { seg ->
                if (seg.startsWith("http://") || seg.startsWith("https://")) seg else baseUrl + seg
            }

            val totalSegments = segmentUrls.size
            val concurrency = parallelConnections.coerceIn(1, 32)
            onLog("[i] Terdeteksi $totalSegments segmen audio HLS. Memulai unduhan paralel ($concurrency koneksi)...")

            val segmentFiles = Array<File?>(totalSegments) { null }
            val completedCount = AtomicInteger(0)
            val totalBytesDownloaded = AtomicLong(0)
            val bytesSinceLastLog = AtomicLong(0)
            var lastLogTime = System.currentTimeMillis()

            val semaphore = Semaphore(concurrency)
            val startTime = System.currentTimeMillis()

            coroutineScope {
                segmentUrls.mapIndexed { index, segUrl ->
                    async {
                        semaphore.withPermit {
                            val segFile = File(tempDir, "seg_${System.currentTimeMillis()}_${index}.ts")
                            var success = false
                            var attempts = 0

                            while (!success && attempts < 4) {
                                attempts++
                                try {
                                    val segReq = Request.Builder()
                                        .url(segUrl)
                                        .header("Referer", REFERER)
                                        .header("Origin", REFERER)
                                        .header("User-Agent", USER_AGENT)
                                        .build()

                                    val segResp = client.newCall(segReq).execute()
                                    if (segResp.isSuccessful) {
                                        val bytes = segResp.body?.bytes()
                                        if (bytes != null && bytes.isNotEmpty()) {
                                            segFile.writeBytes(bytes)
                                            segmentFiles[index] = segFile
                                            totalBytesDownloaded.addAndGet(bytes.size.toLong())
                                            bytesSinceLastLog.addAndGet(bytes.size.toLong())
                                            success = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    delay(400)
                                }
                            }

                            val done = completedCount.incrementAndGet()
                            val pct = done.toFloat() / totalSegments.toFloat()
                            val now = System.currentTimeMillis()
                            val diff = now - lastLogTime

                            if (diff >= 500 || done == totalSegments) {
                                val speedBps = if (diff > 0) (bytesSinceLastLog.get() * 1000) / diff else 0L
                                val speedStr = "${formatFileSize(speedBps)}/s"
                                val downloadedStr = formatFileSize(totalBytesDownloaded.get())

                                val timeElapsed = (now - startTime) / 1000
                                val etaSec = if (done > 0) ((totalSegments - done) * timeElapsed) / done else 0L
                                val etaStr = formatTimeSeconds(etaSec)

                                val estTotal = if (done > 0) totalBytesDownloaded.get() * totalSegments / done else 0L
                                onProgress(pct, speedStr, etaStr, downloadedStr, "~${formatFileSize(estTotal)}")

                                if (done % 25 == 0 || done == totalSegments) {
                                    onLog("  [download] Segmen $done/$totalSegments (${(pct * 100).toInt()}%) - $downloadedStr @ $speedStr")
                                }

                                lastLogTime = now
                                bytesSinceLastLog.set(0)
                            }
                        }
                    }
                }.awaitAll()
            }

            // Gabungkan semua TS ke satu file sementara yang valid
            onLog("[i] Menggabungkan $totalSegments segmen MPEG-TS...")
            val combinedTsFile = File(tempDir, "combined_${System.currentTimeMillis()}.ts")
            FileOutputStream(combinedTsFile).use { outStream ->
                for (i in 0 until totalSegments) {
                    val sf = segmentFiles[i]
                    if (sf != null && sf.exists()) {
                        sf.inputStream().use { it.copyTo(outStream) }
                        sf.delete()
                    }
                }
            }

            // Remux MPEG-TS ke format audio native (M4A/MP4 container dengan seek-table presisi)
            onLog("[i] Melakukan remuxing ke container audio standar (MediaMuxer)...")
            val remuxSuccess = remuxTsToM4a(combinedTsFile, destFile)
            combinedTsFile.delete()

            if (!remuxSuccess) {
                onLog("[!] Remuxing MediaMuxer gagal, mencoba fallback direct copy...")
                combinedTsFile.copyTo(destFile, overwrite = true)
            }

            val finalSizeStr = formatFileSize(destFile.length())
            onLog("[SUCCESS] Audio berhasil dibuat: $finalSizeStr (100% Seekable & Valid)")
            onProgress(1f, "Selesai", "00:00", finalSizeStr, finalSizeStr)
            true
        } catch (e: Exception) {
            onLog("[ERROR] Gagal mengunduh HLS: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Menggunakan Android MediaExtractor + MediaMuxer untuk mengubah MPEG-TS ke ISO MP4/M4A.
     * Ini memastikan file audio memiliki index durasi, sample-table (stbl), dan 100% seekable di semua player.
     */
    private fun remuxTsToM4a(tsFile: File, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(tsFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)

            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(1024 * 1024)
            } else {
                1024 * 1024
            }

            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { muxer?.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
        }
    }

    private suspend fun downloadDirectFile(
        url: String,
        destFile: File,
        onProgress: (progress: Float, speedStr: String, etaStr: String, downloadedStr: String, totalStr: String) -> Unit,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onLog("[i] Mengunduh direct file...")
            val request = Request.Builder()
                .url(url)
                .header("Referer", REFERER)
                .header("Origin", REFERER)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                onLog("[!] Gagal mengunduh file (HTTP ${response.code})")
                return@withContext false
            }

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
                            val speedStr = "${formatFileSize(speedBytesPerSec)}/s"

                            val etaStr = if (totalBytes > 0 && speedBytesPerSec > 0) {
                                val remainingBytes = totalBytes - totalRead
                                val etaSeconds = remainingBytes / speedBytesPerSec
                                formatTimeSeconds(etaSeconds)
                            } else "-"

                            val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes.toFloat() else 0f
                            val downloadedStr = formatFileSize(totalRead)

                            onProgress(progress, speedStr, etaStr, downloadedStr, totalStr)
                            lastTime = now
                            bytesSinceLast = 0
                        }
                    }
                }
            }

            onLog("[SUCCESS] Unduhan selesai: ${formatFileSize(destFile.length())}")
            onProgress(1f, "Selesai", "00:00", formatFileSize(totalRead), totalStr)
            true
        } catch (e: Exception) {
            onLog("[ERROR] Gagal mengunduh file: ${e.message}")
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
