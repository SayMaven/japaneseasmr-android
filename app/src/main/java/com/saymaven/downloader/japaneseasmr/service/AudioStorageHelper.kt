package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AudioStorageHelper {

    val INDONESIAN_LOCALE = Locale("id", "ID")
    val DISPLAY_DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", INDONESIAN_LOCALE)

    fun formatDateForDisplay(timestamp: Long): String {
        return DISPLAY_DATE_FORMAT.format(Date(timestamp))
    }

    /**
     * Mengurai string tanggal ke milidetik secara akurat untuk pengurutan kronologis.
     */
    fun parseDateToTimestamp(dateStr: String?, localFilePath: String?): Long {
        // 1. Jika file lokal ada di memori HP, gunakan lastModified fisik file (paling akurat)
        if (!localFilePath.isNullOrBlank()) {
            val file = File(localFilePath)
            if (file.exists() && file.lastModified() > 0L) {
                return file.lastModified()
            }
        }

        if (dateStr.isNullOrBlank()) return 0L

        // 2. Parse format string yang tersimpan
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd MMM yyyy, HH:mm", INDONESIAN_LOCALE),
            SimpleDateFormat("dd MMM yyyy", INDONESIAN_LOCALE),
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        )

        for (fmt in formats) {
            try {
                val parsed = fmt.parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (e: Exception) {
            }
        }

        return 0L
    }

    /**
     * Menyeragamkan format tampilan tanggal menjadi "dd MMM yyyy" (contoh: 24 Agu 2026).
     */
    fun normalizeDateString(dateStr: String?, localFilePath: String?): String {
        val ts = parseDateToTimestamp(dateStr, localFilePath)
        return if (ts > 0L) formatDateForDisplay(ts) else (dateStr ?: formatDateForDisplay(System.currentTimeMillis()))
    }

    val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "flac", "wav", "ogg")

    /**
     * Mencari file audio yang cocok untuk kode RJ di folder unduhan (termasuk subfolder).
     */
    fun findExistingAudioFile(downloadDir: File?, rjid: String): File? {
        if (downloadDir == null || !downloadDir.exists() || !downloadDir.isDirectory) return null
        val cleanId = rjid.uppercase().trim()

        try {
            // 1. Cek Exact Match & Prefix Match secara rekursif hingga kedalaman 5 subfolder
            return downloadDir.walkTopDown().maxDepth(5).firstOrNull { f ->
                if (!f.isFile || f.length() == 0L) return@firstOrNull false
                val ext = f.extension.lowercase()
                if (!AUDIO_EXTENSIONS.contains(ext)) return@firstOrNull false

                val name = f.name.uppercase()
                f.nameWithoutExtension.equals(cleanId, ignoreCase = true) ||
                    name.startsWith("[$cleanId]") ||
                    name.startsWith("$cleanId ") ||
                    name.startsWith("${cleanId}_") ||
                    name.startsWith("$cleanId-") ||
                    name.contains(cleanId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
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
     * Mendeteksi seluruh subfolder fisik yang ada di dalam direktori unduhan saat ini.
     */
    fun getExistingSubfolders(downloadDir: File?): List<String> {
        if (downloadDir == null || !downloadDir.exists() || !downloadDir.isDirectory) return emptyList()
        return try {
            downloadDir.listFiles { file ->
                file.isDirectory && !file.name.startsWith(".") && file.canRead()
            }?.map { it.name }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Mendapatkan nama folder tempat file audio berada ("Utama" untuk root atau nama subfolder).
     */
    fun getRelativeFolderName(downloadDir: File?, audioFile: File): String {
        if (downloadDir == null || !downloadDir.exists()) return "Utama"
        return try {
            val parent = audioFile.parentFile ?: return "Utama"
            if (parent.canonicalPath == downloadDir.canonicalPath) {
                "Utama"
            } else {
                val rel = parent.relativeToOrNull(downloadDir)?.path?.replace('\\', '/') ?: parent.name
                if (rel.isBlank()) "Utama" else rel
            }
        } catch (e: Exception) {
            "Utama"
        }
    }

    /**
     * Membuat subfolder baru secara fisik di dalam direktori unduhan.
     */
    fun createSubfolder(downloadDir: File?, folderName: String): File? {
        if (downloadDir == null || !downloadDir.exists() || folderName.isBlank()) return null
        val cleanName = folderName.trim().replace("/", "").replace("\\", "")
        val target = File(downloadDir, cleanName)
        return if (target.exists() || target.mkdirs()) target else null
    }

    /**
     * Memindahkan file audio ke folder tujuan ("Utama" untuk root atau nama subfolder) secara fisik di memori HP.
     */
    fun moveAudioFile(sourceFile: File, targetSubfolderName: String, downloadDir: File?): File? {
        if (!sourceFile.exists() || downloadDir == null || !downloadDir.exists()) return null
        return try {
            val targetDir = if (targetSubfolderName.equals("Utama", ignoreCase = true) || targetSubfolderName.equals("Semua", ignoreCase = true)) {
                downloadDir
            } else {
                File(downloadDir, targetSubfolderName).apply { if (!exists()) mkdirs() }
            }

            val targetFile = File(targetDir, sourceFile.name)
            if (sourceFile.canonicalPath == targetFile.canonicalPath) {
                return sourceFile
            }

            val success = sourceFile.renameTo(targetFile)
            if (success && targetFile.exists()) {
                targetFile
            } else {
                // Fallback copy & delete if rename across mount fails
                sourceFile.copyTo(targetFile, overwrite = true)
                sourceFile.delete()
                targetFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

    /**
     * Mengonversi SAF Content URI menjadi path file absolut penyimpanan fisik Android.
     */
    fun resolvePhysicalPathFromUri(context: Context, uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        if (uriString.startsWith("/") || uriString.startsWith("file://")) {
            return uriString.removePrefix("file://")
        }
        if (uriString.startsWith("content://")) {
            try {
                val uri = android.net.Uri.parse(uriString)
                val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                if (docId != null) {
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val relPath = split[1]
                        if ("primary".equals(type, ignoreCase = true)) {
                            val primaryStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
                            return "$primaryStorage/$relPath"
                        } else {
                            return "/storage/$type/$relPath"
                        }
                    }
                }
            } catch (e: Exception) {
                try {
                    val decoded = java.net.URLDecoder.decode(uriString, "UTF-8")
                    val primaryIdx = decoded.indexOf("primary:")
                    if (primaryIdx != -1) {
                        val sub = decoded.substring(primaryIdx + "primary:".length)
                        val primaryStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
                        return "$primaryStorage/$sub"
                    }
                } catch (ignored: Exception) {}
            }
        }
        return uriString
    }

    /**
     * Memformat path penyimpanan agar ringkas dan nyaman dibaca (contoh: "Download/JapaneseASMR").
     */
    fun formatPathForDisplay(rawPath: String?): String {
        if (rawPath.isNullOrBlank()) return "Download/JapaneseASMR"
        var clean = rawPath
        if (clean.startsWith("/storage/emulated/0/")) {
            clean = clean.removePrefix("/storage/emulated/0/")
        } else if (clean.startsWith("/sdcard/")) {
            clean = clean.removePrefix("/sdcard/")
        } else if (clean.startsWith("/storage/")) {
            val parts = clean.removePrefix("/storage/").split("/", limit = 2)
            clean = if (parts.size == 2) "SD Card/${parts[1]}" else clean
        }
        return clean.trimStart('/')
    }
}
