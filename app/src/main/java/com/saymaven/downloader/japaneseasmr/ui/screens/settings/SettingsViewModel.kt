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
import com.saymaven.downloader.japaneseasmr.service.ImportPreviewData
import com.saymaven.downloader.japaneseasmr.service.StorageSyncManager
import com.saymaven.downloader.japaneseasmr.service.TranslationService
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

    val importedDirs = preferencesManager.importedDirsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet()
    )

    private val _importPreview = MutableStateFlow<ImportPreviewData?>(null)
    val importPreview = _importPreview.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    fun prepareImport(uriOrPath: String) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val preview = StorageSyncManager.previewImportDirectory(getApplication(), uriOrPath)
                _importPreview.value = preview
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun confirmImport(dirUriOrPath: String) {
        viewModelScope.launch {
            preferencesManager.addImportedDir(dirUriOrPath)
            _importPreview.value = null
            StorageSyncManager.syncStorageWithDatabase(getApplication(), force = true)
        }
    }

    fun removeImportedDir(dirUriOrPath: String) {
        viewModelScope.launch {
            preferencesManager.removeImportedDir(dirUriOrPath)
            StorageSyncManager.syncStorageWithDatabase(getApplication(), force = true)
        }
    }

    fun dismissImportPreview() {
        _importPreview.value = null
    }

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

    val mergeDownloadedAudio = preferencesManager.mergeDownloadedAudioFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val translationLanguage = preferencesManager.translationLanguageFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "id"
    )

    val autoTranslateLyrics = preferencesManager.autoTranslateLyricsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        preferencesManager.getInitialAutoTranslateLyrics()
    )

    val autoTranslateDetail = preferencesManager.autoTranslateDetailFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        preferencesManager.getInitialAutoTranslateDetail()
    )

    val autoTranslateLists = preferencesManager.autoTranslateListsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        preferencesManager.getInitialAutoTranslateLists()
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

    fun setMergeDownloadedAudio(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setMergeDownloadedAudio(enabled)
        }
    }

    fun setTranslationLanguage(lang: String) {
        viewModelScope.launch {
            preferencesManager.setTranslationLanguage(lang)
        }
    }

    fun setAutoTranslateLyrics(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoTranslateLyrics(enabled)
        }
    }

    fun setAutoTranslateDetail(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoTranslateDetail(enabled)
        }
    }

    fun setAutoTranslateLists(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoTranslateLists(enabled)
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

    // ================= KikoFlu UI Settings =================
    val pageSize = preferencesManager.pageSizeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        preferencesManager.getInitialPageSize()
    )
    fun setPageSize(size: Int) {
        viewModelScope.launch { preferencesManager.setPageSize(size) }
    }

    val playerButtonOrder = preferencesManager.playerButtonOrderFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "repeat,speed,sleep_timer,bookmark,volume,seek_backward,seek_forward,detail,subtitle,specs"
    )
    fun setPlayerButtonOrder(order: String) {
        viewModelScope.launch { preferencesManager.setPlayerButtonOrder(order) }
    }

    // Work Card Display Toggles
    val cardShowRating = preferencesManager.cardShowRatingFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val cardShowPrice = preferencesManager.cardShowPriceFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val cardShowDuration = preferencesManager.cardShowDurationFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val cardShowSales = preferencesManager.cardShowSalesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val cardShowReleaseDate = preferencesManager.cardShowReleaseDateFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val cardShowCircle = preferencesManager.cardShowCircleFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val cardShowSubtitleTag = preferencesManager.cardShowSubtitleTagFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val cardShowAgeRating = preferencesManager.cardShowAgeRatingFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setCardShowRating(show: Boolean) = viewModelScope.launch { preferencesManager.setCardShowRating(show) }
    fun setCardShowPrice(show: Boolean) = viewModelScope.launch { preferencesManager.setCardShowPrice(show) }
    fun setCardShowDuration(show: Boolean) = viewModelScope.launch { preferencesManager.setCardShowDuration(show) }
    fun setCardShowSales(show: Boolean) = viewModelScope.launch { preferencesManager.setCardShowSales(show) }
    fun setCardShowReleaseDate(show: Boolean) = viewModelScope.launch { preferencesManager.setCardShowReleaseDate(show) }
    fun setCardShowCircle(show: Boolean) = viewModelScope.launch { preferencesManager.setCardShowCircle(show) }
    fun setCardShowSubtitleTag(show: Boolean) = viewModelScope.launch { preferencesManager.setCardShowSubtitleTag(show) }
    fun setCardShowAgeRating(show: Boolean) = viewModelScope.launch { preferencesManager.setCardShowAgeRating(show) }

    // Work Detail Display Toggles
    val detailShowRating = preferencesManager.detailShowRatingFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val detailShowPrice = preferencesManager.detailShowPriceFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val detailShowDuration = preferencesManager.detailShowDurationFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val detailShowSales = preferencesManager.detailShowSalesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val detailShowExternalLinks = preferencesManager.detailShowExternalLinksFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val detailShowReleaseDate = preferencesManager.detailShowReleaseDateFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val detailShowTranslateBtn = preferencesManager.detailShowTranslateButtonFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val detailShowSubtitle = preferencesManager.detailShowSubtitleTagFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val detailShowAgeRating = preferencesManager.detailShowAgeRatingFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val detailShowRecommendations = preferencesManager.detailShowRecommendationsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDetailShowRating(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowRating(show) }
    fun setDetailShowPrice(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowPrice(show) }
    fun setDetailShowDuration(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowDuration(show) }
    fun setDetailShowSales(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowSales(show) }
    fun setDetailShowExternalLinks(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowExternalLinks(show) }
    fun setDetailShowReleaseDate(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowReleaseDate(show) }
    fun setDetailShowTranslateBtn(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowTranslateButton(show) }
    fun setDetailShowSubtitle(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowSubtitleTag(show) }
    fun setDetailShowAgeRating(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowAgeRating(show) }
    fun setDetailShowRecommendations(show: Boolean) = viewModelScope.launch { preferencesManager.setDetailShowRecommendations(show) }

    // Privacy Mode (Anti-Malu)
    val privacyEnabled = preferencesManager.privacyEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.getInitialPrivacyEnabled())
    val privacyBlurNotif = preferencesManager.privacyBlurNotifFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.getInitialPrivacyBlurNotif())
    val privacyBlurInApp = preferencesManager.privacyBlurInAppFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.getInitialPrivacyBlurInApp())
    val privacyMaskTitle = preferencesManager.privacyMaskTitleFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.getInitialPrivacyMaskTitle())
    val privacyCustomMask = preferencesManager.privacyCustomMaskFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.getInitialPrivacyCustomMask())

    fun setPrivacyEnabled(enabled: Boolean) = viewModelScope.launch { preferencesManager.setPrivacyEnabled(enabled) }
    fun setPrivacyBlurNotif(blur: Boolean) = viewModelScope.launch { preferencesManager.setPrivacyBlurNotif(blur) }
    fun setPrivacyBlurInApp(blur: Boolean) = viewModelScope.launch { preferencesManager.setPrivacyBlurInApp(blur) }
    fun setPrivacyMaskTitle(mask: Boolean) = viewModelScope.launch { preferencesManager.setPrivacyMaskTitle(mask) }
    fun setPrivacyCustomMask(text: String) = viewModelScope.launch { preferencesManager.setPrivacyCustomMask(text) }

    // Audio Format Priority & Preload
    val audioFormatPriority = preferencesManager.audioFormatPriorityFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "mp3,flac,wav,opus,m4a,aac")
    val audioPreloadSeconds = preferencesManager.audioPreloadSecondsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)
    val audioGainDb = preferencesManager.audioGainDbFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.getInitialAudioGainDb())

    fun setAudioFormatPriority(priority: String) = viewModelScope.launch { preferencesManager.setAudioFormatPriority(priority) }
    fun setAudioPreloadSeconds(seconds: Int) = viewModelScope.launch { preferencesManager.setAudioPreloadSeconds(seconds) }
    fun setAudioGainDb(gainDb: Float) = viewModelScope.launch { preferencesManager.setAudioGainDb(gainDb) }

    // Blocked Items (Blacklist)
    val blockedTags = preferencesManager.blockedTagsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val blockedCvs = preferencesManager.blockedCvsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val blockedCircles = preferencesManager.blockedCirclesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun addBlockedTag(tag: String) = viewModelScope.launch { preferencesManager.addBlockedTag(tag) }
    fun removeBlockedTag(tag: String) = viewModelScope.launch { preferencesManager.removeBlockedTag(tag) }
    fun addBlockedCv(cv: String) = viewModelScope.launch { preferencesManager.addBlockedCv(cv) }
    fun removeBlockedCv(cv: String) = viewModelScope.launch { preferencesManager.removeBlockedCv(cv) }
    fun addBlockedCircle(circle: String) = viewModelScope.launch { preferencesManager.addBlockedCircle(circle) }
    fun removeBlockedCircle(circle: String) = viewModelScope.launch { preferencesManager.removeBlockedCircle(circle) }

    // Translation Provider & Lyrics
    val translationProvider = preferencesManager.translationProviderFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "google"
    )

    fun setTranslationProvider(provider: String) = viewModelScope.launch {
        preferencesManager.setTranslationProvider(provider)
        TranslationService.clearMemoryCache()
    }

    fun clearAllTranslationCache(onComplete: (() -> Unit)? = null) = viewModelScope.launch {
        TranslationService.clearAllCache(getApplication())
        onComplete?.invoke()
    }

    val autoSaveTranslatedLyrics = preferencesManager.autoSaveTranslatedLyricsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    fun setAutoSaveTranslatedLyrics(autoSave: Boolean) = viewModelScope.launch {
        preferencesManager.setAutoSaveTranslatedLyrics(autoSave)
    }

    // LLM Config
    val llmApiUrl = preferencesManager.llmApiUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val llmApiKey = preferencesManager.llmApiKeyFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val llmModel = preferencesManager.llmModelFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "claude-haiku-5-5")
    val llmPrompt = preferencesManager.llmPromptFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.DEFAULT_LLM_PROMPT)
    val llmConcurrency = preferencesManager.llmConcurrencyFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    fun setLlmConfig(url: String, key: String, model: String, prompt: String, concurrency: Int = 2) = viewModelScope.launch {
        preferencesManager.setLlmConfig(url, key, model, prompt, concurrency)
    }

    fun setLlmConcurrency(concurrency: Int) = viewModelScope.launch {
        preferencesManager.setLlmConcurrency(concurrency)
    }

    // Floating Lyrics
    val floatingLyricsEnabled = preferencesManager.floatingLyricsEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val floatingLyricsFontSize = preferencesManager.floatingLyricsFontSizeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 14f)
    val floatingLyricsOpacity = preferencesManager.floatingLyricsOpacityFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.90f)
    val floatingLyricsCornerRadius = preferencesManager.floatingLyricsCornerRadiusFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16f)
    val floatingLyricsShowTranslation = preferencesManager.floatingLyricsShowTranslationFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val floatingLyricsDisplayMode = preferencesManager.floatingLyricsDisplayModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "BOTH")
    val floatingLyricsLocked = preferencesManager.floatingLyricsLockedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setFloatingLyricsEnabled(enabled: Boolean) = viewModelScope.launch { preferencesManager.setFloatingLyricsEnabled(enabled) }
    fun setFloatingLyricsFontSize(size: Float) = viewModelScope.launch { preferencesManager.setFloatingLyricsFontSize(size) }
    fun setFloatingLyricsOpacity(opacity: Float) = viewModelScope.launch { preferencesManager.setFloatingLyricsOpacity(opacity) }
    fun setFloatingLyricsCornerRadius(radius: Float) = viewModelScope.launch { preferencesManager.setFloatingLyricsCornerRadius(radius) }
    fun setFloatingLyricsShowTranslation(show: Boolean) = viewModelScope.launch { preferencesManager.setFloatingLyricsShowTranslation(show) }
    fun setFloatingLyricsDisplayMode(mode: String) = viewModelScope.launch { preferencesManager.setFloatingLyricsDisplayMode(mode) }
    fun setFloatingLyricsLocked(locked: Boolean) = viewModelScope.launch { preferencesManager.setFloatingLyricsLocked(locked) }
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
