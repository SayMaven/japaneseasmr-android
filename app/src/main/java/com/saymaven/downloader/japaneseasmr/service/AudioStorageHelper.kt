package com.saymaven.downloader.japaneseasmr.service

import java.io.File

object AudioStorageHelper {

    /**
     * Mencari file audio yang cocok untuk kode RJ di folder unduhan.
     * Mendukung variasi format:
     * - RJxxxxxx.m4a, RJxxxxxx.mp3, RJxxxxxx.aac
     * - [RJxxxxxx] Judul.m4a / .mp3 / .aac
     * - RJxxxxxx Judul.m4a / .mp3 / .aac
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
     * Jika localFilePath lama sudah tidak ada, mencari file alternatif untuk RJID yang sama di downloadDir.
     */
    fun resolveValidAudioFile(downloadDir: File?, localFilePath: String, rjid: String): File? {
        val direct = File(localFilePath)
        if (direct.exists() && direct.length() > 0) return direct

        return findExistingAudioFile(downloadDir, rjid)
    }
}
