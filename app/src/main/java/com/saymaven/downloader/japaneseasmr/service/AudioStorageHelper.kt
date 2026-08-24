package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

object AudioStorageHelper {

    /**
     * Mencari file audio yang cocok untuk kode RJ di folder unduhan.
     */
    fun findExistingAudioFile(downloadDir: File?, rjid: String): File? {
        if (downloadDir == null || !downloadDir.exists() || !downloadDir.isDirectory) return null
        val cleanId = rjid.uppercase().trim()
        val files = downloadDir.listFiles() ?: return null

        val audioExtensions = setOf("m4a", "mp3", "aac")

        // 1. Cek Exact Match: RJxxxxxx.m4a / .mp3 / .aac
        val exact = files.firstOrNull { f ->
            if (!f.isFile || f.length() == 0L) return@firstOrNull false
            val ext = f.extension.lowercase()
            audioExtensions.contains(ext) && f.nameWithoutExtension.equals(cleanId, ignoreCase = true)
        }
        if (exact != null) return exact

        // 2. Cek Prefix Match: [RJxxxxxx] ... atau RJxxxxxx ...
        val prefixed = files.firstOrNull { f ->
            if (!f.isFile || f.length() == 0L) return@firstOrNull false
            val ext = f.extension.lowercase()
            if (!audioExtensions.contains(ext)) return@firstOrNull false

            val name = f.name.uppercase()
            name.startsWith("[$cleanId]") || name.startsWith("$cleanId ") || name.startsWith("${cleanId}_") || name.startsWith("$cleanId-")
        }

        return prefixed
    }

    /**
     * Memeriksa dan mengembalikan File valid untuk HistoryEntity.
     */
    fun resolveValidAudioFile(downloadDir: File?, localFilePath: String?, rjid: String): File? {
        if (!localFilePath.isNullOrBlank()) {
            val direct = File(localFilePath)
            if (direct.exists() && direct.length() > 0) return direct
        }
        return findExistingAudioFile(downloadDir, rjid)
    }

    /**
     * Mengambil file cover lokal instan (0ms) dari cache atau mengekstrak dari embedded audio.
     */
    fun getLocalCoverFile(context: Context, rjid: String, audioFilePath: String?): File? {
        val coversDir = File(context.cacheDir, "covers").apply { if (!exists()) mkdirs() }
        val cachedCover = File(coversDir, "${rjid.uppercase()}.jpg")
        if (cachedCover.exists() && cachedCover.length() > 0) {
            return cachedCover
        }

        if (!audioFilePath.isNullOrBlank()) {
            val audioFile = File(audioFilePath)
            if (audioFile.exists() && audioFile.length() > 0) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(audioFile.absolutePath)
                    val pic = retriever.embeddedPicture
                    if (pic != null && pic.isNotEmpty()) {
                        FileOutputStream(cachedCover).use { it.write(pic) }
                        return cachedCover
                    }
                } catch (e: Exception) {
                } finally {
                    try { retriever.release() } catch (e: Exception) {}
                }
            }
        }

        return null
    }
}
