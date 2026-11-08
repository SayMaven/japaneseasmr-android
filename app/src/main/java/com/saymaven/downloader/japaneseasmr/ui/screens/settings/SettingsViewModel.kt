package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.BuildConfig
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import com.saymaven.downloader.japaneseasmr.data.model.UpdateInfo
import com.saymaven.downloader.japaneseasmr.service.UpdateManager
import com.saymaven.downloader.japaneseasmr.service.UsbDacManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    val dacState = UsbDacManager.dacState

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            preferencesManager.exclusiveUsbDacFlow.collect { enabled ->
                UsbDacManager.init(getApplication(), enabled)
            }
        }
    }

    val themeMode = preferencesManager.themeModeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        preferencesManager.getInitialThemeMode()
    )

    val dynamicColor = preferencesManager.dynamicColorFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        preferencesManager.getInitialDynamicColor()
    )

    val colorPalette = preferencesManager.colorPaletteFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        preferencesManager.getInitialColorPalette()
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

    val createCueSheet = preferencesManager.createCueSheetFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val translationLanguage = preferencesManager.translationLanguageFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "id"
    )

    val appLanguage = preferencesManager.appLanguageFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        preferencesManager.getInitialAppLanguage()
    )

    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            preferencesManager.setAppLanguage(lang)
        }
    }

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

    fun setCreateCueSheet(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCreateCueSheet(enabled)
        }
    }

    fun setTranslationLanguage(lang: String) {
        viewModelScope.launch {
            preferencesManager.setTranslationLanguage(lang)
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

    // ================= Auto Updater State & Actions =================
    private val _updateUiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateUiState = _updateUiState.asStateFlow()

    private val _targetDebugChannel = MutableStateFlow(BuildConfig.DEBUG)
    val targetDebugChannel = _targetDebugChannel.asStateFlow()

    fun setTargetDebugChannel(enabled: Boolean) {
        _targetDebugChannel.value = enabled
    }

    fun checkForUpdate() {
        if (_updateUiState.value is UpdateUiState.Checking) return
        _updateUiState.value = UpdateUiState.Checking
        viewModelScope.launch {
            val result = UpdateManager.checkForUpdate(targetChannelDebug = _targetDebugChannel.value)
            result.onSuccess { info ->
                if (info != null) {
                    _updateUiState.value = UpdateUiState.UpdateAvailable(info)
                } else {
                    _updateUiState.value = UpdateUiState.NoUpdateAvailable(BuildConfig.VERSION_NAME)
                }
            }.onFailure { err ->
                _updateUiState.value = UpdateUiState.Error(err.message ?: "Gagal memeriksa pembaruan")
            }
        }
    }

    fun startDownload(info: UpdateInfo) {
        _updateUiState.value = UpdateUiState.Downloading(0f, 0L, info.fileSize)
        viewModelScope.launch {
            val result = UpdateManager.downloadUpdate(
                context = getApplication(),
                downloadUrl = info.downloadUrl,
                fileName = info.fileName
            ) { progress, downloaded, total ->
                _updateUiState.value = UpdateUiState.Downloading(progress, downloaded, total)
            }

            result.onSuccess { apkFile ->
                _updateUiState.value = UpdateUiState.ReadyToInstall(apkFile)
            }.onFailure { err ->
                _updateUiState.value = UpdateUiState.Error(err.message ?: "Gagal mengunduh berkas pembaruan")
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!UpdateManager.canRequestPackageInstalls(context)) {
            UpdateManager.openInstallPermissionSettings(context)
            return
        }
        val res = UpdateManager.installApk(context, apkFile)
        if (res.isFailure) {
            _updateUiState.value = UpdateUiState.Error(res.exceptionOrNull()?.message ?: "Gagal memulai pemasangan APK")
        }
    }

    fun dismissUpdateDialog() {
        _updateUiState.value = UpdateUiState.Idle
    }
}

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class NoUpdateAvailable(val currentVersion: String) : UpdateUiState
    data class UpdateAvailable(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateUiState
    data class ReadyToInstall(val apkFile: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}
