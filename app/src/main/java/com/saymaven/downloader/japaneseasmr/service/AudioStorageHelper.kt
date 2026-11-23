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

    val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "flac", "wav", "ogg", "wma")
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    val RJ_EXTRACT_REGEX = Regex("(?:RJ|VJ|BJ)[-_ ]?(\\d{6,8})", RegexOption.IGNORE_CASE)
    private val COVER_CANDIDATE_NAMES = listOf("cover", "folder", "front", "main", "poster", "thumb", "artwork", "default")
    private val IMAGE_SUBDIRECTORIES = listOf("thumb", "thumbnail", "img", "image", "images", "jacket", "cover", "サムネイル", "画像")

    /**
     * Mengekstrak dan menormalisasi kode karya DLsite (RJ, VJ, atau BJ) menjadi format kanonikal (misal: RJ01081584).
     * Mendukung variasi pemisah seperti RJ-01081584, RJ_01081584, atau RJ 01081584.
     */
    fun extractRjCode(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = RJ_EXTRACT_REGEX.find(text) ?: return null
        val raw = match.value.uppercase()
        val prefix = raw.take(2)
        val digits = raw.filter { it.isDigit() }
        return if (digits.length in 6..8) "$prefix$digits" else null
    }

    /**
     * Mengekstrak kode RJ dari metadata ID3 tag internal audio (Title, Album, Artist, Composer)
     * atau berkas lembar cue/lrc pendamping.
     */
    fun extractRjFromAudioMetadata(file: File): String? {
        if (!file.exists() || !file.isFile || file.length() == 0L) return null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            extractRjCode(title)?.let { return it }

            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            extractRjCode(album)?.let { return it }

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            extractRjCode(artist)?.let { return it }

            val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            extractRjCode(author)?.let { return it }

            val composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            extractRjCode(composer)?.let { return it }
        } catch (e: Exception) {
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        try {
            val cueFile = File(file.parentFile, "${file.nameWithoutExtension}.cue")
            if (cueFile.exists() && cueFile.isFile) {
                val cueText = cueFile.readText()
                extractRjCode(cueText)?.let { return it }
            }
        } catch (e: Exception) {}

        return null
    }

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
     * Mengambil seluruh berkas audio secara rekursif di dalam folder beserta subfoldernya,
     * diurutkan berdasarkan natural order pada path relatifnya.
     */
    fun getAudioFilesRecursive(folder: File, maxDepth: Int = 4): List<File> {
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        return try {
            val files = mutableListOf<File>()
            folder.walkTopDown().maxDepth(maxDepth).forEach { f ->
                if (f.isFile && f.length() > 0L && AUDIO_EXTENSIONS.contains(f.extension.lowercase())) {
                    files.add(f)
                }
            }
            files.sortedWith { f1, f2 ->
                val p1 = try { f1.relativeTo(folder).path } catch (_: Exception) { f1.name }
                val p2 = try { f2.relativeTo(folder).path } catch (_: Exception) { f2.name }
                NATURAL_ORDER_COMPARATOR.compare(p1, p2)
            }
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
        var curr: File? = fileOrFolder
        val stopPath = rootBoundary?.canonicalPath
        while (curr != null) {
            val match = extractRjCode(curr.name)
            if (match != null) return match
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
     * Mendapatkan nama folder tempat file audio berada ("Utama" untuk root atau nama subfolder/folder impor).
     */
    fun getRelativeFolderName(downloadDir: File?, audioFile: File, importedDirs: List<File> = emptyList()): String {
        return try {
            val targetCanon = audioFile.canonicalPath

            // 1. Cek apakah audioFile berada di salah satu direktori impor
            for (imp in importedDirs) {
                if (!imp.exists()) continue
                val impCanon = imp.canonicalPath
                if (targetCanon.equals(impCanon, ignoreCase = true) ||
                    targetCanon.startsWith(impCanon + File.separator, ignoreCase = true)
                ) {
                    val name = imp.name
                    return if (name.isBlank()) "Utama" else name
                }
            }

            // 2. Cek apakah audioFile berada di direktori unduhan utama
            if (downloadDir != null && downloadDir.exists()) {
                val dlCanon = downloadDir.canonicalPath
                val parent = audioFile.parentFile ?: return "Utama"
                val parentCanon = parent.canonicalPath
                when {
                    parentCanon.equals(dlCanon, ignoreCase = true) -> "Utama"
                    parentCanon.startsWith(dlCanon + File.separator, ignoreCase = true) -> {
                        val rel = parentCanon.substring(dlCanon.length + 1)
                            .replace('\\', '/')
                            .substringBefore('/')
                        if (rel.isBlank()) "Utama" else rel
                    }
                    else -> {
                        val fallback = parent.name
                        if (fallback.isBlank()) "Utama" else fallback
                    }
                }
            } else {
                val parent = audioFile.parentFile ?: return "Utama"
                val fallback = parent.name
                if (fallback.isBlank()) "Utama" else fallback
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
     * Memindahkan file atau folder audio ke folder tujuan ("Utama" untuk root atau nama subfolder) secara fisik di memori HP.
     * Jika berupa direktori (album multi-track), seluruh folder beserta semua file track di dalamnya dipindahkan.
     * Jika berupa file audio tunggal, file pendamping (.cue, cover .jpg/.png) juga ikut dipindahkan.
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

            if (sourceFile.isDirectory) {
                val success = sourceFile.renameTo(targetFile)
                if (success && targetFile.exists()) {
                    targetFile
                } else {
                    sourceFile.copyRecursively(targetFile, overwrite = true)
                    sourceFile.deleteRecursively()
                    targetFile
                }
            } else {
                val success = sourceFile.renameTo(targetFile)
                val finalFile = if (success && targetFile.exists()) {
                    targetFile
                } else {
                    sourceFile.copyTo(targetFile, overwrite = true)
                    sourceFile.delete()
                    targetFile
                }

                // Pindahkan file pendamping (.cue, .jpg, .png, .webp jika ada)
                try {
                    val baseName = sourceFile.nameWithoutExtension
                    val parent = sourceFile.parentFile
                    val companionExts = listOf("cue", "jpg", "png", "webp", "jpeg")
                    companionExts.forEach { ext ->
                        val comp = File(parent, "$baseName.$ext")
                        if (comp.exists() && comp.isFile) {
                            val targetComp = File(targetDir, comp.name)
                            if (!comp.renameTo(targetComp)) {
                                comp.copyTo(targetComp, overwrite = true)
                                comp.delete()
                            }
                        }
                    }
                } catch (e: Exception) {}

                finalFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearLocalCoverCacheFor(rjid: String) {
        val clean = rjid.uppercase().trim()
        val keysToRemove = localCoverCache.keys.filter { it.startsWith(clean) }
        keysToRemove.forEach { localCoverCache.remove(it) }
    }

    /**
     * Mencari cover art yang secara spesifik milik file audio ini:
     * 1. Companion image dengan nama berkas stem identik (misal: audio1.mp3 -> audio1.jpg/png/webp).
     * 2. Berkas gambar di folder yang namanya memuat kode RJ yang sama (misal: RJ01081584.jpg).
     */
    fun findSpecificCoverForAudioFile(audioFile: File, rjCode: String? = null): File? {
        val parent = audioFile.parentFile ?: return null
        if (!parent.exists() || !parent.isDirectory) return null

        val nameWithoutExt = audioFile.nameWithoutExtension
        // 1. Companion cover persis dengan nama stem file audio
        for (ext in IMAGE_EXTENSIONS) {
            val companion = File(parent, "$nameWithoutExt.$ext")
            if (companion.exists() && companion.isFile && companion.length() > 0) {
                return companion
            }
        }

        // 2. Berkas gambar yang memuat kode RJ spesifik audio ini
        val targetRj = (rjCode ?: extractRjCode(audioFile.name))?.uppercase()?.trim()
        if (!targetRj.isNullOrBlank()) {
            val images = getAllLocalImagesInFolder(parent)
            val rjImage = images.firstOrNull { img ->
                img.name.uppercase().contains(targetRj)
            }
            if (rjImage != null && rjImage.exists() && rjImage.length() > 0) {
                return rjImage
            }
        }

        return null
    }

    /**
     * Memeriksa apakah folder fisik merupakan folder khusus untuk 1 karya saja:
     * - Hanya memiliki <= 1 file audio di dalamnya, ATAU
     * - Nama folder memuat rjCode dan tidak memiliki file audio dengan kode RJ yang berbeda.
     */
    fun isDedicatedSingleWorkFolder(folder: File, rjCode: String? = null): Boolean {
        if (!folder.exists() || !folder.isDirectory) return false
        val audios = getAudioFilesInFolder(folder)
        if (audios.size <= 1) return true
        val cleanRj = rjCode?.uppercase()?.trim()
        if (!cleanRj.isNullOrBlank() && folder.name.uppercase().contains(cleanRj)) {
            val rjs = audios.mapNotNull { extractRjCode(it.name) }.distinct()
            return rjs.isEmpty() || (rjs.size == 1 && rjs.first().equals(cleanRj, ignoreCase = true))
        }
        return false
    }

    /**
     * Memvalidasi apakah coverUrl saat ini merupakan cover folder yang salah waris (bocor ke file audio individual).
     * Mengembalikan true jika coverUrl adalah cover lokal yang tidak cocok dengan audioFile ini.
     */
    fun isMismatchedFolderCover(audioFile: File, coverUrl: String?, rjCode: String?): Boolean {
        if (coverUrl.isNullOrBlank()) return false
        if (coverUrl.startsWith("http://") || coverUrl.startsWith("https://")) return false
        val cover = File(coverUrl)
        if (!cover.exists() || cover.length() == 0L) return true

        val parent = audioFile.parentFile ?: return false
        val cleanRj = rjCode?.uppercase()?.trim()

        // 1. Cover resmi atau custom cover tersimpan di direktori cache covers -> valid
        val canonical = try { cover.canonicalPath } catch (e: Exception) { cover.absolutePath }
        if (canonical.contains("covers")) {
            if (cover.name.startsWith("custom_", ignoreCase = true) || cleanRj.isNullOrBlank() || cover.name.uppercase().contains(cleanRj)) {
                return false
            }
        }

        // 2. Companion cover persis dengan nama stem audio -> valid
        if (cover.nameWithoutExtension.equals(audioFile.nameWithoutExtension, ignoreCase = true)) {
            return false
        }

        // 3. Nama berkas cover memuat kode RJ karya ini -> valid
        if (!cleanRj.isNullOrBlank() && cover.name.uppercase().contains(cleanRj)) {
            return false
        }

        // 4. Jika cover berada di parent folder audio:
        val coverParent = cover.parentFile
        if (coverParent != null && coverParent.canonicalPath.equals(parent.canonicalPath, ignoreCase = true)) {
            val audios = getAudioFilesInFolder(parent)
            // Jika folder hanya memuat 1 file audio, maka cover.jpg/folder.jpg adalah milik audio ini
            if (audios.size <= 1) return false
            // Namun jika folder memuat >= 2 audio (multi-audio non-album), cover generik adalah salah waris!
            return true
        }

        // Cover berada di folder luar / hierarki lebih tinggi yang tidak spesifik -> mismatch
        return true
    }

    /**
     * Mengambil file cover lokal instan (0ms) dari cache, cover folder fisik, atau mengekstrak dari embedded audio.
     */
    fun getLocalCoverFile(context: Context, rjid: String, audioFilePath: String?): File? {
        val cleanRj = rjid.uppercase().trim()
        val cacheKey = "${cleanRj}_${audioFilePath ?: ""}"
        localCoverCache[cacheKey]?.let { return it }

        val coversDir = File(context.cacheDir, "covers")
        if (cleanRj.isNotBlank()) {
            val customCover = File(coversDir, "custom_$cleanRj.jpg")
            if (customCover.exists() && customCover.length() > 0) {
                localCoverCache[cacheKey] = customCover
                return customCover
            }
            val cachedCover = File(coversDir, "$cleanRj.jpg")
            if (cachedCover.exists() && cachedCover.length() > 0) {
                localCoverCache[cacheKey] = cachedCover
                return cachedCover
            }
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
                    // 1. Cek cover spesifik milik file audio ini (companion atau RJ match)
                    val specificCover = findSpecificCoverForAudioFile(fileOrDir, cleanRj)
                    if (specificCover != null) {
                        localCoverCache[cacheKey] = specificCover
                        return specificCover
                    }

                    // 2. Cek embedded picture dari metadata berkas audio
                    if (!coversDir.exists()) coversDir.mkdirs()
                    val targetCachedFile = if (cleanRj.isNotBlank()) {
                        File(coversDir, "$cleanRj.jpg")
                    } else {
                        File(coversDir, "${generateLocalId(fileOrDir.absolutePath)}.jpg")
                    }
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(fileOrDir.absolutePath)
                        val pic = retriever.embeddedPicture
                        if (pic != null && pic.isNotEmpty()) {
                            FileOutputStream(targetCachedFile).use { it.write(pic) }
                            localCoverCache[cacheKey] = targetCachedFile
                            return targetCachedFile
                        }
                    } catch (e: Exception) {
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }

                    // 3. Hanya gunakan cover parent folder JIKA parent adalah folder khusus 1 karya
                    val parent = fileOrDir.parentFile
                    if (parent != null && parent.exists() && isDedicatedSingleWorkFolder(parent, cleanRj)) {
                        val folderCover = findLocalCoverArt(parent)
                        if (folderCover != null && folderCover.exists() && folderCover.length() > 0) {
                            localCoverCache[cacheKey] = folderCover
                            return folderCover
                        }
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
                            val candidateFile = File(candidate)

                            // Jika MANAGE_EXTERNAL_STORAGE aktif di Android 11+, path kartu SD langsung valid untuk POSIX
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                                android.os.Environment.isExternalStorageManager() &&
                                candidateFile.exists()
                            ) {
                                return candidate
                            }

                            if (candidateFile.exists() && candidateFile.canRead()) {
                                return candidate
                            }

                            // Fallback scanning external directories for secondary SD card mount
                            val extDirs = context.getExternalFilesDirs(null)
                            for (extDir in extDirs) {
                                if (extDir != null && extDir.absolutePath.contains(type, ignoreCase = true)) {
                                    val sdRoot = extDir.absolutePath.substringBefore("/Android")
                                    val possiblePath = "$sdRoot/$relPath"
                                    val possibleFile = File(possiblePath)
                                    if (possibleFile.exists() && possibleFile.canRead()) {
                                        return possiblePath
                                    }
                                }
                            }

                            // Jika tidak bisa dibaca via POSIX langsung, return null agar caller tetap menggunakan SAF
                            return if (candidateFile.exists() && candidateFile.canRead()) candidate else null
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
