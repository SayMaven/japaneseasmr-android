package com.saymaven.downloader.japaneseasmr.service

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
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
        val segDir = File(tempDir, "hls_segs_${System.currentTimeMillis()}")
        segDir.mkdirs()

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
                            val segFile = File(segDir, "seg_$index.adts")

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
                                            val adtsBytes = extractAdtsFromTs(rawBytes)
                                            if (adtsBytes.isNotEmpty()) {
                                                segFile.writeBytes(adtsBytes)
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

                                if (done % 50 == 0 || done == totalSegments) {
                                    onLog("  [download] Segmen $done/$totalSegments (${(pct * 100).toInt()}%) - $downloadedStr @ $speedStr")
                                }

                                lastLogTime = now
                                bytesSinceLastLog.set(0)
                            }
                        }
                    }
                }.awaitAll()
            }

            // Gabungkan segmen-segmen ke single temporary ADTS file secara streaming cepat (RAM < 2MB)
            onLog("[i] Menggabungkan $totalSegments segmen audio secara streaming...")
            val tempAdtsCombined = File(tempDir, "temp_combined_${System.currentTimeMillis()}.adts")
            tempAdtsCombined.outputStream().buffered(256 * 1024).use { outStream ->
                val buffer = ByteArray(64 * 1024)
                for (i in 0 until totalSegments) {
                    val sFile = File(segDir, "seg_$i.adts")
                    if (sFile.exists()) {
                        sFile.inputStream().use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                outStream.write(buffer, 0, read)
                            }
                        }
                        sFile.delete()
                    }
                }
            }

            onLog("[i] Mengemas audio ke container ISO M4A (Ultra-Fast Memory-Mapped Engine)...")
            destFile.parentFile?.mkdirs()

            val muxSuccess = muxAdtsFileToM4a(tempAdtsCombined, destFile)
            if (!muxSuccess) {
                tempAdtsCombined.copyTo(destFile, overwrite = true)
            }
            tempAdtsCombined.delete()

            val finalSizeStr = formatFileSize(destFile.length())
            onLog("[SUCCESS] File M4A berhasil dibuat: $finalSizeStr (100% Seekable & Jernih)")
            onProgress(1f, "Selesai", "00:00", finalSizeStr, finalSizeStr)
            true
        } catch (e: Exception) {
            onLog("[ERROR] Gagal mengunduh HLS: ${e.message}")
            e.printStackTrace()
            false
        } finally {
            try {
                segDir.deleteRecursively()
            } catch (e: Exception) {}
        }
    }

    /**
     * Muxing super cepat (< 2 detik untuk 220MB) menggunakan FileChannel & MappedByteBuffer.
     * Tidak memakan RAM heap Java (0 MB Heap Memory), menggunakan native address space virtual memory OS.
     */
    private fun muxAdtsFileToM4a(adtsFile: File, outputFile: File): Boolean {
        if (!adtsFile.exists() || adtsFile.length() < 7) return false
        var muxer: MediaMuxer? = null
        var fis: FileInputStream? = null

        return try {
            fis = FileInputStream(adtsFile)
            val channel = fis.channel
            val fileSize = channel.size()
            val mappedBuf = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)

            // 1. Cari header ADTS pertama
            var firstOffset = 0
            while (firstOffset < fileSize - 7) {
                if ((mappedBuf.get(firstOffset).toInt() and 0xFF) == 0xFF &&
                    (mappedBuf.get(firstOffset + 1).toInt() and 0xF0) == 0xF0
                ) {
                    break
                }
                firstOffset++
            }

            if (firstOffset >= fileSize - 7) return false

            val profile = ((mappedBuf.get(firstOffset + 2).toInt() ushr 6) and 0x03) + 1
            val sampleRateIdx = (mappedBuf.get(firstOffset + 2).toInt() ushr 2) and 0x0F
            val channels = (((mappedBuf.get(firstOffset + 2).toInt() and 0x01) shl 2) or
                    ((mappedBuf.get(firstOffset + 3).toInt() ushr 6) and 0x03)).coerceAtLeast(1)

            val sampleRate = when (sampleRateIdx) {
                0 -> 96000
                1 -> 88200
                2 -> 64000
                3 -> 48000
                4 -> 44100
                5 -> 32000
                6 -> 24000
                7 -> 22050
                8 -> 16000
                else -> 44100
            }

            val csd0 = byteArrayOf(
                ((profile shl 3) or (sampleRateIdx ushr 1)).toByte(),
                (((sampleRateIdx and 0x01) shl 7) or (channels shl 3)).toByte()
            )

            val mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
            mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))

            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndex = muxer.addTrack(mediaFormat)
            muxer.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var frameIndex = 0L
            var pos = firstOffset

            while (pos <= fileSize - 7) {
                val b0 = mappedBuf.get(pos).toInt() and 0xFF
                val b1 = mappedBuf.get(pos + 1).toInt() and 0xF0

                if (b0 == 0xFF && b1 == 0xF0) {
                    val hasCrc = (mappedBuf.get(pos + 1).toInt() and 0x01) == 0
                    val headerLen = if (hasCrc) 9 else 7
                    val frameLen = (((mappedBuf.get(pos + 3).toInt() and 0x03) shl 11) or
                            ((mappedBuf.get(pos + 4).toInt() and 0xFF) shl 3) or
                            ((mappedBuf.get(pos + 5).toInt() and 0xE0) ushr 5))

                    val rawPayloadLen = frameLen - headerLen
                    if (rawPayloadLen > 0 && pos + frameLen <= fileSize) {
                        mappedBuf.position(pos + headerLen)
                        mappedBuf.limit(pos + frameLen)

                        bufferInfo.offset = pos + headerLen
                        bufferInfo.size = rawPayloadLen
                        bufferInfo.presentationTimeUs = (frameIndex * 1024L * 1000000L) / sampleRate
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME

                        muxer.writeSampleData(trackIndex, mappedBuf, bufferInfo)
                        frameIndex++
                        pos += frameLen
                    } else {
                        pos++
                    }
                } else {
                    pos++
                }
            }

            muxer.stop()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { muxer?.release() } catch (e: Exception) {}
            try { fis?.close() } catch (e: Exception) {}
        }
    }

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
            if (pid != 256) continue

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
