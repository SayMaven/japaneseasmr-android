package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    val themeMode = prefs.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    val dynamicColor = prefs.dynamicColorFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val downloadDir = prefs.downloadDirFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val parallelConnections = prefs.parallelConnectionsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)
    val autoClipboard = prefs.autoClipboardFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val useDetailedFilename = prefs.useDetailedFilenameFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDynamicColor(enabled)
        }
    }

    fun setDownloadDir(path: String) {
        viewModelScope.launch {
            prefs.setDownloadDir(path)
        }
    }

    fun setParallelConnections(connections: Int) {
        viewModelScope.launch {
            prefs.setParallelConnections(connections)
        }
    }

    fun setAutoClipboard(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoClipboard(enabled)
        }
    }

    fun setUseDetailedFilename(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setUseDetailedFilename(enabled)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            app.imageLoader.diskCache?.clear()
            app.imageLoader.memoryCache?.clear()
            val tempDir = File(app.cacheDir, "temp_downloads")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
        }
    }
}
