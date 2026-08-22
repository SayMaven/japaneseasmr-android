package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    val themeMode = preferencesManager.themeModeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeMode.SYSTEM
    )

    val dynamicColor = preferencesManager.dynamicColorFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val colorPalette = preferencesManager.colorPaletteFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ColorPalette.DEFAULT
    )

    val downloadDir = preferencesManager.downloadDirFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val parallelConnections = preferencesManager.parallelConnectionsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        16
    )

    val autoClipboard = preferencesManager.autoClipboardFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val useDetailedFilename = preferencesManager.useDetailedFilenameFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDynamicColor(enabled)
        }
    }

    fun setColorPalette(palette: ColorPalette) {
        viewModelScope.launch {
            preferencesManager.setColorPalette(palette)
        }
    }

    fun setDownloadDir(path: String) {
        viewModelScope.launch {
            preferencesManager.setDownloadDir(path)
        }
    }

    fun setParallelConnections(connections: Int) {
        viewModelScope.launch {
            preferencesManager.setParallelConnections(connections)
        }
    }

    fun setAutoClipboard(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoClipboard(enabled)
        }
    }

    fun setUseDetailedFilename(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setUseDetailedFilename(enabled)
        }
    }
}
