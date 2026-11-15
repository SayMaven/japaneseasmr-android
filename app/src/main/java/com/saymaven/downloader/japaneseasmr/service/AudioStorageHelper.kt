package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import android.media.MediaMetadataRetriever
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object AudioStorageHelper {

    val INDONESIAN_LOCALE: Locale = Locale.forLanguageTag("id-ID")
    val DISPLAY_DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", INDONESIAN_LOCALE)

    // O(1) in-memory caches untuk scroll bebas jank & latensi 0ms
    private val localCoverCache = ConcurrentHashMap<String, File?>()
    private val timestampCache = ConcurrentHashMap<String, Long>()

    fun formatDateForDisplay(timestamp: Long): String {
        return DISPLAY_DATE_FORMAT.format(Date(timestamp))
    }

    /**
     * Mengurai string tanggal ke milidetik secara akurat untuk pengurutan kronologis (Cached).
     */
    fun parseDateToTimestamp(dateStr: String?, localFilePath: String?): Long {
        val cacheKey = "${dateStr ?: ""}_${localFilePath ?: ""}"
        timestampCache[cacheKey]?.let { return it }

        val ts = computeTimestampInternal(dateStr, localFilePath)
        if (ts > 0L) {
            timestampCache[cacheKey] = ts
        }
        return ts
    }

    private fun computeTimestampInternal(dateStr: String?, localFilePath: String?): Long {
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
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val COVER_CANDIDATE_NAMES = listOf("cover", "folder", "front", "main", "poster", "thumb", "artwork", "default")
    private val IMAGE_SUBDIRECTORIES = listOf("thumb", "thumbnail", "img", "image", "images", "jacket", "cover", "サムネイル", "画像")

    /**
     * Natural sort order comparator untuk file bernomor (contoh: 1.mp3, 2.mp3, 10.mp3).
     */
    val NATURAL_ORDER_COMPARATOR = Comparator<String> { a, b ->
        val regex = "(\\d+)|(\\D+)".toRegex()
        val aTokens = regex.findAll(a).map { it.value }.toList()
        val bTokens = regex.findAll(b).map { it.value }.toList()
        val minLen = minOf(aTokens.size, bTokens.size)
        for (i in 0 until minLen) {
            val aToken = aTokens[i]
            val bToken = bTokens[i]
            val aNum = aToken.toLongOrNull()
            val bNum = bToken.toLongOrNull()
            if (aNum != null && bNum != null) {
                val cmp = aNum.compareTo(bNum)
                if (cmp != 0) return@Comparator cmp
            } else {
                val cmp = aToken.compareTo(bToken, ignoreCase = true)
                if (cmp != 0) return@Comparator cmp
            }
        }
        aTokens.size.compareTo(bTokens.size)
    }

    /**
     * Mengambil seluruh file audio di dalam folder (direct children), diurutkan natural order.
     */
    fun getAudioFilesInFolder(folder: File): List<File> {
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        return try {
            folder.listFiles { f ->
                f.isFile && f.length() > 0L && AUDIO_EXTENSIONS.contains(f.extension.lowercase())
            }?.sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name, f2.name) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Mengecek apakah sebuah folder adalah multi-track album (memiliki >= 2 file audio).
     */
    fun isMultiTrackFolder(folder: File): Boolean {
        return getAudioFilesInFolder(folder).size >= 2
    }

    private val virtualAlbumChaptersCache = ConcurrentHashMap<String, List<ChapterItem>>()

    fun getCachedVirtualChapters(rjidOrPath: String): List<ChapterItem>? {
        return virtualAlbumChaptersCache[rjidOrPath]
    }

    fun cacheVirtualChapters(rjidOrPath: String, chapters: List<ChapterItem>) {
        if (chapters.isNotEmpty()) {
            virtualAlbumChaptersCache[rjidOrPath] = chapters
        }
    }

    suspend fun extractVirtualAlbumChapters(tracks: List<File>): List<ChapterItem> = withContext(Dispatchers.IO) {
        var cum = 0L
        tracks.map { tr ->
            var dur = 0L
            val ret = MediaMetadataRetriever()
            try {
                ret.setDataSource(tr.absolutePath)
                dur = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
            } finally {
                try { ret.release() } catch (e: Exception) {}
            }
            val ch = ChapterItem(cum, cum + dur, tr.nameWithoutExtension, tr.name)
            cum += dur
            ch
        }
    }

    /**
     * Mengambil seluruh berkas gambar lokal di dalam folder (dan subfolder gambar pendukung).
     */
    fun getAllLocalImagesInFolder(folder: File): List<File> {
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        val result = mutableListOf<File>()
        try {
            // 1. Gambar langsung di dalam folder
            folder.listFiles { f ->
                f.isFile && f.length() > 0L && IMAGE_EXTENSIONS.contains(f.extension.lowercase())
            }?.let { result.addAll(it) }

            // 2. Gambar di subfolder populer
            val subdirs = folder.listFiles { f ->
                f.isDirectory && !f.name.startsWith(".") && IMAGE_SUBDIRECTORIES.any { cand -> f.name.contains(cand, ignoreCase = true) }
            } ?: emptyArray()

            for (sub in subdirs) {
                sub.listFiles { f ->
                    f.isFile && f.length() > 0L && IMAGE_EXTENSIONS.contains(f.extension.lowercase())
                }?.let { result.addAll(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.distinctBy { it.canonicalPath }.sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name, f2.name) }
    }

    /**
     * Mencari cover art lokal di dalam folder (cover.jpg, folder.png, thumb, atau image tunggal).
     * Mendukung pemilahan logo (あり vs なし) serta pencarian di subfolder gambar.
     */
    fun findLocalCoverArt(folder: File): File? {
        if (!folder.exists() || !folder.isDirectory) return null
        return try {
            val images = getAllLocalImagesInFolder(folder)
            if (images.isEmpty()) return null

            // 1. Prioritaskan gambar bertanda 'あり' (dengan logo/teks karya resmi DLsite)
            val ariImg = images.firstOrNull {
                val n = it.nameWithoutExtension
                n.contains("あり") || n.contains("有") || n.contains("logo_on", ignoreCase = true)
            }
            if (ariImg != null) return ariImg

            // 2. Cek prioritas nama populer
            for (cand in COVER_CANDIDATE_NAMES) {
                val found = images.firstOrNull { it.nameWithoutExtension.equals(cand, ignoreCase = true) }
                if (found != null) return found
            }

            // 3. Cek jika ada file cover khas DLsite (berisi "img_main")
            val dlsiteMain = images.firstOrNull { it.nameWithoutExtension.contains("img_main", ignoreCase = true) }
            if (dlsiteMain != null) return dlsiteMain

            // 4. Jika hanya ada 1 gambar di folder, gunakan itu
            if (images.size == 1) return images.first()

            // 5. Cek apakah ada file gambar yang memuat kode RJ jika folder memiliki nama RJ
            val rjMatch = Regex("((?:RJ|VJ|BJ)\\d{6,8})", RegexOption.IGNORE_CASE).find(folder.name)
            if (rjMatch != null) {
                val rjid = rjMatch.value.uppercase()
                val found = images.firstOrNull { it.name.uppercase().contains(rjid) }
                if (found != null) return found
            }

            // 6. Cek gambar tanpa logo 'なし' jika tidak ada opsi lain
            val nashiImg = images.firstOrNull {
                val n = it.nameWithoutExtension
                n.contains("なし") || n.contains("無し")
            }
            if (nashiImg != null) return nashiImg

            // Default ke gambar pertama
            images.first()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Menemukan seluruh folder leaf yang secara langsung berisi file audio (rekursif hingga kedalaman maxDepth).
     * Mendukung struktur bertingkat seperti RJ01697860/mp3/SEあり/
     */
    fun findLeafAudioFolders(rootDir: File, maxDepth: Int = 5): List<File> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()
        val leafFolders = mutableListOf<File>()
        try {
            rootDir.walkTopDown().maxDepth(maxDepth).forEach { file ->
                if (file.isDirectory && !file.name.startsWith(".")) {
                    val hasAudios = file.listFiles { f ->
                        f.isFile && f.length() > 0L && AUDIO_EXTENSIONS.contains(f.extension.lowercase())
                    }?.isNotEmpty() == true
                    if (hasAudios) {
                        leafFolders.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return leafFolders
    }

    /**
     * Mencari kode RJ ke atas hierarki folder (ancestor) hingga mencapai boundary/root direktori.
     */
    fun findRjCodeInPath(fileOrFolder: File, rootBoundary: File? = null): String? {
        val rjRegex = Regex("((?:RJ|VJ|BJ)\\d{6,8})", RegexOption.IGNORE_CASE)
        var curr: File? = fileOrFolder
        val stopPath = rootBoundary?.canonicalPath
        while (curr != null) {
            val match = rjRegex.find(curr.name)
            if (match != null) return match.value.uppercase()
            if (stopPath != null && curr.canonicalPath.equals(stopPath, ignoreCase = true)) break
            curr = curr.parentFile
        }
        return null
    }

    /**
     * Mencari cover art lokal dengan menelusuri ke atas hierarki folder (misal cover berada di folder RJ induk).
     */
    fun findCoverInPath(folder: File, rootBoundary: File? = null): File? {
        var curr: File? = folder
        val stopPath = rootBoundary?.canonicalPath
        while (curr != null) {
            val found = findLocalCoverArt(curr)
            if (found != null && found.exists() && found.length() > 0) return found
            if (stopPath != null && curr.canonicalPath.equals(stopPath, ignoreCase = true)) break
            curr = curr.parentFile
        }
        return null
    }

    /**
     * Membuat ID deterministik sintetis unik untuk file/folder lokal non-RJ (misal LOCAL_A1B2C3D4E5).
     */
    fun generateLocalId(path: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(path.toByteArray(Charsets.UTF_8))
            val hash = bytes.joinToString("") { "%02x".format(it) }
            "LOCAL_${hash.take(10).uppercase()}"
        } catch (e: Exception) {
            val hex = path.hashCode().toUInt().toString(16).padStart(8, '0')
            "LOCAL_${hex.take(10).uppercase()}"
        }
    }

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
     * Memeriksa dan mengembalikan File valid untuk HistoryEntity (file tunggal atau folder multi-track).
     */
    fun resolveValidAudioFile(downloadDir: File?, localFilePath: String?, rjid: String): File? {
        if (!localFilePath.isNullOrBlank()) {
            val direct = File(localFilePath)
            if (direct.exists()) {
                if (direct.isFile && direct.length() > 0) return direct
                if (direct.isDirectory && getAudioFilesInFolder(direct).isNotEmpty()) return direct
            }
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
     * Mengambil file cover lokal instan (0ms) dari cache, cover folder fisik, atau mengekstrak dari embedded audio.
     */
    fun getLocalCoverFile(context: Context, rjid: String, audioFilePath: String?): File? {
        val cleanRj = rjid.uppercase().trim()
        val cacheKey = "${cleanRj}_${audioFilePath ?: ""}"
        localCoverCache[cacheKey]?.let { return it }

        val coversDir = File(context.cacheDir, "covers")
        val cachedCover = File(coversDir, "$cleanRj.jpg")
        if (cachedCover.exists() && cachedCover.length() > 0) {
            localCoverCache[cacheKey] = cachedCover
            return cachedCover
        }

        if (!audioFilePath.isNullOrBlank()) {
            val fileOrDir = File(audioFilePath)
            if (fileOrDir.exists()) {
                if (fileOrDir.isDirectory) {
                    val folderCover = findLocalCoverArt(fileOrDir)
                    if (folderCover != null && folderCover.exists() && folderCover.length() > 0) {
                        localCoverCache[cacheKey] = folderCover
                        return folderCover
                    }
                    val firstAudio = getAudioFilesInFolder(fileOrDir).firstOrNull()
                    if (firstAudio != null) {
                        return getLocalCoverFile(context, rjid, firstAudio.absolutePath)
                    }
                } else if (fileOrDir.length() > 0) {
                    val parent = fileOrDir.parentFile
                    if (parent != null && parent.exists()) {
                        val folderCover = findLocalCoverArt(parent)
                        if (folderCover != null && folderCover.exists() && folderCover.length() > 0) {
                            localCoverCache[cacheKey] = folderCover
                            return folderCover
                        }
                    }
                    if (!coversDir.exists()) coversDir.mkdirs()
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(fileOrDir.absolutePath)
                        val pic = retriever.embeddedPicture
                        if (pic != null && pic.isNotEmpty()) {
                            FileOutputStream(cachedCover).use { it.write(pic) }
                            localCoverCache[cacheKey] = cachedCover
                            return cachedCover
                        }
                    } catch (e: Exception) {
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }
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
                val docId = try {
                    android.provider.DocumentsContract.getTreeDocumentId(uri)
                } catch (e: Exception) {
                    try {
                        android.provider.DocumentsContract.getDocumentId(uri)
                    } catch (e2: Exception) {
                        null
                    }
                }
                if (docId != null) {
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val relPath = split[1]
                        if ("primary".equals(type, ignoreCase = true)) {
                            val primaryStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
                            return "$primaryStorage/$relPath"
                        } else {
                            val candidate = "/storage/$type/$relPath"
                            if (File(candidate).exists()) {
                                return candidate
                            }
                            // Fallback scanning external directories for secondary SD card mount
                            val extDirs = context.getExternalFilesDirs(null)
                            for (extDir in extDirs) {
                                if (extDir != null && extDir.absolutePath.contains(type, ignoreCase = true)) {
                                    val sdRoot = extDir.absolutePath.substringBefore("/Android")
                                    val possiblePath = "$sdRoot/$relPath"
                                    if (File(possiblePath).exists()) {
                                        return possiblePath
                                    }
                                }
                            }
                            return candidate
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
