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

    val RJ_REGEX = Regex("((?:RJ|VJ|BJ)\\d{6,8})", RegexOption.IGNORE_CASE)

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

        if (directFile != null && directFile.exists() && directFile.isDirectory) {
            val folderName = directFile.name.ifBlank { "Folder" }
            var singleCount = 0
            var multiAlbumCount = 0
            var totalAudioTracks = 0
            var unreadableCount = 0

            // 1. Cek immediate subfolders
            val subfolders = directFile.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()
            for (sub in subfolders) {
                val audios = AudioStorageHelper.getAudioFilesInFolder(sub)
                if (audios.size >= 2) {
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

            // 2. Cek root audio files
            val rootAudios = AudioStorageHelper.getAudioFilesInFolder(directFile)
            if (rootAudios.isNotEmpty()) {
                if (multiAlbumCount == 0 && singleCount == 0 && rootAudios.size >= 2) {
                    // User memilih folder album multi-track secara langsung
                    multiAlbumCount = 1
                    totalAudioTracks = rootAudios.size
                    for (f in rootAudios) {
                        if (!f.canRead() || f.length() == 0L) unreadableCount++
                    }
                } else {
                    singleCount += rootAudios.size
                    totalAudioTracks += rootAudios.size
                    for (f in rootAudios) {
                        if (!f.canRead() || f.length() == 0L) unreadableCount++
                    }
                }
            }

            return@withContext ImportPreviewData(
                singleCount = singleCount,
                multiAlbumCount = multiAlbumCount,
                totalAudioTracks = totalAudioTracks,
                unreadableCount = unreadableCount,
                folderPathOrUri = uriOrPath,
                folderName = folderName
            )
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

                val children = docDir.listFiles()
                for (doc in children) {
                    if (doc.isDirectory && !doc.name.orEmpty().startsWith(".")) {
                        val subAudios = doc.listFiles().filter { it.isFile && AudioStorageHelper.AUDIO_EXTENSIONS.contains(it.name?.substringAfterLast('.', "")?.lowercase()) }
                        if (subAudios.size >= 2) {
                            multiAlbumCount++
                            totalAudioTracks += subAudios.size
                        } else if (subAudios.size == 1) {
                            singleCount++
                            totalAudioTracks += 1
                        }
                    } else if (doc.isFile && AudioStorageHelper.AUDIO_EXTENSIONS.contains(doc.name?.substringAfterLast('.', "")?.lowercase())) {
                        singleCount++
                        totalAudioTracks++
                    }
                }

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

                // 1. Normalisasi semua format tanggal lama di database secara batch
                val existingHistoryList = dao.getAllHistoryDirect()
                for (item in existingHistoryList) {
                    val normalized = AudioStorageHelper.normalizeDateString(item.downloadDate, item.localFilePath)
                    if (normalized != item.downloadDate) {
                        dao.insertHistory(item.copy(downloadDate = normalized))
                    }
                }

                // 2. Scan setiap folder target
                for (dirEntry in allTargetDirs) {
                    val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(context, dirEntry) ?: dirEntry
                    val dirFile = File(resolvedPath)

                    if (dirFile.exists() && dirFile.isDirectory) {
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
     * Memindai folder fisik: membedakan multi-track album dan single track audio.
     */
    private suspend fun scanPhysicalDirectory(
        context: Context,
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        rootDir: File,
        isImported: Boolean
    ) {
        val subfolders = rootDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()

        // 1. Periksa subfolder langsung
        for (sub in subfolders) {
            val audioFiles = AudioStorageHelper.getAudioFilesInFolder(sub)
            if (audioFiles.size >= 2) {
                // Multi-track Virtual Album
                syncMultiTrackFolder(context, dao, sub, audioFiles)
            } else if (audioFiles.size == 1) {
                // Single audio track di dalam subfolder
                syncSingleAudioFile(context, dao, audioFiles.first())
            }
        }

        // 2. Periksa audio files di root folder itu sendiri
        val rootAudios = AudioStorageHelper.getAudioFilesInFolder(rootDir)
        if (rootAudios.isNotEmpty()) {
            if (subfolders.isEmpty() && rootAudios.size >= 2 && isImported) {
                // User mengimpor folder album multi-track secara langsung
                syncMultiTrackFolder(context, dao, rootDir, rootAudios)
            } else {
                for (f in rootAudios) {
                    syncSingleAudioFile(context, dao, f)
                }
            }
        }
    }

    /**
     * Mengintegrasikan album multi-track sebagai satu entitas virtual di Room DB tanpa duplikasi storage.
     */
    private suspend fun syncMultiTrackFolder(
        context: Context,
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        folder: File,
        audioTracks: List<File>
    ) {
        val rjMatch = RJ_REGEX.find(folder.name)
        val rjid = rjMatch?.value?.uppercase() ?: AudioStorageHelper.generateLocalId(folder.absolutePath)
        val isOfficialRj = rjMatch != null

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

            chapters.add(
                ChapterItem(
                    startMs = cumMs,
                    endMs = cumMs + durMs,
                    title = trackTitle,
                    fileName = track.name
                )
            )
            cumMs += durMs
        }

        val chaptersJson = ChapterItem.toJsonArray(chapters)
        val localCover = AudioStorageHelper.findLocalCoverArt(folder)
            ?: AudioStorageHelper.getLocalCoverFile(context, rjid, audioTracks.first().absolutePath)

        val cleanTitle = if (isOfficialRj) {
            folder.name.replace(rjid, "").replace("[$rjid]", "").trim().ifEmpty { folder.name }
        } else {
            folder.name
        }

        val fallbackCoverUrl = if (isOfficialRj) {
            val prefix = if (rjid.length >= 2) rjid.substring(0, 2) else "RJ"
            "https://img.dlsite.jp/modpub/images2/work/doujin/${prefix}0000/${rjid}_img_main.jpg"
        } else ""

        val coverUrl = localCover?.absolutePath ?: fallbackCoverUrl

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
                sourceType = SourceType.LOCAL_FOLDER.name,
                linkedRjId = if (isOfficialRj) rjid else null
            )
            dao.insertHistory(newEntity)

            if (isOfficialRj) {
                scope.launch { fetchAndCacheMetadata(context, dao, newEntity) }
            }
        } else {
            var updated = existing
            var needsUpdate = false
            if (existing.localFilePath != folder.absolutePath) {
                updated = updated.copy(localFilePath = folder.absolutePath)
                needsUpdate = true
            }
            if (existing.sourceType != SourceType.LOCAL_FOLDER.name) {
                updated = updated.copy(sourceType = SourceType.LOCAL_FOLDER.name)
                needsUpdate = true
            }
            if (existing.chaptersJson != chaptersJson) {
                updated = updated.copy(chaptersJson = chaptersJson)
                needsUpdate = true
            }
            if (needsUpdate) {
                dao.insertHistory(updated)
            }
        }
    }

    /**
     * Memproses file audio tunggal (baik DLsite resmi maupun file lokal mandiri).
     */
    private suspend fun syncSingleAudioFile(
        context: Context,
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        file: File
    ) {
        val rjMatch = RJ_REGEX.find(file.name) ?: RJ_REGEX.find(file.parentFile?.name ?: "")
        val isOfficialRj = rjMatch != null
        val rjid = rjMatch?.value?.uppercase() ?: AudioStorageHelper.generateLocalId(file.absolutePath)

        val existing = dao.getHistoryById(rjid)
        val formattedDate = AudioStorageHelper.formatDateForDisplay(file.lastModified())

        if (existing == null) {
            var title = file.nameWithoutExtension.replace("[$rjid]", "").replace(rjid, "").trim().ifEmpty { file.nameWithoutExtension }
            var artist = "-"

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                if (!metaTitle.isNullOrBlank()) title = metaTitle.replace("[$rjid]", "").trim()
                if (!metaArtist.isNullOrBlank()) artist = metaArtist.trim()
            } catch (e: Exception) {
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }

            val localCover = AudioStorageHelper.getLocalCoverFile(context, rjid, file.absolutePath)
            val fallbackCoverUrl = if (isOfficialRj) {
                val prefix = if (rjid.length >= 2) rjid.substring(0, 2) else "RJ"
                "https://img.dlsite.jp/modpub/images2/work/doujin/${prefix}0000/${rjid}_img_main.jpg"
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
                linkedRjId = if (isOfficialRj) rjid else null
            )
            dao.insertHistory(newEntity)

            if (isOfficialRj) {
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

            val isCoverPlaceholder = existing.coverUrl.isBlank() || existing.coverUrl.contains("modpub/images2")
            val isMetaIncomplete = existing.title.isBlank() || existing.title == rjid || existing.cv == "-" || existing.circle == "-"

            if (isOfficialRj && (isCoverPlaceholder || isMetaIncomplete)) {
                scope.launch { fetchAndCacheMetadata(context, dao, existing) }
            }

            if (needsUpdate) {
                dao.insertHistory(updated)
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

            for (doc in docDir.listFiles()) {
                if (doc.isFile && AudioStorageHelper.AUDIO_EXTENSIONS.contains(doc.name?.substringAfterLast('.', "")?.lowercase())) {
                    val rjMatch = RJ_REGEX.find(doc.name.orEmpty())
                    val isOfficialRj = rjMatch != null
                    val rjid = rjMatch?.value?.uppercase() ?: AudioStorageHelper.generateLocalId(doc.uri.toString())

                    val existing = dao.getHistoryById(rjid)
                    if (existing == null) {
                        val newEntity = HistoryEntity(
                            rjid = rjid,
                            title = doc.name?.substringBeforeLast('.') ?: rjid,
                            cv = "-",
                            circle = "-",
                            genre = "-",
                            ageRating = "-",
                            coverUrl = "",
                            localFilePath = doc.uri.toString(),
                            downloadDate = AudioStorageHelper.formatDateForDisplay(doc.lastModified()),
                            fileSize = AudioDownloader.formatFileSize(doc.length()),
                            chaptersJson = null,
                            sourceType = if (isOfficialRj) SourceType.DL_SITE.name else SourceType.LOCAL_SINGLE.name,
                            linkedRjId = if (isOfficialRj) rjid else null
                        )
                        dao.insertHistory(newEntity)
                        if (isOfficialRj) {
                            scope.launch { fetchAndCacheMetadata(context, dao, newEntity) }
                        }
                    }
                }
            }
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

            var resolvedCoverPath = entity.coverUrl
            val localCover = AudioStorageHelper.getLocalCoverFile(context, rjid, entity.localFilePath)

            if (localCover != null && localCover.exists() && localCover.length() > 0) {
                resolvedCoverPath = localCover.absolutePath
            } else if (!meta.coverUrl.isNullOrBlank()) {
                val coversDir = File(context.cacheDir, "covers").apply { if (!exists()) mkdirs() }
                val targetCoverFile = File(coversDir, "${rjid.uppercase()}.jpg")

                val req = Request.Builder()
                    .url(meta.coverUrl)
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

            val finalEntity = entity.copy(
                title = if (meta.title.isNotBlank() && meta.title != rjid) meta.title else entity.title,
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
