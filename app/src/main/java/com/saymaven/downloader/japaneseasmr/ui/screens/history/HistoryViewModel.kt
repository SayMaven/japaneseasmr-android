package com.saymaven.downloader.japaneseasmr.ui.screens.history

import android.app.Application
import android.media.MediaMetadataRetriever
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import com.saymaven.downloader.japaneseasmr.service.AudioDownloader
import com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val dao = AsmrDatabase.getDatabase(application).historyDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    init {
        syncStorageWithDatabase()
    }

    val historyList = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            dao.getAllHistory()
        } else {
            dao.searchHistory(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Memindai folder unduhan dan otomatis memulihkan seluruh riwayat audio yang ada di penyimpanan HP.
     */
    fun syncStorageWithDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val customDirStr = prefs.downloadDirFlow.first()
                val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()

                if (!downloadDir.exists() || !downloadDir.isDirectory) return@launch

                val audioFiles = downloadDir.listFiles { file ->
                    file.isFile && (file.extension.equals("m4a", true) || file.extension.equals("mp3", true) || file.extension.equals("aac", true))
                } ?: return@launch

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

                        val coverFile = File(downloadDir, "${rjid}_cover.jpg")
                        val coverUrl = if (coverFile.exists()) coverFile.absolutePath else "https://img.dlsite.jp/modpub/images2/work/doujin/${rjid.substring(0, 2)}0000/${rjid}_img_main.jpg"

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

                        // Update metadata scraper di background
                        launch {
                            try {
                                val meta = DLsiteScraper.fetchMetadata(rjid)
                                dao.insertHistory(
                                    restored.copy(
                                        title = meta.title,
                                        circle = meta.circle,
                                        cv = meta.cv,
                                        coverUrl = meta.coverUrl
                                    )
                                )
                            } catch (e: Exception) {}
                        }
                    } else if (existing.localFilePath != f.absolutePath) {
                        dao.insertHistory(existing.copy(localFilePath = f.absolutePath))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun isFilePresent(item: HistoryEntity): Boolean {
        val direct = File(item.localFilePath)
        if (direct.exists() && direct.length() > 0) return true
        val defaultDir = DownloadService.getDefaultDownloadDirectory()
        return AudioStorageHelper.findExistingAudioFile(defaultDir, item.rjid) != null
    }

    fun deleteHistory(item: HistoryEntity, deleteFile: Boolean = false) {
        viewModelScope.launch {
            if (deleteFile) {
                try {
                    val file = File(item.localFilePath)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            dao.deleteHistory(item)
        }
    }

    fun cleanMissingFiles() {
        viewModelScope.launch {
            val list = historyList.value
            val customDirStr = prefs.downloadDirFlow.first()
            val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()

            for (item in list) {
                val resolved = AudioStorageHelper.resolveValidAudioFile(downloadDir, item.localFilePath, item.rjid)
                if (resolved == null) {
                    dao.deleteHistory(item)
                } else if (resolved.absolutePath != item.localFilePath) {
                    dao.insertHistory(item.copy(localFilePath = resolved.absolutePath))
                }
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.clearAll()
        }
    }
}
