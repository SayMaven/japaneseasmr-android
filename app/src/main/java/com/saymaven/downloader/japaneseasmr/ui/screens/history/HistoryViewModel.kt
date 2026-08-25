package com.saymaven.downloader.japaneseasmr.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.request.ImageRequest
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.service.StorageSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class HistorySortOrder {
    DATE_DESC, // Waktu Terbaru (Default - Audio Baru Paling Atas)
    DATE_ASC,  // Waktu Terlama
    TITLE_ASC, // Nama A - Z
    TITLE_DESC // Nama Z - A
}

data class HistoryUiItem(
    val entity: HistoryEntity,
    val rjid: String,
    val title: String,
    val cv: String,
    val circle: String,
    val coverUrl: String?,
    val downloadDate: String,
    val fileSize: String,
    val isPresent: Boolean
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val dao = AsmrDatabase.getDatabase(application).historyDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(HistorySortOrder.DATE_DESC)
    val sortOrder = _sortOrder.asStateFlow()

    // O(1) Instant presence cache to eliminate disk I/O on UI thread during scroll
    private val filePresenceCache = ConcurrentHashMap<String, Boolean>()

    init {
        syncStorageWithDatabase()
        preloadHistoryCovers()
    }

    val historyUiList: StateFlow<List<HistoryUiItem>> = combine(_searchQuery, _sortOrder) { query, order ->
        Pair(query, order)
    }.flatMapLatest { (query, order) ->
        val flow = if (query.isBlank()) {
            dao.getAllHistory()
        } else {
            dao.searchHistory(query)
        }
        flow.map { list ->
            val customDirStr = prefs.downloadDirFlow.first()
            val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), customDirStr) ?: customDirStr
            val downloadDir = if (!resolvedPath.isNullOrBlank()) {
                val f = File(resolvedPath)
                if (f.exists() && f.isDirectory) f else DownloadService.getDefaultDownloadDirectory()
            } else {
                DownloadService.getDefaultDownloadDirectory()
            }

            val resolved = list.map { item ->
                val localCover = AudioStorageHelper.getLocalCoverFile(getApplication(), item.rjid, item.localFilePath)
                val normalizedDate = AudioStorageHelper.normalizeDateString(item.downloadDate, item.localFilePath)
                val cover = if (localCover != null && localCover.exists()) localCover.absolutePath else item.coverUrl

                val isPresent = isFilePresentInternal(item, downloadDir)

                HistoryUiItem(
                    entity = item.copy(coverUrl = cover, downloadDate = normalizedDate),
                    rjid = item.rjid,
                    title = item.title,
                    cv = item.cv,
                    circle = item.circle,
                    coverUrl = cover,
                    downloadDate = normalizedDate,
                    fileSize = item.fileSize,
                    isPresent = isPresent
                )
            }

            // Pengurutan berbasis Timestamp Milidetik Nyata
            when (order) {
                HistorySortOrder.DATE_DESC -> resolved.sortedByDescending {
                    AudioStorageHelper.parseDateToTimestamp(it.downloadDate, it.entity.localFilePath)
                }
                HistorySortOrder.DATE_ASC -> resolved.sortedBy {
                    AudioStorageHelper.parseDateToTimestamp(it.downloadDate, it.entity.localFilePath)
                }
                HistorySortOrder.TITLE_ASC -> resolved.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                HistorySortOrder.TITLE_DESC -> resolved.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            }
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyList: StateFlow<List<HistoryEntity>> = historyUiList.map { list ->
        list.map { it.entity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasMissingFiles: StateFlow<Boolean> = historyUiList.map { list ->
        list.any { !it.isPresent }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun isFilePresentInternal(item: HistoryEntity, downloadDir: File): Boolean {
        val key = "${item.rjid}_${item.localFilePath}"
        return filePresenceCache.getOrPut(key) {
            val path = item.localFilePath
            if (!path.isNullOrBlank()) {
                val direct = File(path)
                if (direct.exists() && direct.length() > 0) return@getOrPut true
            }
            AudioStorageHelper.findExistingAudioFile(downloadDir, item.rjid) != null
        }
    }

    private fun preloadHistoryCovers() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllHistory().collect { list ->
                list.take(40).forEach { item ->
                    val localCover = AudioStorageHelper.getLocalCoverFile(getApplication(), item.rjid, item.localFilePath)
                    val url = localCover?.absolutePath ?: item.coverUrl
                    if (!url.isNullOrBlank()) {
                        try {
                            val req = ImageRequest.Builder(getApplication())
                                .data(url)
                                .memoryCacheKey(url)
                                .size(140, 104)
                                .crossfade(false)
                                .build()
                            Coil.imageLoader(getApplication()).enqueue(req)
                        } catch (ignored: Exception) {}
                    }
                }
            }
        }
    }

    fun toggleSortByDate() {
        _sortOrder.value = if (_sortOrder.value == HistorySortOrder.DATE_DESC) {
            HistorySortOrder.DATE_ASC
        } else {
            HistorySortOrder.DATE_DESC
        }
    }

    fun toggleSortByTitle() {
        _sortOrder.value = if (_sortOrder.value == HistorySortOrder.TITLE_ASC) {
            HistorySortOrder.TITLE_DESC
        } else {
            HistorySortOrder.TITLE_ASC
        }
    }

    fun syncStorageWithDatabase() {
        filePresenceCache.clear()
        StorageSyncManager.syncStorageWithDatabase(getApplication())
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun isFilePresent(item: HistoryEntity): Boolean {
        val key = "${item.rjid}_${item.localFilePath}"
        return filePresenceCache.getOrPut(key) {
            val path = item.localFilePath
            if (!path.isNullOrBlank()) {
                val direct = File(path)
                if (direct.exists() && direct.length() > 0) return@getOrPut true
            }
            val defaultDir = DownloadService.getDefaultDownloadDirectory()
            AudioStorageHelper.findExistingAudioFile(defaultDir, item.rjid) != null
        }
    }

    fun deleteHistory(item: HistoryEntity, deleteFile: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            filePresenceCache.clear()
            if (deleteFile) {
                val path = item.localFilePath
                if (!path.isNullOrBlank()) {
                    try {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            dao.deleteHistory(item)
        }
    }

    fun cleanMissingFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            filePresenceCache.clear()
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
        viewModelScope.launch(Dispatchers.IO) {
            filePresenceCache.clear()
            dao.clearAll()
        }
    }
}
