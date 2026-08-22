package com.saymaven.downloader.japaneseasmr.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.repository.AsmrRepository
import com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repository: AsmrRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    init {
        val db = AsmrDatabase.getDatabase(application)
        repository = AsmrRepository(db.historyDao())
    }

    val historyList = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            repository.allHistory
        } else {
            repository.searchHistory(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            repository.deleteHistory(item)
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
                    repository.deleteHistory(item)
                } else if (resolved.absolutePath != item.localFilePath) {
                    repository.saveHistory(item.copy(localFilePath = resolved.absolutePath))
                }
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }
}
