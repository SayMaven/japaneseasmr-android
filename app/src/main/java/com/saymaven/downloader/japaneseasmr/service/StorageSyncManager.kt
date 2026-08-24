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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageSyncManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _syncTick = MutableStateFlow<Long>(0L)
    val syncTick = _syncTick.asStateFlow()

    /**
     * Memindai direktori unduhan secara realtime.
     * Jika ada file audio baru di memori yang belum ada di database, otomatis mengambil metadata dan menambahkannya ke riwayat.
     * Jika ada path file yang berubah/pindah/kembali, otomatis diperbarui dan memicu update realtime ke seluruh layar.
     */
    fun syncStorageWithDatabase(context: Context) {
        scope.launch {
            try {
                val prefs = PreferencesManager(context)
                val dao = AsmrDatabase.getDatabase(context).historyDao()

                val customDirStr = prefs.downloadDirFlow.first()
                val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()

                if (downloadDir.exists() && downloadDir.isDirectory) {
                    val audioFiles = downloadDir.listFiles { file ->
                        file.isFile && (file.extension.equals("m4a", true) || file.extension.equals("mp3", true) || file.extension.equals("aac", true) || file.extension.equals("flac", true) || file.extension.equals("wav", true))
                    } ?: emptyArray()

                    val rjRegex = Regex("(RJ\\d{6,8})", RegexOption.IGNORE_CASE)
                    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

                    for (f in audioFiles) {
                        val match = rjRegex.find(f.name) ?: continue
                        val rjid = match.value.uppercase()

                        val existing = dao.getHistoryById(rjid)
                        if (existing == null) {
                            val retriever = MediaMetadataRetriever()
                            var title = f.nameWithoutExtension.replace("[$rjid]", "").trim().ifEmpty { rjid }
                            var artist = "-"
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
                            val coverUrl = localCover?.absolutePath ?: "https://img.dlsite.jp/modpub/images2/work/doujin/${prefix}0000/${rjid}_img_main.jpg"

                            val restored = HistoryEntity(
                                rjid = rjid,
                                title = title,
                                cv = artist,
                                circle = "-",
                                genre = "-",
                                ageRating = "-",
                                coverUrl = coverUrl,
                                localFilePath = f.absolutePath,
                                downloadDate = dateFormat.format(Date(f.lastModified())),
                                fileSize = AudioDownloader.formatFileSize(f.length())
                            )
                            dao.insertHistory(restored)

                            launch {
                                try {
                                    val meta = DLsiteScraper.fetchMetadata(rjid)
                                    dao.insertHistory(
                                        restored.copy(
                                            title = meta.title,
                                            circle = meta.circle,
                                            cv = meta.cv,
                                            coverUrl = localCover?.absolutePath ?: meta.coverUrl
                                        )
                                    )
                                } catch (e: Exception) {}
                            }
                        } else if (existing.localFilePath != f.absolutePath) {
                            dao.insertHistory(existing.copy(localFilePath = f.absolutePath))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _syncTick.value = System.currentTimeMillis()
            }
        }
    }
}
