package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import com.saymaven.downloader.japaneseasmr.service.UsbDacManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    val dacState = UsbDacManager.dacState

    init {
        viewModelScope.launch {
            preferencesManager.exclusiveUsbDacFlow.collect { enabled ->
                UsbDacManager.init(getApplication(), enabled)
            }
        }
    }

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

    // Player Settings StateFlows
    val audioFocus = preferencesManager.audioFocusFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val exclusiveUsbDac = preferencesManager.exclusiveUsbDacFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val pauseOnUnplug = preferencesManager.pauseOnUnplugFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val skipSilence = preferencesManager.skipSilenceFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val keepScreenOn = preferencesManager.keepScreenOnFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val autoResume = preferencesManager.autoResumeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val defaultSpeed = preferencesManager.defaultSpeedFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        1.0f
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

    fun setAudioFocus(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAudioFocus(enabled)
        }
    }

    fun setExclusiveUsbDac(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setExclusiveUsbDac(enabled)
            UsbDacManager.setExclusiveSetting(getApplication(), enabled)
        }
    }

    fun setPauseOnUnplug(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPauseOnUnplug(enabled)
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSkipSilence(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setKeepScreenOn(enabled)
        }
    }

    fun setAutoResume(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoResume(enabled)
        }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesManager.setDefaultSpeed(speed)
        }
    }
}
