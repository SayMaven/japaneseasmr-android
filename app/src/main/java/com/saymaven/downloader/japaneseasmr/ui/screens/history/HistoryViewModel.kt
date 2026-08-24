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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val dao = AsmrDatabase.getDatabase(application).historyDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    init {
        syncStorageWithDatabase()
        preloadHistoryCovers()
    }

    val historyList = _searchQuery.flatMapLatest { query ->
        val flow = if (query.isBlank()) {
            dao.getAllHistory()
        } else {
            dao.searchHistory(query)
        }
        flow.map { list ->
            list.map { item ->
                val localCover = AudioStorageHelper.getLocalCoverFile(getApplication(), item.rjid, item.localFilePath)
                if (localCover != null && localCover.exists()) {
                    item.copy(coverUrl = localCover.absolutePath)
                } else {
                    item
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                                .crossfade(false)
                                .build()
                            Coil.imageLoader(getApplication()).enqueue(req)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun syncStorageWithDatabase() {
        StorageSyncManager.syncStorageWithDatabase(getApplication())
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun isFilePresent(item: HistoryEntity): Boolean {
        val path = item.localFilePath
        if (!path.isNullOrBlank()) {
            val direct = File(path)
            if (direct.exists() && direct.length() > 0) return true
        }
        val defaultDir = DownloadService.getDefaultDownloadDirectory()
        return AudioStorageHelper.findExistingAudioFile(defaultDir, item.rjid) != null
    }

    fun deleteHistory(item: HistoryEntity, deleteFile: Boolean = false) {
        viewModelScope.launch {
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
