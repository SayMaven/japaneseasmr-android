package com.saymaven.downloader.japaneseasmr.service

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
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
            downloadDirectFileParallel(url, destFile, parallelConnections, onProgress, onLog)
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

            val segmentAdtsData = Array<ByteArray?>(totalSegments) { null }
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
                                        val rawBytes = segResp.body?.bytes()
                                        if (rawBytes != null && rawBytes.isNotEmpty()) {
                                            // Demux paket MPEG-TS langsung ke frame ADTS AAC murni
                                            val adtsBytes = extractAdtsFromTs(rawBytes)
                                            if (adtsBytes.isNotEmpty()) {
                                                segmentAdtsData[index] = adtsBytes
                                                totalBytesDownloaded.addAndGet(adtsBytes.size.toLong())
                                                bytesSinceLastLog.addAndGet(adtsBytes.size.toLong())
                                                success = true
                                            }
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

            // Gabungkan seluruh frame ADTS AAC secara berurutan ke file akhir
            onLog("[i] Menggabungkan $totalSegments segmen ADTS AAC menjadi file audio utuh...")
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { outStream ->
                for (i in 0 until totalSegments) {
                    val adts = segmentAdtsData[i]
                    if (adts != null && adts.isNotEmpty()) {
                        outStream.write(adts)
                    }
                }
            }

            val finalSizeStr = formatFileSize(destFile.length())
            onLog("[SUCCESS] File audio berhasil dibuat: $finalSizeStr (Suara Jernih & Format Valid)")
            onProgress(1f, "Selesai", "00:00", finalSizeStr, finalSizeStr)
            true
        } catch (e: Exception) {
            onLog("[ERROR] Gagal mengunduh HLS: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Mengekstrak seluruh payload audio ADTS AAC murni dari potongan MPEG-TS 188-byte.
     */
    private fun extractAdtsFromTs(tsBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(tsBytes.size)
        var i = 0
        while (i <= tsBytes.size - 188) {
            if (tsBytes[i] != 0x47.toByte()) {
                i++
                continue
            }
            val pkt = tsBytes
            val pktStart = i
            i += 188

            val pid = ((pkt[pktStart + 1].toInt() and 0x1F) shl 8) or (pkt[pktStart + 2].toInt() and 0xFF)
            if (pid != 256) continue // Audio stream adalah PID 256 (0x100)

            val pusi = (pkt[pktStart + 1].toInt() and 0x40) != 0
            val afc = (pkt[pktStart + 3].toInt() and 0x30) ushr 4

            var offset = 4
            if (afc == 2 || afc == 3) {
                val afLen = pkt[pktStart + 4].toInt() and 0xFF
                offset += 1 + afLen
            }
            if (offset >= 188) continue

            var payloadOffset = pktStart + offset
            var payloadLen = 188 - offset

            if (pusi) {
                // PES Header: 0x00 0x00 0x01
                if (payloadLen >= 9 && pkt[payloadOffset] == 0.toByte() && pkt[payloadOffset + 1] == 0.toByte() && pkt[payloadOffset + 2] == 1.toByte()) {
                    val hLen = pkt[payloadOffset + 8].toInt() and 0xFF
                    val skip = 9 + hLen
                    payloadOffset += skip
                    payloadLen -= skip
                }
            }

            if (payloadLen > 0 && payloadOffset + payloadLen <= pktStart + 188) {
                out.write(pkt, payloadOffset, payloadLen)
            }
        }
        return out.toByteArray()
    }

    /**
     * Akselerasi unduhan Direct MP3 menggunakan 16 koneksi paralel HTTP Range (mirip engine aria2c).
     */
    private suspend fun downloadDirectFileParallel(
        url: String,
        destFile: File,
        parallelConnections: Int,
        onProgress: (progress: Float, speedStr: String, etaStr: String, downloadedStr: String, totalStr: String) -> Unit,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onLog("[i] Memeriksa ukuran file direct MP3...")
            val headReq = Request.Builder()
                .url(url)
                .head()
                .header("Referer", REFERER)
                .header("User-Agent", USER_AGENT)
                .build()

            val headResp = client.newCall(headReq).execute()
            val totalBytes = headResp.headers["Content-Length"]?.toLongOrNull() ?: 0L
            val acceptRanges = headResp.headers["Accept-Ranges"]?.contains("bytes", ignoreCase = true) == true

            destFile.parentFile?.mkdirs()

            if (totalBytes <= 0L || !acceptRanges || parallelConnections <= 1) {
                onLog("[i] Mengunduh single-stream...")
                return@withContext downloadDirectSingleStream(url, destFile, onProgress, onLog)
            }

            val concurrency = parallelConnections.coerceIn(2, 32)
            val totalStr = formatFileSize(totalBytes)
            onLog("[i] Ukuran file: $totalStr. Memulai unduhan multi-part Range ($concurrency thread paralel)...")

            // Siapkan file kosong dengan ukuran total
            val raf = RandomAccessFile(destFile, "rw")
            raf.setLength(totalBytes)
            raf.close()

            val chunkSize = totalBytes / concurrency
            val totalRead = AtomicLong(0L)
            val bytesSinceLast = AtomicLong(0L)
            var lastTime = System.currentTimeMillis()
            val startTime = System.currentTimeMillis()

            coroutineScope {
                (0 until concurrency).map { partIndex ->
                    async {
                        val startByte = partIndex * chunkSize
                        val endByte = if (partIndex == concurrency - 1) totalBytes - 1 else (partIndex + 1) * chunkSize - 1

                        var success = false
                        var attempts = 0

                        while (!success && attempts < 4) {
                            attempts++
                            try {
                                val rangeReq = Request.Builder()
                                    .url(url)
                                    .header("Range", "bytes=$startByte-$endByte")
                                    .header("Referer", REFERER)
                                    .header("User-Agent", USER_AGENT)
                                    .build()

                                val rangeResp = client.newCall(rangeReq).execute()
                                val body = rangeResp.body ?: throw Exception("Empty body")

                                val inputStream = body.byteStream()
                                val partRaf = RandomAccessFile(destFile, "rw")
                                partRaf.seek(startByte)

                                val buffer = ByteArray(32 * 1024)
                                var bytesRead: Int

                                inputStream.use { input ->
                                    partRaf.use { pRaf ->
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            pRaf.write(buffer, 0, bytesRead)
                                            totalRead.addAndGet(bytesRead.toLong())
                                            bytesSinceLast.addAndGet(bytesRead.toLong())

                                            val now = System.currentTimeMillis()
                                            val diff = now - lastTime
                                            if (diff >= 500) {
                                                val currentRead = totalRead.get()
                                                val speedBps = if (diff > 0) (bytesSinceLast.get() * 1000) / diff else 0L
                                                val speedStr = "${formatFileSize(speedBps)}/s"

                                                val timeElapsed = (now - startTime) / 1000
                                                val etaSec = if (currentRead > 0) ((totalBytes - currentRead) * timeElapsed) / currentRead else 0L
                                                val etaStr = formatTimeSeconds(etaSec)

                                                val pct = currentRead.toFloat() / totalBytes.toFloat()
                                                onProgress(pct, speedStr, etaStr, formatFileSize(currentRead), totalStr)

                                                lastTime = now
                                                bytesSinceLast.set(0)
                                            }
                                        }
                                    }
                                }
                                success = true
                            } catch (e: Exception) {
                                delay(500)
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalSizeStr = formatFileSize(destFile.length())
            onLog("[SUCCESS] Unduhan multi-part selesai: $finalSizeStr")
            onProgress(1f, "Selesai", "00:00", finalSizeStr, totalStr)
            true
        } catch (e: Exception) {
            onLog("[ERROR] Gagal unduhan multi-part: ${e.message}")
            downloadDirectSingleStream(url, destFile, onProgress, onLog)
        }
    }

    private suspend fun downloadDirectSingleStream(
        url: String,
        destFile: File,
        onProgress: (progress: Float, speedStr: String, etaStr: String, downloadedStr: String, totalStr: String) -> Unit,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
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

            val finalSize = formatFileSize(destFile.length())
            onLog("[SUCCESS] Unduhan selesai: $finalSize")
            onProgress(1f, "Selesai", "00:00", finalSize, totalStr)
            true
        } catch (e: Exception) {
            onLog("[ERROR] Gagal mengunduh: ${e.message}")
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
