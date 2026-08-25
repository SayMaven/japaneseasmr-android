package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import android.media.MediaMetadataRetriever
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object StorageSyncManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val syncMutex = Mutex()
    private var lastSyncTime = 0L

    private val _syncTick = MutableStateFlow<Long>(0L)
    val syncTick = _syncTick.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Memindai direktori unduhan dan subfolder secara rekursif di background IO (120 FPS UI safety).
     * Mengambil metadata & cover otomatis jika berkas belum terdaftar.
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
                val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(context, rawDirStr) ?: rawDirStr
                val downloadDir = if (!resolvedPath.isNullOrBlank()) File(resolvedPath) else DownloadService.getDefaultDownloadDirectory()

                if (!downloadDir.exists() || !downloadDir.isDirectory) {
                    return@launch
                }

                // 1. Normalisasi semua format tanggal lama di database secara batch
                val existingHistoryList = dao.getAllHistoryDirect()
                for (item in existingHistoryList) {
                    val normalized = AudioStorageHelper.normalizeDateString(item.downloadDate, item.localFilePath)
                    if (normalized != item.downloadDate) {
                        dao.insertHistory(item.copy(downloadDate = normalized))
                    }
                }

                // 2. Pemindaian rekursif seluruh berkas audio di root dan seluruh subfolder (kedalaman 5 level)
                val audioFiles = try {
                    downloadDir.walkTopDown().maxDepth(5).filter { f ->
                        f.isFile && f.length() > 0L && AudioStorageHelper.AUDIO_EXTENSIONS.contains(f.extension.lowercase())
                    }.toList()
                } catch (e: Exception) {
                    emptyList()
                }

                val rjRegex = Regex("((?:RJ|VJ|BJ)\\d{6,8})", RegexOption.IGNORE_CASE)

                for (f in audioFiles) {
                    // Cari kode RJ di nama file atau di nama folder induknya
                    val match = rjRegex.find(f.name) ?: rjRegex.find(f.parentFile?.name ?: "") ?: continue
                    val rjid = match.value.uppercase()

                    val existing = dao.getHistoryById(rjid)
                    val formattedDate = AudioStorageHelper.formatDateForDisplay(f.lastModified())

                    if (existing == null) {
                        // File Baru yang belum ada di Database: Ekstrak lokal + Auto-Fetch DLsite
                        var title = f.nameWithoutExtension.replace("[$rjid]", "").replace(rjid, "").trim().ifEmpty { rjid }
                        var artist = "-"

                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(f.absolutePath)
                            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            if (!metaTitle.isNullOrBlank()) title = metaTitle.replace("[$rjid]", "").trim()
                            if (!metaArtist.isNullOrBlank()) artist = metaArtist.trim()
                        } catch (e: Exception) {
                        } finally {
                            try { retriever.release() } catch (e: Exception) {}
                        }

                        val localCover = AudioStorageHelper.getLocalCoverFile(context, rjid, f.absolutePath)
                        val prefix = if (rjid.length >= 2) rjid.substring(0, 2) else "RJ"
                        val fallbackCoverUrl = "https://img.dlsite.jp/modpub/images2/work/doujin/${prefix}0000/${rjid}_img_main.jpg"
                        val coverUrl = localCover?.absolutePath ?: fallbackCoverUrl

                        val newEntity = HistoryEntity(
                            rjid = rjid,
                            title = title,
                            cv = artist,
                            circle = "-",
                            genre = "-",
                            ageRating = "-",
                            coverUrl = coverUrl,
                            localFilePath = f.absolutePath,
                            downloadDate = formattedDate,
                            fileSize = AudioDownloader.formatFileSize(f.length())
                        )
                        dao.insertHistory(newEntity)

                        // Auto-Fetch DLsite Metadata & Download Cover di Background
                        scope.launch {
                            fetchAndCacheMetadata(context, dao, newEntity)
                        }
                    } else {
                        // File sudah ada: Perbarui path jika file dipindahkan ke subfolder
                        var needsUpdate = false
                        var updated = existing

                        if (existing.localFilePath != f.absolutePath) {
                            updated = updated.copy(localFilePath = f.absolutePath)
                            needsUpdate = true
                        }
                        if (existing.downloadDate.isBlank()) {
                            updated = updated.copy(downloadDate = formattedDate)
                            needsUpdate = true
                        }

                        // Jika metadata atau cover masih kosong / placeholder, coba auto-fetch
                        val isCoverPlaceholder = existing.coverUrl.isBlank() || existing.coverUrl.contains("modpub/images2")
                        val isMetaIncomplete = existing.title.isBlank() || existing.title == rjid || existing.cv == "-" || existing.circle == "-"

                        if (isCoverPlaceholder || isMetaIncomplete) {
                            scope.launch {
                                fetchAndCacheMetadata(context, dao, existing)
                            }
                        }

                        if (needsUpdate) {
                            dao.insertHistory(updated)
                        }
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
     * Mengambil metadata lengkap dari DLsite dan mengunduh cover art ke penyimpanan cache offline.
     */
    private suspend fun fetchAndCacheMetadata(
        context: Context,
        dao: com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao,
        entity: HistoryEntity
    ) {
        try {
            val rjid = entity.rjid
            val meta = DLsiteScraper.fetchMetadata(rjid)

            var resolvedCoverPath = entity.coverUrl
            val localCover = AudioStorageHelper.getLocalCoverFile(context, rjid, entity.localFilePath)

            if (localCover != null && localCover.exists() && localCover.length() > 0) {
                resolvedCoverPath = localCover.absolutePath
            } else if (!meta.coverUrl.isNullOrBlank()) {
                // Download cover image to local cache
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

            val finalEntity = entity.copy(
                title = if (meta.title.isNotBlank() && meta.title != rjid) meta.title else entity.title,
                circle = if (meta.circle.isNotBlank() && meta.circle != "-") meta.circle else entity.circle,
                cv = if (meta.cv.isNotBlank() && meta.cv != "-") meta.cv else entity.cv,
                genre = if (meta.genre.isNotBlank() && meta.genre != "-") meta.genre else entity.genre,
                ageRating = if (meta.ageRating.isNotBlank() && meta.ageRating != "-") meta.ageRating else entity.ageRating,
                coverUrl = resolvedCoverPath
            )
            dao.insertHistory(finalEntity)
            _syncTick.value = System.currentTimeMillis()
        } catch (e: Exception) {
            // Ignore offline network errors
        }
    }
}
