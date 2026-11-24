package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import com.saymaven.downloader.japaneseasmr.data.model.SourceType
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ImportPreviewData(
    val singleCount: Int,
    val multiAlbumCount: Int,
    val totalAudioTracks: Int,
    val unreadableCount: Int,
    val folderPathOrUri: String,
    val folderName: String
)

object StorageSyncManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val syncMutex = Mutex()
    private var lastSyncTime = 0L

    private val _syncTick = MutableStateFlow<Long>(0L)
    val syncTick = _syncTick.asStateFlow()

    val RJ_REGEX = Regex("((?:RJ|VJ|BJ)[-_ ]?\\d{6,8})", RegexOption.IGNORE_CASE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Memindai direktori pilihan (1-level depth) secara instan untuk pratinjau sebelum disimpan ke database.
     */
    suspend fun previewImportDirectory(context: Context, uriOrPath: String): ImportPreviewData = withContext(Dispatchers.IO) {
        val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(context, uriOrPath)
        val directFile = if (!resolvedPath.isNullOrBlank()) File(resolvedPath) else null

        if (directFile != null && directFile.exists() && directFile.isDirectory && directFile.canRead()) {
            val folderName = directFile.name.ifBlank { "Folder" }
            var singleCount = 0
            var multiAlbumCount = 0
            var totalAudioTracks = 0
            var unreadableCount = 0

            val prefs = PreferencesManager(context)
            val formatPriority = prefs.getInitialAudioFormatPriority()
            val leafFolders = AudioStorageHelper.findLeafAudioFolders(directFile, maxDepth = 5)
            for (leaf in leafFolders) {
                val rawAudios = AudioStorageHelper.getAudioFilesInFolder(leaf)
                val audios = filterAudioFilesByPriority(rawAudios, formatPriority)
                if (leaf == directFile && leafFolders.size > 1) {
                    // directFile memiliki subfolder audio sekaligus file audio di root -> hitung file root sebagai single
                    singleCount += audios.size
                    totalAudioTracks += audios.size
                    for (f in audios) {
                        if (!f.canRead() || f.length() == 0L) unreadableCount++
                    }
                } else if (audios.size >= 2) {
                    multiAlbumCount++
                    totalAudioTracks += audios.size
                    for (f in audios) {
                        if (!f.canRead() || f.length() == 0L) unreadableCount++
                    }
                } else if (audios.size == 1) {
                    singleCount++
                    totalAudioTracks += 1
                    val f = audios[0]
                    if (!f.canRead() || f.length() == 0L) unreadableCount++
                }
            }

            if (totalAudioTracks > 0) {
                return@withContext ImportPreviewData(
                    singleCount = singleCount,
                    multiAlbumCount = multiAlbumCount,
                    totalAudioTracks = totalAudioTracks,
                    unreadableCount = unreadableCount,
                    folderPathOrUri = uriOrPath,
                    folderName = folderName
                )
            }
        }

        // Fallback untuk SAF Content URI bila raw File tidak bisa diakses
        try {
            val treeUri = Uri.parse(uriOrPath)
            val docDir = DocumentFile.fromTreeUri(context, treeUri)
            if (docDir != null && docDir.exists() && docDir.isDirectory) {
                val folderName = docDir.name ?: "Folder"
                var singleCount = 0
                var multiAlbumCount = 0
                var totalAudioTracks = 0
                var unreadableCount = 0

                fun scanDocRecursive(dir: DocumentFile, depth: Int) {
                    if (depth > 5) return
                    val children = dir.listFiles()
                    val audioDocs = children.filter { it.isFile && AudioStorageHelper.AUDIO_EXTENSIONS.contains(it.name?.substringAfterLast('.', "")?.lowercase()) }
                    if (audioDocs.size >= 2) {
                        multiAlbumCount++
                        totalAudioTracks += audioDocs.size
                    } else if (audioDocs.size == 1) {
                        singleCount++
                        totalAudioTracks += 1
                    }
                    for (child in children) {
                        if (child.isDirectory && !child.name.orEmpty().startsWith(".")) {
                            scanDocRecursive(child, depth + 1)
                        }
                    }
                }

                scanDocRecursive(docDir, 1)

                return@withContext ImportPreviewData(
                    singleCount = singleCount,
                    multiAlbumCount = multiAlbumCount,
                    totalAudioTracks = totalAudioTracks,
                    unreadableCount = unreadableCount,
                    folderPathOrUri = uriOrPath,
                    folderName = folderName
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        ImportPreviewData(0, 0, 0, 0, uriOrPath, "Folder")
    }

    /**
     * Memindai direktori unduhan utama dan seluruh folder audio yang diimpor secara berkala di background IO.
     */
    fun syncStorageWithDatabase(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncTime < 1200L) {
            return // Debounce rapid sync requests
        }

        scope.launch {
            if (!syncMutex.tryLock()) {
                return@launch // Prevent concurrent duplicate scans
            }

            try {
                lastSyncTime = System.currentTimeMillis()
                val prefs = PreferencesManager(context)
                val dao = AsmrDatabase.getDatabase(context).historyDao()

                val rawDirStr = prefs.downloadDirFlow.first()
                val importedDirs = prefs.importedDirsFlow.first()

                val allTargetDirs = mutableSetOf<String>()
                if (!rawDirStr.isNullOrBlank()) {
                    allTargetDirs.add(rawDirStr)
                } else {
                    allTargetDirs.add(DownloadService.getDefaultDownloadDirectory().absolutePath)
                }
                allTargetDirs.addAll(importedDirs)

                // 1. Normalisasi semua format tanggal lama & pulihkan cover salah waris di database secara batch
                val existingHistoryList = dao.getAllHistoryDirect()
                for (item in existingHistoryList) {
                    var itemToUpdate = item
                    var itemNeedsUpdate = false

                    val normalized = AudioStorageHelper.normalizeDateString(item.downloadDate, item.localFilePath)
                    if (normalized != item.downloadDate) {
                        itemToUpdate = itemToUpdate.copy(downloadDate = normalized)
                        itemNeedsUpdate = true
                    }

                    val localF = File(item.localFilePath)
                    if (localF.isFile) {
                        val cleanRj = item.linkedRjId
                            ?: AudioStorageHelper.extractRjCode(item.rjid)
                            ?: AudioStorageHelper.extractRjCode(localF.name)
                            ?: AudioStorageHelper.extractRjFromAudioMetadata(localF)

                        if (cleanRj != null && item.linkedRjId != cleanRj) {
                            itemToUpdate = itemToUpdate.copy(linkedRjId = cleanRj)
                            itemNeedsUpdate = true
                        }

                        if (AudioStorageHelper.isMismatchedFolderCover(localF, itemToUpdate.coverUrl, cleanRj)) {
                            val corrected = AudioStorageHelper.findSpecificCoverForAudioFile(localF, cleanRj)
                                ?: AudioStorageHelper.getLocalCoverFile(context, cleanRj ?: "", localF.absolutePath)
                            val newCover = corrected?.absolutePath ?: if (!cleanRj.isNullOrBlank()) {
                                val prefix = if (cleanRj.length >= 2) cleanRj.substring(0, 2) else "RJ"
                                "https://img.dlsite.jp/modpub/images2/work/doujin/${prefix}0000/${cleanRj}_img_main.jpg"
                            } else ""
                            if (newCover != itemToUpdate.coverUrl) {
                                itemToUpdate = itemToUpdate.copy(coverUrl = newCover)
                                itemNeedsUpdate = true
                            }
                            if (!cleanRj.isNullOrBlank()) {
                                scope.launch { fetchAndCacheMetadata(context, dao, itemToUpdate) }
                            }
                        }
                    }

                    if (itemNeedsUpdate) {
                        dao.insertHistory(itemToUpdate)
                    }
                }

                // 2. Scan setiap folder target
                for (dirEntry in allTargetDirs) {
                    val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(context, dirEntry) ?: dirEntry
                    val dirFile = File(resolvedPath)

                    val canReadPhysical = dirFile.exists() && dirFile.isDirectory && dirFile.canRead() &&
                        (dirFile.listFiles()?.isNotEmpty() == true || AudioStorageHelper.findLeafAudioFolders(dirFile, 5).isNotEmpty())

                    if (canReadPhysical) {
                        scanPhysicalDirectory(context, dao, dirFile, isImported = importedDirs.contains(dirEntry))
                    } else if (dirEntry.startsWith("content://")) {
                        scanSafDirectory(context, dao, dirEntry)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                syncMutex.unlock()
                _syncTick.value = System.currentTimeMillis()
            }
        }
    }

    /**
     * Memeriksa apakah folder leaf adalah satu kesatuan album multi-track atau kumpulan track mandiri (koleksi/playlist).
     */
    private fun isMultiTrackAlbumFolder(leaf: File, audioFiles: List<File>, isImported: Boolean, rootDir: File): Boolean {
        if (audioFiles.size < 2) return false

        // 1. Periksa kode RJ pada masing-masing file
        val fileRjs = audioFiles.mapNotNull { f ->
            AudioStorageHelper.extractRjCode(f.name) ?: AudioStorageHelper.extractRjFromAudioMetadata(f)
        }.distinct()

        // Jika file-file memiliki kode RJ yang BERBEDA (misal RJ01075892 dan RJ01081584),
        // maka ini PASTI kumpulan karya mandiri (folder playlist/kategori), BUKAN satu album multi-track!
        if (fileRjs.size > 1) {
            return false
        }

        // 2. Jika leaf adalah root direktori itu sendiri dan root memiliki subfolder audio lain -> bukan multi-track
        if (leaf == rootDir && leaf != rootDir.parentFile) {
            val hasChildAudioDirs = rootDir.listFiles { f -> f.isDirectory }?.any { d ->
                AudioStorageHelper.getAudioFilesInFolder(d).isNotEmpty()
            } == true
            if (hasChildAudioDirs) return false
        }

        // 3. Jika folder ini diekspor/diimpor secara eksplisit via "Import Folder Audio":
        if (isImported && (leaf == rootDir || leaf.parentFile == rootDir)) {
            return true
        }

        // 4. Jika nama folder atau ancestor mengandung kode RJ:
        val pathRj = AudioStorageHelper.findRjCodeInPath(leaf, rootBoundary = rootDir.parentFile)
        if (pathRj != null) {
            return fileRjs.isEmpty() || (fileRjs.size == 1 && fileRjs.first() == pathRj)
        }

        // 5. Jika file-file berbagi 1 kode RJ yang sama:
        if (fileRjs.size == 1) {
            val withRjCount = audioFiles.count { AudioStorageHelper.extractRjCode(it.name) != null }
            if (withRjCount >= audioFiles.size / 2 || audioFiles.size <= 3) {
                return true
            }
        }

        // 6. Untuk folder tanpa kode RJ: cek apakah penamaan file mengikuti pola penomoran track album
        // (misal 01.mp3, 02.mp3, track01.mp3, ch1.mp3, part1.mp3, scene1.mp3)
        val trackPattern = Regex("^(?:\\d{1,3}|track|ch|chapter|part|scene|op|ed|se|disc|\\(\\d+\\)|\\[\\d+\\])[-_ .]", RegexOption.IGNORE_CASE)
        val numberedCount = audioFiles.count { f ->
            trackPattern.containsMatchIn(f.name) || f.name.matches(Regex("^\\d{1,3}\\..*"))
        }
        return numberedCount >= (audioFiles.size * 0.6)
    }

    /**
     * Menyaring daftar berkas audio agar tidak terjadi duplikasi format dengan stem nama yang sama
     * berdasarkan urutan prioritas format audio yang dikonfigurasi pengguna.
     */
    private fun filterAudioFilesByPriority(audioFiles: List<File>, priorityList: List<String>): List<File> {
        if (audioFiles.size <= 1) return audioFiles

        val grouped = audioFiles.groupBy { it.nameWithoutExtension.lowercase().trim() }
        val selectedFiles = mutableListOf<File>()

        for ((_, files) in grouped) {
            if (files.size == 1) {
                selectedFiles.add(files.first())
            } else {
                val best = files.minByOrNull { file ->
                    val ext = file.extension.lowercase().trim()
                    val idx = priorityList.indexOf(ext)
                    if (idx >= 0) idx else 999
                } ?: files.first()
                selectedFiles.add(best)
            }
        }
        return selectedFiles.distinctBy { it.canonicalPath }.sortedWith { f1, f2 ->
            AudioStorageHelper.NATURAL_ORDER_COMPARATOR.compare(f1.name, f2.name)
        }
    }

    /**
     * Memindai folder fisik: membedakan multi-track album dan single track audio secara akurat.
     */
    private suspend fun isSubfolderOfExistingDownload(
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        leaf: File,
        pathRj: String?,
        isImported: Boolean
    ): Boolean {
        if (isImported || pathRj == null) return false
        val existing = dao.getHistoryById(pathRj) ?: return false
        if (existing.sourceType != SourceType.DL_SITE.name) return false
        val parent = File(existing.localFilePath)
        if (!parent.exists()) return false
        return leaf.canonicalPath == parent.canonicalPath ||
            leaf.canonicalPath.startsWith(parent.canonicalPath + File.separator)
    }

    @Volatile
    private var currentScanIsImported: Boolean = true

    private suspend fun scanPhysicalDirectory(
        context: Context,
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        rootDir: File,
        isImported: Boolean
    ) {
        currentScanIsImported = isImported
        val prefs = PreferencesManager(context)
        val formatPriority = prefs.getInitialAudioFormatPriority()
        val leafFolders = AudioStorageHelper.findLeafAudioFolders(rootDir, maxDepth = 5)
        val variantDirRegex = Regex("(?:mp3|wav|flac|m4a|aac|opus|wma|seあり|se有り|seなし|se無し|se_ari|se_nashi|効果音|nose|音声原本)", RegexOption.IGNORE_CASE)

        // Deteksi folder karya ber-kode RJ yang memecah setiap trek ke subfolder berbeda (bukan varian format/SE)
        val mergedWorkDirs = mutableSetOf<String>()
        val rjWorkGroups = leafFolders
            .filter { it != rootDir }
            .groupBy { leaf ->
                var curr: File? = leaf.parentFile
                var foundWorkDir: File? = null
                while (curr != null && curr != rootDir.parentFile) {
                    if (AudioStorageHelper.extractRjCode(curr.name) != null) {
                        foundWorkDir = curr
                        break
                    }
                    if (curr == rootDir) break
                    curr = curr.parentFile
                }
                foundWorkDir
            }

        for ((workDir, groupLeaves) in rjWorkGroups) {
            if (workDir != null && groupLeaves.size >= 2) {
                val hasVariantSubfolders = groupLeaves.any { variantDirRegex.containsMatchIn(it.name) }
                val allSingleOrSmallLeaves = groupLeaves.all { AudioStorageHelper.getAudioFilesInFolder(it).size <= 2 }
                if (!hasVariantSubfolders && allSingleOrSmallLeaves) {
                    val allRecursiveTracks = filterAudioFilesByPriority(
                        AudioStorageHelper.getAudioFilesRecursive(workDir),
                        formatPriority
                    )
                    if (allRecursiveTracks.size >= 2) {
                        val rj = AudioStorageHelper.extractRjCode(workDir.name)
                        val fallbackCover = AudioStorageHelper.findCoverInPath(workDir, rootBoundary = rootDir)
                        val existingByRj = if (rj != null) dao.getHistoryById(rj) else null
                        val effectiveCustomRjid = if (rj != null && existingByRj != null && existingByRj.localFilePath != workDir.absolutePath && File(existingByRj.localFilePath).exists()) {
                            "${rj}_IMPORT_${AudioStorageHelper.generateLocalId(workDir.absolutePath).takeLast(6)}"
                        } else {
                            rj
                        }
                        syncMultiTrackFolder(
                            context = context,
                            dao = dao,
                            folder = workDir,
                            audioTracks = allRecursiveTracks,
                            customRjid = effectiveCustomRjid,
                            linkedRj = rj,
                            fallbackCover = fallbackCover
                        )
                        groupLeaves.forEach { mergedWorkDirs.add(it.canonicalPath) }
                    }
                }
            }
        }

        for (leaf in leafFolders) {
            if (mergedWorkDirs.contains(leaf.canonicalPath)) continue
            val rawAudioFiles = AudioStorageHelper.getAudioFilesInFolder(leaf)
            if (rawAudioFiles.isEmpty()) continue
            val audioFiles = filterAudioFilesByPriority(rawAudioFiles, formatPriority)
            if (audioFiles.isEmpty()) continue

            val fallbackCover = AudioStorageHelper.findCoverInPath(leaf, rootBoundary = rootDir)
            val pathRj = AudioStorageHelper.findRjCodeInPath(leaf, rootBoundary = rootDir.parentFile)
            if (isSubfolderOfExistingDownload(dao, leaf, pathRj, isImported)) continue

            val isAlbum = isMultiTrackAlbumFolder(leaf, audioFiles, isImported, rootDir)

            if (isAlbum) {
                val fileRjs = audioFiles.mapNotNull { f ->
                    AudioStorageHelper.extractRjCode(f.name) ?: AudioStorageHelper.extractRjFromAudioMetadata(f)
                }.distinct()
                val rj = pathRj ?: fileRjs.firstOrNull()
                val existingByRj = if (rj != null) dao.getHistoryById(rj) else null
                val effectiveCustomRjid = if (rj != null && existingByRj != null && existingByRj.localFilePath != leaf.absolutePath && File(existingByRj.localFilePath).exists()) {
                    "${rj}_IMPORT_${AudioStorageHelper.generateLocalId(leaf.absolutePath).takeLast(6)}"
                } else {
                    rj
                }
                syncMultiTrackFolder(
                    context = context,
                    dao = dao,
                    folder = leaf,
                    audioTracks = audioFiles,
                    customRjid = effectiveCustomRjid,
                    linkedRj = rj,
                    fallbackCover = fallbackCover
                )
            } else {
                // Bersihkan entitas virtual album palsu/usang jika folder ini sebelumnya pernah salah didaftarkan sebagai album
                val bogusAlbums = dao.getAllHistoryDirect().filter {
                    it.localFilePath == leaf.absolutePath && it.sourceType == SourceType.LOCAL_FOLDER.name
                }
                for (bogus in bogusAlbums) {
                    dao.deleteById(bogus.rjid)
                }

                // Proses file audio secara individual
                val rjCounts = mutableMapOf<String, Int>()
                for (f in audioFiles) {
                    val fileRj = AudioStorageHelper.extractRjCode(f.name)
                        ?: AudioStorageHelper.extractRjFromAudioMetadata(f)
                        ?: pathRj

                    val customId = if (fileRj != null) {
                        val count = rjCounts.getOrDefault(fileRj, 0) + 1
                        rjCounts[fileRj] = count
                        if (count == 1) fileRj else "${fileRj}_${count}"
                    } else null

                    // HANYA gunakan cover yang spesifik untuk file audio ini (companion, embedded, atau cache resmi)
                    // JANGAN gunakan blanket cover folder sembarang untuk file individual di multi-file folder!
                    val fileCover = AudioStorageHelper.findSpecificCoverForAudioFile(f, fileRj)
                        ?: AudioStorageHelper.getLocalCoverFile(context, fileRj ?: "", f.absolutePath)

                    syncSingleAudioFile(
                        context = context,
                        dao = dao,
                        file = f,
                        customRjid = customId,
                        linkedRj = fileRj,
                        fallbackCover = fileCover
                    )
                }
            }
        }
    }

    /**
     * Mengintegrasikan album multi-track sebagai satu entitas virtual di Room DB tanpa duplikasi storage.
     */
    private fun resolveFolderSourceType(isOfficialRj: Boolean, rjid: String): String {
        val isDownloadedWork = !currentScanIsImported && isOfficialRj && !rjid.contains("_IMPORT_") && !rjid.startsWith("LOCAL_")
        return if (isDownloadedWork) SourceType.DL_SITE.name else SourceType.LOCAL_FOLDER.name
    }

    private suspend fun syncMultiTrackFolder(
        context: Context,
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        folder: File,
        audioTracks: List<File>,
        customRjid: String? = null,
        linkedRj: String? = null,
        fallbackCover: File? = null
    ) {
        val rjMatch = RJ_REGEX.find(folder.name)
        val baseRj = linkedRj ?: rjMatch?.value?.uppercase()
        val rjid = customRjid ?: baseRj ?: AudioStorageHelper.generateLocalId(folder.absolutePath)
        val isOfficialRj = baseRj != null
        val finalLinkedRjId = linkedRj ?: if (isOfficialRj) baseRj else null

        val existing = dao.getHistoryById(rjid)
        val formattedDate = AudioStorageHelper.formatDateForDisplay(folder.lastModified())

        // Bangun bab virtual dari file track tanpa re-encode
        var totalBytes = 0L
        var cumMs = 0L
        val chapters = mutableListOf<ChapterItem>()

        for (track in audioTracks) {
            totalBytes += track.length()
            var durMs = 0L
            var trackTitle = track.nameWithoutExtension

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(track.absolutePath)
                durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!metaTitle.isNullOrBlank()) trackTitle = metaTitle.trim()
            } catch (e: Exception) {
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }

            val relFileName = try {
                track.relativeTo(folder).path.replace('\\', '/')
            } catch (_: Exception) {
                track.name
            }
            chapters.add(
                ChapterItem(
                    startMs = cumMs,
                    endMs = cumMs + durMs,
                    title = trackTitle,
                    fileName = relFileName
                )
            )
            cumMs += durMs
        }

        val chaptersJson = ChapterItem.toJsonArray(chapters)
        val localCover = AudioStorageHelper.findLocalCoverArt(folder)
            ?: fallbackCover
            ?: AudioStorageHelper.getLocalCoverFile(context, finalLinkedRjId ?: rjid, audioTracks.first().absolutePath)

        val cleanTitle = if (baseRj != null && folder.name != baseRj && !folder.name.contains(baseRj)) {
            val parentWorkFolder = folder.parentFile?.let { p ->
                if (AudioStorageHelper.findRjCodeInPath(p) != null) p else null
            }
            val parentTitle = parentWorkFolder?.name?.replace(baseRj, "")?.replace("[$baseRj]", "")?.trim()?.ifEmpty { null }
            if (parentTitle != null) {
                "$parentTitle (${folder.name})"
            } else {
                folder.name
            }
        } else if (isOfficialRj) {
            folder.name.replace(baseRj ?: "", "").replace("[${baseRj ?: ""}]", "").trim().ifEmpty { folder.name }
        } else {
            folder.name
        }

        val fallbackCoverUrl = if (finalLinkedRjId != null) {
            val prefix = if (finalLinkedRjId.length >= 2) finalLinkedRjId.substring(0, 2) else "RJ"
            "https://img.dlsite.jp/modpub/images2/work/doujin/${prefix}0000/${finalLinkedRjId}_img_main.jpg"
        } else ""

        val coverUrl = localCover?.absolutePath ?: fallbackCoverUrl

        val desiredSourceType = resolveFolderSourceType(isOfficialRj, rjid)

        if (existing == null) {
            val newEntity = HistoryEntity(
                rjid = rjid,
                title = cleanTitle,
                cv = "-",
                circle = "-",
                genre = "-",
                ageRating = "-",
                coverUrl = coverUrl,
                localFilePath = folder.absolutePath,
                downloadDate = formattedDate,
                fileSize = AudioDownloader.formatFileSize(totalBytes),
                chaptersJson = chaptersJson,
                sourceType = desiredSourceType,
                linkedRjId = finalLinkedRjId
            )
            dao.insertHistory(newEntity)
            AudioStorageHelper.cacheVirtualChapters(rjid, chapters)

            if (finalLinkedRjId != null) {
                scope.launch { fetchAndCacheMetadata(context, dao, newEntity) }
            }
        } else {
            var updated = existing
            var needsUpdate = false
            if (existing.localFilePath != folder.absolutePath) {
                updated = updated.copy(localFilePath = folder.absolutePath)
                needsUpdate = true
            }
            val keepDownloadType = existing.sourceType == SourceType.DL_SITE.name && !currentScanIsImported
            if (existing.sourceType != desiredSourceType && !keepDownloadType) {
                updated = updated.copy(sourceType = desiredSourceType)
                needsUpdate = true
            }
            if (existing.chaptersJson != chaptersJson) {
                updated = updated.copy(chaptersJson = chaptersJson)
                needsUpdate = true
            }
            if (existing.linkedRjId != finalLinkedRjId && finalLinkedRjId != null) {
                updated = updated.copy(linkedRjId = finalLinkedRjId)
                needsUpdate = true
            }
            if (existing.coverUrl.isBlank() && coverUrl.isNotBlank()) {
                updated = updated.copy(coverUrl = coverUrl)
                needsUpdate = true
            }
            if (needsUpdate) {
                dao.insertHistory(updated)
            }
            if (finalLinkedRjId != null && (existing.genre == "-" || existing.ageRating == "-" || existing.cv == "-")) {
                scope.launch { fetchAndCacheMetadata(context, dao, updated) }
            }
        }
    }

    /**
     * Memproses file audio tunggal (baik DLsite resmi maupun file lokal mandiri).
     */
    private suspend fun syncSingleAudioFile(
        context: Context,
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        file: File,
        customRjid: String? = null,
        linkedRj: String? = null,
        fallbackCover: File? = null
    ) {
        val rjMatch = AudioStorageHelper.extractRjCode(file.name)
            ?: AudioStorageHelper.extractRjFromAudioMetadata(file)
            ?: AudioStorageHelper.extractRjCode(file.parentFile?.name ?: "")
        val baseRj = linkedRj ?: rjMatch
        val isOfficialRj = baseRj != null
        val rjid = customRjid ?: baseRj ?: AudioStorageHelper.generateLocalId(file.absolutePath)
        val finalLinkedRjId = linkedRj ?: if (isOfficialRj) baseRj else null

        val existing = dao.getHistoryById(rjid)
        val formattedDate = AudioStorageHelper.formatDateForDisplay(file.lastModified())

        if (existing == null) {
            var title = if (baseRj != null) {
                file.nameWithoutExtension.replace("[$baseRj]", "").replace(baseRj, "").trim().ifEmpty { file.nameWithoutExtension }
            } else {
                file.nameWithoutExtension
            }
            var artist = "-"

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                if (!metaTitle.isNullOrBlank()) title = metaTitle.replace("[$baseRj]", "").trim()
                if (!metaArtist.isNullOrBlank()) artist = metaArtist.trim()
            } catch (e: Exception) {
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }

            val localCover = fallbackCover
                ?: AudioStorageHelper.findSpecificCoverForAudioFile(file, finalLinkedRjId)
                ?: AudioStorageHelper.getLocalCoverFile(context, finalLinkedRjId ?: rjid, file.absolutePath)

            val fallbackCoverUrl = if (finalLinkedRjId != null) {
                val prefix = if (finalLinkedRjId.length >= 2) finalLinkedRjId.substring(0, 2) else "RJ"
                "https://img.dlsite.jp/modpub/images2/work/doujin/${prefix}0000/${finalLinkedRjId}_img_main.jpg"
            } else ""
            val coverUrl = localCover?.absolutePath ?: fallbackCoverUrl

            val cueFile = File(file.parentFile, "${file.nameWithoutExtension}.cue")
            val existingChaps = if (cueFile.exists()) ChapterDiscoveryService.parseCueFile(cueFile) else emptyList()
            val chJson = if (existingChaps.isNotEmpty()) ChapterItem.toJsonArray(existingChaps) else null

            val newEntity = HistoryEntity(
                rjid = rjid,
                title = title,
                cv = artist,
                circle = "-",
                genre = "-",
                ageRating = "-",
                coverUrl = coverUrl,
                localFilePath = file.absolutePath,
                downloadDate = formattedDate,
                fileSize = AudioDownloader.formatFileSize(file.length()),
                chaptersJson = chJson,
                sourceType = if (isOfficialRj) SourceType.DL_SITE.name else SourceType.LOCAL_SINGLE.name,
                linkedRjId = finalLinkedRjId
            )
            dao.insertHistory(newEntity)

            if (finalLinkedRjId != null) {
                scope.launch { fetchAndCacheMetadata(context, dao, newEntity) }
            }
        } else {
            var needsUpdate = false
            var updated = existing

            if (existing.localFilePath != file.absolutePath) {
                updated = updated.copy(localFilePath = file.absolutePath)
                needsUpdate = true
            }
            if (existing.downloadDate.isBlank()) {
                updated = updated.copy(downloadDate = formattedDate)
                needsUpdate = true
            }
            if (existing.linkedRjId != finalLinkedRjId && finalLinkedRjId != null) {
                updated = updated.copy(linkedRjId = finalLinkedRjId)
                needsUpdate = true
            }
            val isMismatchedFolderCover = AudioStorageHelper.isMismatchedFolderCover(file, existing.coverUrl, finalLinkedRjId)
            val fallbackCoverUrl = if (finalLinkedRjId != null) {
                val prefix = if (finalLinkedRjId.length >= 2) finalLinkedRjId.substring(0, 2) else "RJ"
                "https://img.dlsite.jp/modpub/images2/work/doujin/${prefix}0000/${finalLinkedRjId}_img_main.jpg"
            } else ""

            if (isMismatchedFolderCover) {
                val correctedCover = fallbackCover
                    ?: AudioStorageHelper.findSpecificCoverForAudioFile(file, finalLinkedRjId)
                    ?: AudioStorageHelper.getLocalCoverFile(context, finalLinkedRjId ?: rjid, file.absolutePath)
                val newCoverUrl = correctedCover?.absolutePath ?: fallbackCoverUrl
                if (newCoverUrl != existing.coverUrl) {
                    updated = updated.copy(coverUrl = newCoverUrl)
                    needsUpdate = true
                }
            } else if (existing.coverUrl.isBlank() && fallbackCover != null) {
                updated = updated.copy(coverUrl = fallbackCover.absolutePath)
                needsUpdate = true
            }

            val isCoverPlaceholder = updated.coverUrl.isBlank() ||
                updated.coverUrl.contains("modpub/images2") ||
                isMismatchedFolderCover
            val isMetaIncomplete = updated.title.isBlank() || updated.title == rjid || updated.cv == "-" || updated.circle == "-"

            if (isOfficialRj && (isCoverPlaceholder || isMetaIncomplete)) {
                scope.launch { fetchAndCacheMetadata(context, dao, updated) }
            }

            if (needsUpdate) {
                dao.insertHistory(updated)
            }
            if (finalLinkedRjId != null && (existing.genre == "-" || existing.ageRating == "-" || existing.cv == "-")) {
                scope.launch { fetchAndCacheMetadata(context, dao, updated) }
            }
        }
    }

    /**
     * Memindai via SAF DocumentFile untuk storage yang hanya mengizinkan Document Tree URI.
     */
    private suspend fun scanSafDirectory(
        context: Context,
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        uriString: String
    ) {
        try {
            val docDir = DocumentFile.fromTreeUri(context, Uri.parse(uriString)) ?: return
            if (!docDir.exists() || !docDir.isDirectory) return

            suspend fun scanDocRecursive(dir: DocumentFile, depth: Int, parentRj: String?) {
                if (depth > 5) return
                val rjHere = AudioStorageHelper.extractRjCode(dir.name.orEmpty()) ?: parentRj
                val children = dir.listFiles()
                val audioDocs = children.filter { it.isFile && AudioStorageHelper.AUDIO_EXTENSIONS.contains(it.name?.substringAfterLast('.', "")?.lowercase()) }

                if (audioDocs.isNotEmpty()) {
                    val rjCode = rjHere ?: audioDocs.firstNotNullOfOrNull { AudioStorageHelper.extractRjCode(it.name.orEmpty()) }
                    val isOfficialRj = rjCode != null
                    val rjid = rjCode ?: AudioStorageHelper.generateLocalId(dir.uri.toString())

                    val existing = dao.getHistoryById(rjid)
                    if (existing == null) {
                        val firstDoc = audioDocs.first()
                        val newEntity = HistoryEntity(
                            rjid = rjid,
                            title = dir.name ?: firstDoc.name?.substringBeforeLast('.') ?: rjid,
                            cv = "-",
                            circle = "-",
                            genre = "-",
                            ageRating = "-",
                            coverUrl = "",
                            localFilePath = if (audioDocs.size >= 2) dir.uri.toString() else firstDoc.uri.toString(),
                            downloadDate = AudioStorageHelper.formatDateForDisplay(firstDoc.lastModified()),
                            fileSize = AudioDownloader.formatFileSize(audioDocs.sumOf { it.length() }),
                            chaptersJson = null,
                            sourceType = if (audioDocs.size >= 2) SourceType.LOCAL_FOLDER.name else (if (isOfficialRj) SourceType.DL_SITE.name else SourceType.LOCAL_SINGLE.name),
                            linkedRjId = rjCode
                        )
                        dao.insertHistory(newEntity)
                        if (isOfficialRj) {
                            scope.launch { fetchAndCacheMetadata(context, dao, newEntity) }
                        }
                    } else if (isOfficialRj && (existing.genre == "-" || existing.ageRating == "-" || existing.cv == "-")) {
                        scope.launch { fetchAndCacheMetadata(context, dao, existing) }
                    }
                }

                for (child in children) {
                    if (child.isDirectory && !child.name.orEmpty().startsWith(".")) {
                        scanDocRecursive(child, depth + 1, rjHere)
                    }
                }
            }

            scanDocRecursive(docDir, 1, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Mengambil metadata lengkap dari DLsite dan mengunduh cover art ke penyimpanan cache offline.
     */
    private suspend fun fetchAndCacheMetadata(
        context: Context,
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        entity: HistoryEntity
    ) {
        try {
            val rjid = entity.linkedRjId ?: entity.rjid
            val meta = DLsiteScraper.fetchMetadata(rjid)

            val audioF = File(entity.localFilePath)
            val isMismatched = if (audioF.isFile) AudioStorageHelper.isMismatchedFolderCover(audioF, entity.coverUrl, rjid) else false
            var resolvedCoverPath = if (isMismatched) "" else entity.coverUrl
            val localCover = AudioStorageHelper.getLocalCoverFile(context, rjid, entity.localFilePath)

            if (localCover != null && localCover.exists() && localCover.length() > 0) {
                resolvedCoverPath = localCover.absolutePath
            } else {
                val targetCoverUrl = if (!meta.coverUrl.isNullOrBlank()) meta.coverUrl else DLsiteScraper.resolveBestCoverUrl(rjid)
                if (targetCoverUrl.isNotBlank()) {
                    val coversDir = File(context.cacheDir, "covers").apply { if (!exists()) mkdirs() }
                    val targetCoverFile = File(coversDir, "${rjid.uppercase()}.jpg")

                    val req = Request.Builder()
                        .url(targetCoverUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Referer", "https://japaneseasmr.com")
                        .build()

                    val resp = okHttpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            FileOutputStream(targetCoverFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (targetCoverFile.exists() && targetCoverFile.length() > 0) {
                            resolvedCoverPath = targetCoverFile.absolutePath
                        }
                    }
                }
            }

            var resolvedChaptersJson = entity.chaptersJson
            if (resolvedChaptersJson.isNullOrBlank()) {
                val audioF = File(entity.localFilePath)
                if (audioF.isFile) {
                    val cueF = File(audioF.parentFile, "${audioF.nameWithoutExtension}.cue")
                    if (cueF.exists()) {
                        val chaps = ChapterDiscoveryService.parseCueFile(cueF)
                        if (chaps.isNotEmpty()) {
                            resolvedChaptersJson = ChapterItem.toJsonArray(chaps)
                        }
                    }
                }
            }

            val variantSuffix = if (entity.rjid.contains("_") && entity.rjid != rjid) {
                " (${entity.rjid.substringAfter("_")})"
            } else ""

            val updatedTitle = if (meta.title.isNotBlank() && meta.title != rjid) {
                if (variantSuffix.isNotBlank()) "${meta.title}$variantSuffix" else meta.title
            } else entity.title

            val finalEntity = entity.copy(
                title = updatedTitle,
                circle = if (meta.circle.isNotBlank() && meta.circle != "-") meta.circle else entity.circle,
                cv = if (meta.cv.isNotBlank() && meta.cv != "-") meta.cv else entity.cv,
                genre = if (meta.genre.isNotBlank() && meta.genre != "-") meta.genre else entity.genre,
                ageRating = if (meta.ageRating.isNotBlank() && meta.ageRating != "-") meta.ageRating else entity.ageRating,
                coverUrl = resolvedCoverPath,
                chaptersJson = resolvedChaptersJson,
                linkedRjId = rjid
            )
            dao.insertHistory(finalEntity)
            _syncTick.value = System.currentTimeMillis()
        } catch (e: Exception) {
            // Ignore offline network errors
        }
    }
}
