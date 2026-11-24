package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.net.URL
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.Coil
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.UserPlaylistEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.PlaylistItemEntity
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import com.saymaven.downloader.japaneseasmr.data.model.SourceType
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneApiService
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneFileNode
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneTrack
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import com.saymaven.downloader.japaneseasmr.data.remote.TrackDiscoveryService
import com.saymaven.downloader.japaneseasmr.service.lyrics.LyricLine
import com.saymaven.downloader.japaneseasmr.service.lyrics.LyricsParser
import com.saymaven.downloader.japaneseasmr.service.lyrics.SubtitleSource
import com.saymaven.downloader.japaneseasmr.ui.screens.player.delegates.PlayerLyricsDelegate
import com.saymaven.downloader.japaneseasmr.ui.screens.player.delegates.PlayerPlaylistDelegate
import com.saymaven.downloader.japaneseasmr.ui.screens.player.delegates.PlayerTranslationDelegate
import com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper
import com.saymaven.downloader.japaneseasmr.service.ChapterDiscoveryService
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.service.PlaybackService
import com.saymaven.downloader.japaneseasmr.service.StorageSyncManager
import com.saymaven.downloader.japaneseasmr.service.TranslationService
import com.saymaven.downloader.japaneseasmr.service.UsbDacManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.saymaven.downloader.japaneseasmr.util.AgeRatingHelper
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class PlaylistSortType {
    CUSTOM,
    DATE,
    TITLE,
    DURATION,
    RJID,
    ALL_AGES
}

data class PlaylistSortState(
    val type: PlaylistSortType = PlaylistSortType.CUSTOM,
    val isAscending: Boolean = false
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val sp = application.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
    private val prefs = PreferencesManager(application)
    private val historyDao = AsmrDatabase.getDatabase(application).historyDao()
    private val playlistDao = AsmrDatabase.getDatabase(application).playlistDao()
    private val CURRENT_TRANSLATION_ENGINE_VERSION = 2

    // Fast downloadDir StateFlow initialized from SP cache (instant 0ms)
    private val _downloadDir = MutableStateFlow<String?>(sp.getString("cached_download_dir", null))

    private fun loadFastCachedPlaylist(): List<HistoryEntity> {
        return try {
            val file = File(getApplication<Application>().filesDir, "fast_playlist.json")
            if (file.exists() && file.length() > 0L) {
                val jsonStr = file.readText()
                val array = JSONArray(jsonStr)
                val list = ArrayList<HistoryEntity>(array.length())
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        HistoryEntity(
                            rjid = obj.getString("rjid"),
                            title = obj.getString("title"),
                            cv = obj.optString("cv", "-"),
                            circle = obj.optString("circle", "-"),
                            genre = obj.optString("genre", "-"),
                            ageRating = obj.optString("ageRating", "-"),
                            coverUrl = obj.optString("coverUrl", ""),
                            localFilePath = obj.optString("localFilePath", ""),
                            downloadDate = obj.optString("downloadDate", ""),
                            fileSize = obj.optString("fileSize", ""),
                            chaptersJson = if (obj.has("chaptersJson") && !obj.isNull("chaptersJson")) obj.optString("chaptersJson") else null,
                            translatedTitle = if (obj.has("translatedTitle") && !obj.isNull("translatedTitle")) obj.optString("translatedTitle") else null,
                            translatedChaptersJson = if (obj.has("translatedChaptersJson") && !obj.isNull("translatedChaptersJson")) obj.optString("translatedChaptersJson") else null
                        )
                    )
                }
                list
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveFastCachedPlaylist(items: List<HistoryEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                for (item in items) {
                    val obj = JSONObject()
                    obj.put("rjid", item.rjid)
                    obj.put("title", item.title)
                    obj.put("cv", item.cv)
                    obj.put("circle", item.circle)
                    obj.put("genre", item.genre)
                    obj.put("ageRating", item.ageRating)
                    obj.put("coverUrl", item.coverUrl)
                    obj.put("localFilePath", item.localFilePath)
                    obj.put("downloadDate", item.downloadDate)
                    obj.put("fileSize", item.fileSize)
                    if (item.chaptersJson != null) obj.put("chaptersJson", item.chaptersJson)
                    if (item.translatedTitle != null) obj.put("translatedTitle", item.translatedTitle)
                    if (item.translatedChaptersJson != null) obj.put("translatedChaptersJson", item.translatedChaptersJson)
                    array.put(obj)
                }
                val file = File(getApplication<Application>().filesDir, "fast_playlist.json")
                file.writeText(array.toString())
            } catch (_: Exception) {}
        }
    }

    // Playlist: HANYA berisi audio yang benar-benar ada filenya di HP (0 missing tracks)
    private val _playlist = MutableStateFlow<List<HistoryEntity>>(loadFastCachedPlaylist())
    val playlist = _playlist.asStateFlow()

    // Folder Filter & Organization States
    private val _availableFolders = MutableStateFlow<List<String>>(listOf("Semua", "Utama"))
    val availableFolders = _availableFolders.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String>("Semua")
    val selectedFolder = _selectedFolder.asStateFlow()

    fun selectFolder(folder: String) {
        _selectedFolder.value = folder
    }

    // Playlist Sorting State
    private val _sortState = MutableStateFlow(
        PlaylistSortState(
            type = try {
                PlaylistSortType.valueOf(sp.getString("playlist_sort_type", PlaylistSortType.CUSTOM.name) ?: PlaylistSortType.CUSTOM.name)
            } catch (_: Exception) {
                PlaylistSortType.CUSTOM
            },
            isAscending = sp.getBoolean("playlist_sort_asc", false)
        )
    )
    val sortState = _sortState.asStateFlow()

    fun toggleSort(type: PlaylistSortType) {
        val current = _sortState.value
        if (current.type == type) {
            val newAsc = !current.isAscending
            _sortState.value = current.copy(isAscending = newAsc)
            sp.edit().putBoolean("playlist_sort_asc", newAsc).apply()
        } else {
            val defaultAsc = when (type) {
                PlaylistSortType.TITLE, PlaylistSortType.ALL_AGES -> true
                else -> false
            }
            _sortState.value = PlaylistSortState(type = type, isAscending = defaultAsc)
            sp.edit()
                .putString("playlist_sort_type", type.name)
                .putBoolean("playlist_sort_asc", defaultAsc)
                .apply()
        }
    }

    fun setSort(type: PlaylistSortType, isAscending: Boolean) {
        _sortState.value = PlaylistSortState(type = type, isAscending = isAscending)
        sp.edit()
            .putString("playlist_sort_type", type.name)
            .putBoolean("playlist_sort_asc", isAscending)
            .apply()
    }

    private fun parseFileSizeToBytes(sizeStr: String?): Long {
        if (sizeStr.isNullOrBlank()) return 0L
        return try {
            val clean = sizeStr.trim().replace(",", ".").uppercase(Locale.ROOT)
            val num = clean.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
            when {
                clean.endsWith("GB") -> (num * 1024 * 1024 * 1024).toLong()
                clean.endsWith("MB") -> (num * 1024 * 1024).toLong()
                clean.endsWith("KB") -> (num * 1024).toLong()
                else -> num.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    val folderItemsMap: StateFlow<Map<String, List<HistoryEntity>>> = combine(_playlist, _downloadDir, prefs.importedDirsFlow) { list, rawDirStr, rawImportedDirs ->
        val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), rawDirStr) ?: rawDirStr
        val downloadDir = if (!resolvedPath.isNullOrBlank()) File(resolvedPath) else DownloadService.getDefaultDownloadDirectory()
        val importedFiles = rawImportedDirs.mapNotNull { uriStr ->
            val phys = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), uriStr) ?: uriStr
            if (phys.isNotBlank()) File(phys) else null
        }
        list.groupBy { item ->
            AudioStorageHelper.getRelativeFolderName(downloadDir, File(item.localFilePath), importedFiles)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val filteredPlaylist = combine(_playlist, _selectedFolder, _downloadDir, prefs.importedDirsFlow, _sortState) { list, folder, rawDirStr, rawImportedDirs, sort ->
        val folderFiltered = if (folder == "Semua") {
            list
        } else {
            val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), rawDirStr) ?: rawDirStr
            val downloadDir = if (!resolvedPath.isNullOrBlank()) File(resolvedPath) else DownloadService.getDefaultDownloadDirectory()
            val importedFiles = rawImportedDirs.mapNotNull { uriStr ->
                val phys = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), uriStr) ?: uriStr
                if (phys.isNotBlank()) File(phys) else null
            }
            list.filter { item ->
                val itemFolder = AudioStorageHelper.getRelativeFolderName(downloadDir, File(item.localFilePath), importedFiles)
                itemFolder.equals(folder, ignoreCase = true)
            }
        }

        when (sort.type) {
            PlaylistSortType.CUSTOM -> folderFiltered
            PlaylistSortType.DATE -> {
                if (sort.isAscending) {
                    folderFiltered.sortedBy { AudioStorageHelper.parseDateToTimestamp(it.downloadDate, it.localFilePath) }
                } else {
                    folderFiltered.sortedByDescending { AudioStorageHelper.parseDateToTimestamp(it.downloadDate, it.localFilePath) }
                }
            }
            PlaylistSortType.TITLE -> {
                if (sort.isAscending) {
                    folderFiltered.sortedBy { it.title.lowercase(Locale.ROOT) }
                } else {
                    folderFiltered.sortedByDescending { it.title.lowercase(Locale.ROOT) }
                }
            }
            PlaylistSortType.DURATION -> {
                if (sort.isAscending) {
                    folderFiltered.sortedBy { parseFileSizeToBytes(it.fileSize) }
                } else {
                    folderFiltered.sortedByDescending { parseFileSizeToBytes(it.fileSize) }
                }
            }
            PlaylistSortType.RJID -> {
                if (sort.isAscending) {
                    folderFiltered.sortedBy { it.rjid.filter { c -> c.isDigit() }.toLongOrNull() ?: 0L }
                } else {
                    folderFiltered.sortedByDescending { it.rjid.filter { c -> c.isDigit() }.toLongOrNull() ?: 0L }
                }
            }
            PlaylistSortType.ALL_AGES -> {
                val safeWorks = folderFiltered.filter { item ->
                    val normalized = AgeRatingHelper.normalize(item.ageRating)
                    normalized == AgeRatingHelper.RATING_SAFE
                }
                if (sort.isAscending) {
                    safeWorks.sortedByDescending { AudioStorageHelper.parseDateToTimestamp(it.downloadDate, it.localFilePath) }
                } else {
                    safeWorks.sortedBy { it.title.lowercase(Locale.ROOT) }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _playlist.value)

    val dacState = UsbDacManager.dacState
    val hardwareVolume = UsbDacManager.hardwareVolume

    fun setHardwareVolume(percent: Float) {
        UsbDacManager.setHardwareVolume(percent)
    }

    val keepScreenOn = prefs.keepScreenOnFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    private var controllerFuture: ListenableFuture<MediaController>? = null
    val player: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    // Synchronous immediate 0ms restoration flags
    private val isAutoResumeActive: Boolean = sp.getBoolean("setting_auto_resume", true)

    // Playback States (Synchronously restored from cache on launch if auto-resume is ON)
    private val _currentRjId = MutableStateFlow<String?>(
        if (isAutoResumeActive) {
            val cached = sp.getString("cached_rjid", null)
            if (cached != null && (cached.startsWith("/") || cached.contains("/"))) null else cached
        } else null
    )
    val currentRjId = _currentRjId.asStateFlow()

    enum class PlaybackMode { OFFLINE_SINGLE, OFFLINE_ALBUM, ONLINE_STREAM }

    private val _playbackMode = MutableStateFlow(PlaybackMode.OFFLINE_SINGLE)
    val playbackMode = _playbackMode.asStateFlow()

    private val _isOnlineStreaming = MutableStateFlow(false)
    val isOnlineStreaming = _isOnlineStreaming.asStateFlow()

    val currentDisplayBadge = combine(_currentRjId, _playlist, _isOnlineStreaming) { rj, list, isStream ->
        if (rj.isNullOrBlank()) return@combine ""
        if (isStream) return@combine "STREAM | $rj"
        val entity = list.firstOrNull { it.rjid == rj }
        if (entity == null) {
            when {
                rj.startsWith("LOCAL_") -> "IMPORT"
                rj.contains('/') || rj.startsWith("http") -> "IMPORT"
                else -> rj
            }
        } else {
            val isLocal = entity.sourceType != SourceType.DL_SITE.name || entity.rjid.startsWith("LOCAL_")
            when {
                isLocal && !entity.linkedRjId.isNullOrBlank() -> "IMPORT • ${entity.linkedRjId}"
                isLocal -> "IMPORT"
                else -> entity.rjid
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _currentTitle = MutableStateFlow(if (isAutoResumeActive) sp.getString("cached_title", "Belum ada lagu yang diputar") ?: "Belum ada lagu yang diputar" else "Belum ada lagu yang diputar")
    val currentTitle = _currentTitle.asStateFlow()

    private val _currentStreamTrackTitle = MutableStateFlow<String?>(null)
    val currentStreamTrackTitle = _currentStreamTrackTitle.asStateFlow()

    // Room Playlists State
    val userPlaylists = playlistDao.getAllPlaylists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSavedRjids = playlistDao.getAllSavedRjids().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    val homeViewMode = prefs.homeViewModeFlow.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        prefs.getInitialHomeViewMode()
    )

    fun setHomeViewMode(mode: String) {
        viewModelScope.launch {
            prefs.setHomeViewMode(mode)
        }
    }

    // Privacy Mode (Anti-Malu) Flows
    val privacyEnabled = prefs.privacyEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, prefs.getInitialPrivacyEnabled())
    val privacyBlurNotif = prefs.privacyBlurNotifFlow.stateIn(viewModelScope, SharingStarted.Lazily, prefs.getInitialPrivacyBlurNotif())
    val privacyBlurInApp = prefs.privacyBlurInAppFlow.stateIn(viewModelScope, SharingStarted.Lazily, prefs.getInitialPrivacyBlurInApp())
    val privacyMaskTitle = prefs.privacyMaskTitleFlow.stateIn(viewModelScope, SharingStarted.Lazily, prefs.getInitialPrivacyMaskTitle())
    val privacyCustomMask = prefs.privacyCustomMaskFlow.stateIn(viewModelScope, SharingStarted.Lazily, prefs.getInitialPrivacyCustomMask())

    // Audio Gain Flow
    val audioGainDb = prefs.audioGainDbFlow.stateIn(viewModelScope, SharingStarted.Lazily, prefs.getInitialAudioGainDb())

    // Lyrics & Floating Lyrics Delegates
    private val lyricsDelegate by lazy {
        PlayerLyricsDelegate(
            appContext = getApplication(),
            scope = viewModelScope,
            prefs = prefs,
            historyDao = historyDao,
            getPlayer = { player },
            getCurrentRjId = { _currentRjId.value },
            getCurrentTitle = { _currentTitle.value },
            getCurrentChapters = { _currentChapters.value },
            getIsOnlineStreaming = { _isOnlineStreaming.value },
            getIsVirtualAlbum = { isCurrentVirtualAlbum },
            getCurrentStreamTrackTitle = { _currentStreamTrackTitle.value },
            setCurrentStreamTrackTitle = { _currentStreamTrackTitle.value = it },
            getCurrentAlbumHistory = { currentAlbumHistory }
        )
    }

    private val playlistDelegate by lazy {
        PlayerPlaylistDelegate(
            appContext = getApplication(),
            scope = viewModelScope,
            prefs = prefs,
            historyDao = historyDao,
            playlistDao = playlistDao,
            onPlayLocalTrack = { local -> playLocalTrack(local) },
            onPlayOnlineWork = { rjid, workTitle, cv, coverUrl, tracks ->
                playOnlineWork(
                    rjid = rjid,
                    workTitle = workTitle,
                    cv = cv,
                    coverUrl = coverUrl,
                    tracks = tracks
                )
            }
        )
    }

    val currentLyrics: StateFlow<List<LyricLine>> get() = lyricsDelegate.currentLyrics
    val activeLyricIndex: StateFlow<Int> get() = lyricsDelegate.activeLyricIndex
    val availableSubtitles: StateFlow<List<SubtitleSource>> get() = lyricsDelegate.availableSubtitles
    val selectedSubtitleSource: StateFlow<SubtitleSource?> get() = lyricsDelegate.selectedSubtitleSource
    val isLyricsTranslated: StateFlow<Boolean> get() = lyricsDelegate.isLyricsTranslated
    val isTranslatingLyrics: StateFlow<Boolean> get() = lyricsDelegate.isTranslatingLyrics

    val floatingLyricsEnabled = prefs.floatingLyricsEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val floatingLyricsFontSize = prefs.floatingLyricsFontSizeFlow.stateIn(viewModelScope, SharingStarted.Lazily, 14f)
    val floatingLyricsOpacity = prefs.floatingLyricsOpacityFlow.stateIn(viewModelScope, SharingStarted.Lazily, 0.90f)
    val floatingLyricsCornerRadius = prefs.floatingLyricsCornerRadiusFlow.stateIn(viewModelScope, SharingStarted.Lazily, 16f)
    val floatingLyricsShowTranslation = prefs.floatingLyricsShowTranslationFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val floatingLyricsDisplayMode = prefs.floatingLyricsDisplayModeFlow.stateIn(viewModelScope, SharingStarted.Lazily, "TRANSLATION_ONLY")
    val floatingLyricsLocked = prefs.floatingLyricsLockedFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _currentArtist = MutableStateFlow(if (isAutoResumeActive) sp.getString("cached_artist", "-") ?: "-" else "-")
    val currentArtist = _currentArtist.asStateFlow()

    private val _currentCoverUrl = MutableStateFlow<String?>(if (isAutoResumeActive) sp.getString("cached_cover", null) else null)
    val currentCoverUrl = _currentCoverUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(if (isAutoResumeActive) sp.getLong("cached_pos", 0L) else 0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(if (isAutoResumeActive) sp.getLong("cached_duration", 0L) else 0L)
    val duration = _duration.asStateFlow()

    private val _repeatMode = MutableStateFlow(if (isAutoResumeActive) sp.getInt("cached_repeat", Player.REPEAT_MODE_OFF) else Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffleMode = MutableStateFlow(if (isAutoResumeActive) sp.getBoolean("cached_shuffle", false) else false)
    val shuffleMode = _shuffleMode.asStateFlow()

    private val _showRemainingTime = MutableStateFlow(if (isAutoResumeActive) sp.getBoolean("cached_show_remaining", false) else false)
    val showRemainingTime = _showRemainingTime.asStateFlow()

    private val _audioSpecs = MutableStateFlow(if (isAutoResumeActive) sp.getString("cached_specs", "- | - | -") ?: "- | - | -" else "- | - | -")
    val audioSpecs = _audioSpecs.asStateFlow()

    // Sleep Timer state
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs = _sleepTimerRemainingMs.asStateFlow()

    // Interactive Chapters State
    private val _currentChapters = MutableStateFlow<List<ChapterItem>>(emptyList())
    val currentChapters = _currentChapters.asStateFlow()

    private val _isLoadingChapters = MutableStateFlow(false)
    val isLoadingChapters = _isLoadingChapters.asStateFlow()

    // Translation Delegate
    private val translationDelegate by lazy {
        PlayerTranslationDelegate(
            appContext = getApplication(),
            scope = viewModelScope,
            prefs = prefs,
            sp = sp,
            historyDao = historyDao,
            getCurrentRjId = { _currentRjId.value },
            getCurrentTitle = { _currentTitle.value },
            getCurrentChapters = { _currentChapters.value },
            getIsOnlineStreaming = { _isOnlineStreaming.value },
            onClearPlaylistEntityTranslations = {
                val current = _playlist.value
                if (current.isNotEmpty()) {
                    _playlist.value = current.map { it.copy(translatedTitle = null, translatedChaptersJson = null) }
                }
            }
        )
    }

    val isTranslationActive: StateFlow<Boolean> get() = translationDelegate.isTranslationActive
    val isPlaylistTranslationActive: StateFlow<Boolean> get() = translationDelegate.isPlaylistTranslationActive
    val isTranslating: StateFlow<Boolean> get() = translationDelegate.isTranslating
    val translatedTitle: StateFlow<String?> get() = translationDelegate.translatedTitle
    val translatedChapters: StateFlow<List<ChapterItem>> get() = translationDelegate.translatedChapters
    val playlistTranslatedTitles: StateFlow<Map<String, String>> get() = translationDelegate.playlistTranslatedTitles

    val activeChapterIndex = combine(_currentPosition, _currentChapters) { pos, chapters ->
        if (chapters.isEmpty()) -1
        else chapters.indexOfLast { pos >= it.startMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    // Pagination & Work Card Display Preferences
    val pageSize: StateFlow<Int> = prefs.pageSizeFlow.stateIn(viewModelScope, SharingStarted.Lazily, 40)
    val showCardRating: StateFlow<Boolean> = prefs.cardShowRatingFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val showCardPrice: StateFlow<Boolean> = prefs.cardShowPriceFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val showCardDuration: StateFlow<Boolean> = prefs.cardShowDurationFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showCardSales: StateFlow<Boolean> = prefs.cardShowSalesFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val showCardReleaseDate: StateFlow<Boolean> = prefs.cardShowReleaseDateFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showCardCircle: StateFlow<Boolean> = prefs.cardShowCircleFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showCardSubtitle: StateFlow<Boolean> = prefs.cardShowSubtitleTagFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showCardAgeRating: StateFlow<Boolean> = prefs.cardShowAgeRatingFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val playerButtonOrder: StateFlow<String> = prefs.playerButtonOrderFlow.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        PreferencesManager.DEFAULT_PLAYER_BUTTON_ORDER
    )
    val playbackSpeed: StateFlow<Float> = prefs.defaultSpeedFlow.stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            prefs.setDefaultSpeed(speed)
            player?.setPlaybackSpeed(speed)
        }
    }

    val activeChapter = combine(activeChapterIndex, _currentChapters) { idx, chapters ->
        if (idx in chapters.indices) chapters[idx] else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isVirtualAlbum = MutableStateFlow(false)
    val isVirtualAlbum = _isVirtualAlbum.asStateFlow()

    var isCurrentVirtualAlbum: Boolean
        get() = _isVirtualAlbum.value
        set(value) {
            _isVirtualAlbum.value = value
        }
    var currentAlbumHistory: HistoryEntity? = null
        private set

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    init {
        TranslationService.addOnCacheClearedListener {
            clearInMemoryTranslations()
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Instant 0ms Cache-first boot: populate immediately from direct database
            val initialDbList = historyDao.getAllHistoryDirect()
            if (initialDbList.isNotEmpty() && _playlist.value.isEmpty()) {
                val savedOrderStr = sp.getString("custom_playlist_order", null)
                val orderMap = if (!savedOrderStr.isNullOrBlank()) {
                    savedOrderStr.split(",")
                        .mapIndexed { idx, rjid -> rjid.trim() to idx }
                        .toMap()
                } else {
                    emptyMap()
                }
                val initialSorted = if (orderMap.isNotEmpty()) {
                    initialDbList.sortedBy { orderMap[it.rjid] ?: Int.MAX_VALUE }
                } else {
                    initialDbList.sortedByDescending { AudioStorageHelper.parseDateToTimestamp(it.downloadDate, it.localFilePath) }
                }
                _playlist.value = initialSorted
                saveFastCachedPlaylist(initialSorted)
            }
        }

        viewModelScope.launch {
            prefs.downloadDirFlow.distinctUntilChanged().collect { dir ->
                _downloadDir.value = dir
                sp.edit().putString("cached_download_dir", dir).apply()
            }
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                resolveAndPreloadInitialCover()
            }
            initMediaController()
            observeDatabasePlaylist()
            observeSyncTicks()
            restorePlaybackState()
            observeAudioSettings()
            observeTranslationPreference()
            observePrivacyNotificationSync()
            checkAndMigrateTranslationEngine()
            if (!_currentRjId.value.isNullOrBlank()) {
                loadChaptersForCurrentTrack(_currentRjId.value)
            }
        }
    }

    private fun observePrivacyNotificationSync() {
        viewModelScope.launch {
            var isFirst = true
            combine(
                prefs.privacyEnabledFlow,
                prefs.privacyBlurNotifFlow,
                prefs.privacyMaskTitleFlow,
                prefs.privacyCustomMaskFlow
            ) { enabled, blurNotif, maskTitle, customMask ->
                listOf(enabled, blurNotif, maskTitle, customMask)
            }.distinctUntilChanged().collect { values ->
                if (isFirst) {
                    isFirst = false
                    return@collect
                }
                val p = player ?: return@collect
                val curIdx = p.currentMediaItemIndex
                val curItem = p.currentMediaItem ?: return@collect
                val enabled = values[0] as Boolean
                val blurNotif = enabled && (values[1] as Boolean)
                val maskTitle = enabled && (values[2] as Boolean)
                val customMask = values[3] as String

                val coverBytes = loadArtworkBytes(null, _currentCoverUrl.value, shouldBlur = blurNotif)
                val updatedMeta = buildMediaMetadataWithPrivacy(
                    title = if (_isOnlineStreaming.value) (_currentStreamTrackTitle.value ?: _currentTitle.value) else _currentTitle.value,
                    artist = _currentArtist.value,
                    albumTitle = _currentTitle.value,
                    coverUrl = _currentCoverUrl.value,
                    coverBytes = coverBytes,
                    blurNotif = blurNotif,
                    maskTitle = maskTitle,
                    customMask = customMask
                )
                val updatedItem = curItem.buildUpon().setMediaMetadata(updatedMeta).build()
                p.replaceMediaItem(curIdx, updatedItem)
            }
        }
    }

    private fun resolveAndPreloadInitialCover() {
        val rj = _currentRjId.value
        val cover = _currentCoverUrl.value
        if (!rj.isNullOrBlank()) {
            val localCover = AudioStorageHelper.getLocalCoverFile(getApplication(), rj, null)
            if (localCover != null && localCover.exists()) {
                _currentCoverUrl.value = localCover.absolutePath
                preloadCoverArtInMemory(localCover.absolutePath)
                return
            }
        }
        if (!cover.isNullOrBlank()) {
            preloadCoverArtInMemory(cover)
        }
    }

    private fun preloadCoverArtInMemory(url: String?) {
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

    private fun observeDatabasePlaylist() {
        viewModelScope.launch {
            historyDao.getAllHistory().collect { dbList ->
                filterAndSetPlaylist(dbList)
            }
        }
    }

    private fun observeSyncTicks() {
        viewModelScope.launch {
            StorageSyncManager.syncTick.collect { tick ->
                if (tick > 0L) {
                    refreshPlaylist()
                }
            }
        }
    }

    fun refreshPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            val dbList = historyDao.getAllHistoryDirect()
            filterAndSetPlaylist(dbList)
        }
    }

    private fun checkAndMigrateTranslationEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            val cachedVer = prefs.translationEngineVersionFlow.first()
            if (cachedVer < CURRENT_TRANSLATION_ENGINE_VERSION) {
                historyDao.clearAllTranslations()
                prefs.setTranslationEngineVersion(CURRENT_TRANSLATION_ENGINE_VERSION)
            }
        }
    }

    private suspend fun filterAndSetPlaylist(dbList: List<HistoryEntity>) {
        val customDirStr = prefs.downloadDirFlow.first()
        val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), customDirStr) ?: customDirStr
        val downloadDir = if (!resolvedPath.isNullOrBlank()) File(resolvedPath) else DownloadService.getDefaultDownloadDirectory()

        // Pindai subfolder fisik yang ada di direktori unduhan dan folder impor
        val subfolders = AudioStorageHelper.getExistingSubfolders(downloadDir)
        val imported = prefs.importedDirsFlow.first()
        val importedNames = imported.mapNotNull { dirUri ->
            val phys = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), dirUri)
            val decoded = if (!phys.isNullOrBlank()) {
                File(phys).name
            } else {
                val rawDecoded = Uri.decode(dirUri)
                rawDecoded.substringAfterLast(':').substringAfterLast('/').trim()
            }
            decoded.takeIf { it.isNotBlank() }
        }
        val allFolders = (listOf("Semua", "Utama") + subfolders + importedNames).distinct()
        _availableFolders.value = allFolders

        // Filter HANYA audio yang file fisiknya benar-benar ada di HP (termasuk di subfolder dan folder multi-track)
        val validItems = withContext(Dispatchers.IO) {
            dbList.mapNotNull { item ->
                val validFile = AudioStorageHelper.resolveValidAudioFile(downloadDir, item.localFilePath, item.rjid)

                if (validFile != null && validFile.exists() && (validFile.isDirectory || validFile.length() > 0L)) {
                    val customCover = if (!item.coverUrl.isNullOrBlank() && File(item.coverUrl).exists()) item.coverUrl else null
                    val localCover = customCover ?: AudioStorageHelper.getLocalCoverFile(getApplication(), item.rjid, validFile.absolutePath)?.absolutePath
                    val resolvedCover = localCover ?: item.coverUrl
                    item.copy(
                        localFilePath = validFile.absolutePath,
                        coverUrl = resolvedCover
                    )
                } else {
                    null // File hilang / terhapus: DIHAPUS dari daftar putar koleksi
                }
            }
        }

        val savedOrderStr = sp.getString("custom_playlist_order", null)
        val orderMap = if (!savedOrderStr.isNullOrBlank()) {
            savedOrderStr.split(",")
                .mapIndexed { idx, rjid -> rjid.trim() to idx }
                .toMap()
        } else {
            emptyMap()
        }

        val sorted = if (orderMap.isNotEmpty()) {
            validItems.sortedBy { orderMap[it.rjid] ?: Int.MAX_VALUE }
        } else {
            // Urutan default: Berdasarkan waktu ditambahkan (terbaru di atas)
            validItems.sortedByDescending { AudioStorageHelper.parseDateToTimestamp(it.downloadDate, it.localFilePath) }
        }
        _playlist.value = sorted
        saveFastCachedPlaylist(sorted)

        // Preload judul terjemahan yang sudah tersimpan di database Room
        translationDelegate.preloadFromHistory(sorted)

        // Otomatis lengkapi metadata karya lokal yang belum memiliki tag/usia
        autoEnrichIncompleteMetadata(sorted)
    }

    private val attemptedEnrichmentRj = ConcurrentHashMap.newKeySet<String>()
    private var enrichmentJob: Job? = null

    private fun autoEnrichIncompleteMetadata(items: List<HistoryEntity>) {
        val incompleteItems = items.filter { item ->
            item.genre == "-" || item.genre.isBlank() || item.ageRating == "-" || item.cv == "-"
        }
        if (incompleteItems.isEmpty()) return

        enrichmentJob?.cancel()
        enrichmentJob = viewModelScope.launch(Dispatchers.IO) {
            for (item in incompleteItems) {
                if (!isActive) break
                val rjCandidate = item.linkedRjId?.takeIf { it.isNotBlank() }
                    ?: StorageSyncManager.RJ_REGEX.find(item.rjid)?.value
                    ?: StorageSyncManager.RJ_REGEX.find(item.title)?.value
                    ?: StorageSyncManager.RJ_REGEX.find(File(item.localFilePath).name)?.value
                    ?: AudioStorageHelper.findRjCodeInPath(File(item.localFilePath))

                if (rjCandidate == null) continue
                val cleanRj = rjCandidate.uppercase().trim()
                if (attemptedEnrichmentRj.contains(cleanRj)) continue
                attemptedEnrichmentRj.add(cleanRj)

                try {
                    val meta = DLsiteScraper.fetchMetadata(cleanRj)
                    if (meta.title.isNotBlank() || meta.cv.isNotBlank() || (meta.genre.isNotBlank() && meta.genre != "-")) {
                        var resolvedCover = item.coverUrl
                        if (!meta.coverUrl.isNullOrBlank() && (resolvedCover.isBlank() || resolvedCover == "-")) {
                            try {
                                val coversDir = File(getApplication<Application>().cacheDir, "covers").apply { if (!exists()) mkdirs() }
                                val targetCoverFile = File(coversDir, "$cleanRj.jpg")
                                val req = Request.Builder()
                                    .url(meta.coverUrl)
                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                    .header("Referer", "https://japaneseasmr.com")
                                    .build()
                                val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
                                val resp = client.newCall(req).execute()
                                if (resp.isSuccessful) {
                                    resp.body?.byteStream()?.use { input ->
                                        FileOutputStream(targetCoverFile).use { output -> input.copyTo(output) }
                                    }
                                    if (targetCoverFile.exists() && targetCoverFile.length() > 0) {
                                        resolvedCover = targetCoverFile.absolutePath
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        val updated = item.copy(
                            title = if (meta.title.isNotBlank()) meta.title else item.title,
                            cv = if (meta.cv.isNotBlank() && meta.cv != "-") meta.cv else item.cv,
                            circle = if (meta.circle.isNotBlank() && meta.circle != "-") meta.circle else item.circle,
                            genre = if (meta.genre.isNotBlank() && meta.genre != "-") meta.genre else item.genre,
                            ageRating = if (meta.ageRating.isNotBlank() && meta.ageRating != "-") meta.ageRating else item.ageRating,
                            coverUrl = resolvedCover,
                            linkedRjId = cleanRj
                        )
                        historyDao.insertHistory(updated)
                    }
                } catch (_: Exception) {}
                delay(400)
            }
        }
    }

    fun createNewFolder(folderName: String) {
        if (folderName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val customDirStr = prefs.downloadDirFlow.first()
            val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), customDirStr) ?: customDirStr
            val downloadDir = if (!resolvedPath.isNullOrBlank()) File(resolvedPath) else DownloadService.getDefaultDownloadDirectory()
            val created = AudioStorageHelper.createSubfolder(downloadDir, folderName)
            if (created != null) {
                val subfolders = AudioStorageHelper.getExistingSubfolders(downloadDir)
                val imported = prefs.importedDirsFlow.first()
                val importedNames = imported.mapNotNull { dirUri ->
                    val phys = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), dirUri)
                    val decoded = if (!phys.isNullOrBlank()) {
                        File(phys).name
                    } else {
                        Uri.decode(dirUri).substringAfterLast(':').substringAfterLast('/').trim()
                    }
                    decoded.takeIf { it.isNotBlank() }
                }
                _availableFolders.value = (listOf("Semua", "Utama") + subfolders + importedNames).distinct()
                _selectedFolder.value = folderName.trim()
                refreshPlaylist()
            }
        }
    }

    fun moveTrackToFolder(track: HistoryEntity, targetFolder: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val customDirStr = prefs.downloadDirFlow.first()
            val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), customDirStr) ?: customDirStr
            val downloadDir = if (!resolvedPath.isNullOrBlank()) File(resolvedPath) else DownloadService.getDefaultDownloadDirectory()
            val sourceFile = File(track.localFilePath)
            val moved = AudioStorageHelper.moveAudioFile(sourceFile, targetFolder, downloadDir)
            if (moved != null && moved.exists()) {
                val isFolderAlbum = track.sourceType == SourceType.LOCAL_FOLDER.name || moved.isDirectory
                if (isFolderAlbum) {
                    val tracks = AudioStorageHelper.getAudioFilesInFolder(moved)
                    val newChapters = AudioStorageHelper.extractVirtualAlbumChapters(tracks)
                    AudioStorageHelper.cacheVirtualChapters(track.rjid, newChapters)
                    historyDao.insertHistory(track.copy(
                        localFilePath = moved.absolutePath,
                        chaptersJson = ChapterItem.toJsonArray(newChapters)
                    ))
                } else {
                    historyDao.insertHistory(track.copy(localFilePath = moved.absolutePath))
                }

                // Jika folder lama terdaftar di importedDirs, perbarui path impor ke lokasi baru
                val currentImported = prefs.importedDirsFlow.first()
                if (currentImported.contains(sourceFile.absolutePath)) {
                    prefs.removeImportedDir(sourceFile.absolutePath)
                    prefs.addImportedDir(moved.absolutePath)
                }

                refreshPlaylist()
                StorageSyncManager.syncStorageWithDatabase(getApplication(), force = true)
            }
        }
    }

    fun deleteTrack(track: HistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(track.localFilePath)
                if (file.exists()) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            } catch (ignored: Exception) {}
            historyDao.deleteById(track.rjid)
            refreshPlaylist()
        }
    }

    private fun observeAudioSettings() {
        viewModelScope.launch {
            prefs.defaultSpeedFlow.distinctUntilChanged().collect { speed ->
                player?.setPlaybackSpeed(speed)
            }
        }
    }

    private fun observeTranslationPreference() {
        translationDelegate.observePreferences()
    }

    // Ultra-Fast In-Memory Reorder (0 disk I/O, 0 IPC calls during drag)
    fun reorderPlaylistInMemory(fromIndex: Int, toIndex: Int) {
        if (_sortState.value.type != PlaylistSortType.CUSTOM) {
            _sortState.value = PlaylistSortState(PlaylistSortType.CUSTOM, false)
            sp.edit().putString("playlist_sort_type", PlaylistSortType.CUSTOM.name).putBoolean("playlist_sort_asc", false).apply()
        }
        val current = _playlist.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _playlist.value = current
        }
    }

    // Persist reorder to disk only once when drag gesture finishes
    fun commitPlaylistReorder() {
        val current = _playlist.value
        if (current.isNotEmpty()) {
            saveFastCachedPlaylist(current)
            val rjidList = current.map { it.rjid }
            viewModelScope.launch(Dispatchers.IO) {
                val str = rjidList.joinToString(",")
                sp.edit().putString("custom_playlist_order", str).commit()
            }
        }
    }

    fun reorderPlaylist(fromIndex: Int, toIndex: Int) {
        reorderPlaylistInMemory(fromIndex, toIndex)
        commitPlaylistReorder()
    }

    fun toggleShowRemainingTime() {
        val next = !_showRemainingTime.value
        _showRemainingTime.value = next
        saveCurrentState()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val p = controllerFuture?.get() ?: return@addListener
                setupPlayerListener(p)
                syncPlayerWithCurrentState(p)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun syncPlayerWithCurrentState(p: Player) {
        if (p.mediaItemCount > 0) {
            _isPlaying.value = p.isPlaying
            if (p.isPlaying || p.currentPosition > 0L) {
                _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
            }
            if (p.duration > 0L) {
                _duration.value = p.duration.coerceAtLeast(0L)
            }
            // Keep user-facing _repeatMode intact; map to ExoPlayer internal repeatMode
            p.repeatMode = if (_repeatMode.value == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            _shuffleMode.value = p.shuffleModeEnabled

            p.currentMediaItem?.let { item ->
                val rawMediaId = item.mediaId
                val cleanMediaId = if (rawMediaId.startsWith("/") || rawMediaId.contains("/")) null else rawMediaId
                val prevRj = _currentRjId.value
                if (!cleanMediaId.isNullOrBlank()) {
                    _currentRjId.value = cleanMediaId
                    if (prevRj != cleanMediaId) {
                        loadChaptersForCurrentTrack(cleanMediaId)
                    }
                }
                item.mediaMetadata.let { meta ->
                    if (!isCurrentVirtualAlbum && _playbackMode.value != PlaybackMode.ONLINE_STREAM) {
                        _currentTitle.value = meta.title?.toString() ?: _currentTitle.value
                    }
                    _currentArtist.value = meta.artist?.toString() ?: _currentArtist.value
                    val cover = meta.artworkUri?.toString() ?: _currentCoverUrl.value
                    _currentCoverUrl.value = cover
                    preloadCoverArtInMemory(cover)
                }
                if (isCurrentVirtualAlbum) {
                    _currentStreamTrackTitle.value = item.mediaMetadata.title?.toString()
                }
            }

            if (p.isPlaying) {
                startProgressTracking()
            }
        } else {
            // Player idle on launch: If Auto-Resume is active and we have a track, prepare it silently in player!
            val autoResume = sp.getBoolean("setting_auto_resume", true)
            val lastRj = _currentRjId.value
            val lastPos = _currentPosition.value
            if (autoResume && !lastRj.isNullOrBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    delay(2000)
                    val history = historyDao.getHistoryById(lastRj)
                    if (history != null) {
                        prepareTrackSilently(history, _playlist.value, lastPos)
                    }
                }
            }
        }
    }

    private fun restorePlaybackState() {
        viewModelScope.launch {
            val autoResume = prefs.autoResumeFlow.first()
            if (!autoResume) {
                _currentRjId.value = null
                _currentTitle.value = "Belum ada lagu yang diputar"
                _currentArtist.value = "-"
                _currentCoverUrl.value = null
                _currentPosition.value = 0L
                _duration.value = 0L
                _showRemainingTime.value = false
                _audioSpecs.value = "- | - | -"
                return@launch
            }

            val lastRj = prefs.lastPlayedRjidFlow.first()
            val lastPos = prefs.lastPositionMsFlow.first()
            val savedRemaining = prefs.showRemainingTimeFlow.first()
            _showRemainingTime.value = savedRemaining

            if (!lastRj.isNullOrBlank()) {
                val history = historyDao.getHistoryById(lastRj)
                if (history != null) {
                    val localCover = withContext(Dispatchers.IO) {
                        AudioStorageHelper.getLocalCoverFile(getApplication(), history.rjid, history.localFilePath)
                    }
                    val finalCover = localCover?.absolutePath ?: history.coverUrl

                    _currentRjId.value = history.rjid
                    val isLocal = history.sourceType != SourceType.DL_SITE.name || history.rjid.startsWith("LOCAL_")
                    val tag = when {
                        isLocal && !history.linkedRjId.isNullOrBlank() -> "IMPORT • ${history.linkedRjId}"
                        isLocal -> "IMPORT"
                        else -> history.rjid
                    }
                    _currentTitle.value = "[$tag] ${history.title}"
                    _currentArtist.value = history.cv
                    _currentCoverUrl.value = finalCover
                    preloadCoverArtInMemory(finalCover)

                    val isFolder = history.sourceType == SourceType.LOCAL_FOLDER.name || File(history.localFilePath).isDirectory
                    if (isFolder) {
                        isCurrentVirtualAlbum = true
                        currentAlbumHistory = history
                        _playbackMode.value = PlaybackMode.OFFLINE_ALBUM
                    } else {
                        isCurrentVirtualAlbum = false
                        currentAlbumHistory = null
                        _playbackMode.value = PlaybackMode.OFFLINE_SINGLE
                    }

                    if (_currentPosition.value == 0L && lastPos > 0L) {
                        _currentPosition.value = lastPos
                    }
                    _repeatMode.value = prefs.repeatModeFlow.first()
                    _shuffleMode.value = prefs.shuffleModeFlow.first()
                    if (translationDelegate.isTranslationActive.value) {
                        loadOrFetchTranslations(history.rjid)
                    }

                    val p = player
                    if (p != null && p.mediaItemCount == 0) {
                        viewModelScope.launch(Dispatchers.IO) {
                            delay(2000)
                            prepareTrackSilently(history.copy(coverUrl = finalCover), _playlist.value, _currentPosition.value)
                        }
                    }
                }
            }
        }
    }

    private fun prepareTrackSilently(history: HistoryEntity, fullList: List<HistoryEntity>, startPositionMs: Long) {
        viewModelScope.launch {
            val p = player ?: return@launch
            _isOnlineStreaming.value = false
            val customDirStr = prefs.downloadDirFlow.first()
            val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()

            val validItemsWithFiles = fullList.mapNotNull { item ->
                val resolved = AudioStorageHelper.resolveValidAudioFile(downloadDir, item.localFilePath, item.rjid)
                if (resolved != null) Pair(item, resolved) else null
            }

            if (validItemsWithFiles.isEmpty()) return@launch

            val targetPair = validItemsWithFiles.firstOrNull { it.first.rjid == history.rjid } ?: validItemsWithFiles.first()

            val isPriv = privacyEnabled.value
            val blurNotif = isPriv && privacyBlurNotif.value
            val maskNotifTitle = isPriv && privacyMaskTitle.value
            val customMask = privacyCustomMask.value
            val coverBytes = loadArtworkBytes(targetPair.second.absolutePath, targetPair.first.coverUrl, shouldBlur = blurNotif)

            if (targetPair.first.sourceType == SourceType.LOCAL_FOLDER.name || targetPair.second.isDirectory) {
                _playbackMode.value = PlaybackMode.OFFLINE_ALBUM
                isCurrentVirtualAlbum = true
                currentAlbumHistory = targetPair.first
                val folder = targetPair.second
                val tracks = AudioStorageHelper.getAudioFilesInFolder(folder)

                val parsedChapters = if (!targetPair.first.chaptersJson.isNullOrBlank()) {
                    ChapterItem.fromJsonArray(targetPair.first.chaptersJson)
                } else emptyList()

                val chapters = if (parsedChapters.isNotEmpty() && parsedChapters.size == tracks.size) {
                    parsedChapters
                } else {
                    var cum = 0L
                    tracks.map { tr ->
                        var dur = 0L
                        val ret = MediaMetadataRetriever()
                        try {
                            ret.setDataSource(tr.absolutePath)
                            dur = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        } catch (e: Exception) {}
                        finally {
                            try { ret.release() } catch (e: Exception) {}
                        }
                        val ch = ChapterItem(cum, cum + dur, tr.nameWithoutExtension, tr.name)
                        cum += dur
                        ch
                    }
                }

                _currentChapters.value = chapters
                val totalDur = chapters.lastOrNull()?.endMs ?: 0L
                _duration.value = totalDur

                val mediaItems = tracks.mapIndexed { idx, tr ->
                    val trackTitle = chapters.getOrNull(idx)?.title ?: tr.nameWithoutExtension
                    val meta = buildMediaMetadataWithPrivacy(
                        title = trackTitle,
                        artist = targetPair.first.cv,
                        albumTitle = targetPair.first.title,
                        coverUrl = targetPair.first.coverUrl,
                        coverBytes = coverBytes,
                        blurNotif = blurNotif,
                        maskTitle = maskNotifTitle,
                        customMask = customMask
                    )
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(tr))
                        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.fromFile(tr)).build())
                        .setMediaId(targetPair.first.rjid)
                        .setMediaMetadata(meta)
                        .build()
                }

                val initialTrackIdx = if (startPositionMs > 0L && chapters.isNotEmpty()) {
                    chapters.indexOfLast { startPositionMs >= it.startMs }.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
                } else 0
                val initialOffset = if (startPositionMs > 0L && chapters.isNotEmpty()) {
                    (startPositionMs - (chapters.getOrNull(initialTrackIdx)?.startMs ?: 0L)).coerceAtLeast(0L)
                } else 0L

                if (tracks.isNotEmpty()) {
                    calculateAudioSpecs(tracks[initialTrackIdx])
                }

                p.setMediaItems(mediaItems, initialTrackIdx, initialOffset)
                p.prepare()
                _currentPosition.value = startPositionMs
            } else {
                _playbackMode.value = PlaybackMode.OFFLINE_SINGLE
                isCurrentVirtualAlbum = false
                currentAlbumHistory = null
                val targetIndex = validItemsWithFiles.indexOf(targetPair).coerceAtLeast(0)
                calculateAudioSpecs(targetPair.second)

                val mediaItems = validItemsWithFiles.map { (item, file) ->
                    val itemBytes = if (item.rjid == targetPair.first.rjid) coverBytes else null
                    val meta = buildMediaMetadataWithPrivacy(
                        title = "[${item.rjid}] ${item.title}",
                        artist = item.cv,
                        albumTitle = item.circle,
                        coverUrl = item.coverUrl,
                        coverBytes = itemBytes,
                        blurNotif = blurNotif,
                        maskTitle = maskNotifTitle,
                        customMask = customMask
                    )

                    MediaItem.Builder()
                        .setUri(Uri.fromFile(file))
                        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.fromFile(file)).build())
                        .setMediaId(item.rjid)
                        .setMediaMetadata(meta)
                        .build()
                }

                p.setMediaItems(mediaItems, targetIndex, startPositionMs)
                p.prepare()
                _currentPosition.value = startPositionMs
            }
        }
    }

    private fun setupPlayerListener(p: Player) {
        p.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updatePlayingState(p)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startProgressTracking()
                } else {
                    stopProgressTracking()
                }
                saveCurrentState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    when (_playbackMode.value) {
                        PlaybackMode.OFFLINE_ALBUM -> {
                            val album = currentAlbumHistory ?: _currentRjId.value?.let { rj -> _playlist.value.firstOrNull { it.rjid == rj } }
                            val curIdx = p.currentMediaItemIndex
                            val curChapter = _currentChapters.value.getOrNull(curIdx)
                            val trackTitle = curChapter?.title ?: mediaItem.mediaMetadata.title?.toString() ?: ""
                            _currentStreamTrackTitle.value = trackTitle
                            val isLocalAlbum = album?.sourceType != SourceType.DL_SITE.name || album?.rjid?.startsWith("LOCAL_") == true
                            val albumTag = when {
                                isLocalAlbum && !album?.linkedRjId.isNullOrBlank() -> "IMPORT • ${album?.linkedRjId}"
                                isLocalAlbum -> "IMPORT"
                                else -> album?.rjid ?: ""
                            }
                            _currentTitle.value = if (album != null) "[$albumTag] ${album.title}" else _currentTitle.value
                            if (album != null && _currentRjId.value != album.rjid) {
                                _currentRjId.value = album.rjid
                            }
                            val baseMs = curChapter?.startMs ?: 0L
                            _currentPosition.value = (baseMs + p.currentPosition).coerceAtLeast(0L)
                            val totalDur = _currentChapters.value.lastOrNull()?.endMs ?: 0L
                            if (totalDur > 0L) {
                                _duration.value = totalDur
                            }
                            saveCurrentState()
                        }
                        PlaybackMode.ONLINE_STREAM -> {
                            val curIdx = p.currentMediaItemIndex
                            val curChapter = _currentChapters.value.getOrNull(curIdx)
                            val trackTitle = curChapter?.title ?: mediaItem.mediaMetadata.title?.toString() ?: ""
                            _currentStreamTrackTitle.value = trackTitle
                            val albumTitle = mediaItem.mediaMetadata.albumTitle?.toString() ?: ""
                            if (albumTitle.isNotBlank()) {
                                val rj = _currentRjId.value ?: ""
                                _currentTitle.value = if (rj.isNotBlank()) "[$rj] $albumTitle" else albumTitle
                            }
                            val baseMs = curChapter?.startMs ?: 0L
                            _currentPosition.value = (baseMs + p.currentPosition).coerceAtLeast(0L)
                            val totalDur = _currentChapters.value.lastOrNull()?.endMs ?: 0L
                            if (totalDur > 0L) {
                                _duration.value = totalDur
                            }
                            saveCurrentState()
                        }
                        PlaybackMode.OFFLINE_SINGLE -> {
                            val rawId = mediaItem.mediaId
                            val cleanId = when {
                                rawId.startsWith("http") || rawId.contains('/') -> _currentRjId.value ?: ""
                                else -> rawId
                            }
                            val prevRj = _currentRjId.value
                            if (cleanId.isNotBlank()) {
                                _currentRjId.value = cleanId
                            }
                            if (prevRj != cleanId && cleanId.isNotBlank() && !cleanId.startsWith("http") && !cleanId.contains('/')) {
                                loadChaptersForCurrentTrack(cleanId)
                                if (translationDelegate.isTranslationActive.value) {
                                    loadOrFetchTranslations(cleanId)
                                } else {
                                    translationDelegate.resetPlayerTranslations()
                                }
                            }
                            mediaItem.mediaMetadata.let { meta ->
                                _currentTitle.value = meta.title?.toString() ?: ""
                                _currentArtist.value = meta.artist?.toString() ?: "-"
                                val cover = meta.artworkUri?.toString()
                                if (!cover.isNullOrBlank()) {
                                    _currentCoverUrl.value = cover
                                    preloadCoverArtInMemory(cover)
                                }
                            }
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                                _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
                            } else if (p.isPlaying) {
                                _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
                            }
                            if (p.duration > 0L) {
                                _duration.value = p.duration.coerceAtLeast(0L)
                            }
                            saveCurrentState()
                        }
                    }
                    loadLyricsForCurrentTrack()
                }
                updatePlayingState(p)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlayingState(p)
                if (playbackState == Player.STATE_ENDED) {
                    if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
                        p.seekTo(p.currentMediaItemIndex, 0L)
                        p.play()
                    } else if (_isOnlineStreaming.value) {
                        if (_repeatMode.value == Player.REPEAT_MODE_ALL && p.mediaItemCount > 0) {
                            p.seekTo(0, 0L)
                            p.play()
                        }
                    } else {
                        playAdjacentWork(forward = true)
                    }
                } else if (playbackState == Player.STATE_READY) {
                    if (isCurrentVirtualAlbum || _isOnlineStreaming.value) {
                        val totalDur = _currentChapters.value.lastOrNull()?.endMs ?: 0L
                        if (totalDur > 0L) _duration.value = totalDur
                        val curIdx = p.currentMediaItemIndex
                        val curChapter = _currentChapters.value.getOrNull(curIdx)
                        val baseMs = curChapter?.startMs ?: 0L
                        _currentPosition.value = (baseMs + p.currentPosition).coerceAtLeast(0L)
                    } else {
                        if (p.duration > 0L) {
                            _duration.value = p.duration.coerceAtLeast(0L)
                        }
                        if (p.isPlaying) {
                            _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
                        }
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                // When toggled via system notification or external controller
                if (repeatMode == Player.REPEAT_MODE_ONE) {
                    _repeatMode.value = Player.REPEAT_MODE_ONE
                } else if (repeatMode == Player.REPEAT_MODE_OFF && _repeatMode.value == Player.REPEAT_MODE_ONE) {
                    _repeatMode.value = Player.REPEAT_MODE_OFF
                }
                saveCurrentState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleMode.value = shuffleModeEnabled
                saveCurrentState()
            }
        })

        p.repeatMode = if (_repeatMode.value == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.shuffleModeEnabled = _shuffleMode.value
        updatePlayingState(p)
    }

    private fun updatePlayingState(p: Player) {
        val playing = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
        if (_isPlaying.value != playing) {
            _isPlaying.value = playing
            if (playing) {
                startProgressTracking()
            } else {
                stopProgressTracking()
            }
            saveCurrentState()
        }
    }

    private fun calculateAudioSpecs(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var sampleRate = 44100
                var bitDepth = 16
                var bitrateKbps = 0

                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                                bitrateKbps = format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
                            }
                            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                val pcm = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                bitDepth = if (pcm == 3) 8 else if (pcm == 4) 32 else 16
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                } finally {
                    try { extractor.release() } catch (e: Exception) {}
                }

                if (bitrateKbps <= 0) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durMs = durStr?.toLongOrNull() ?: 0L
                        if (durMs > 0) {
                            bitrateKbps = ((file.length() * 8L) / durMs).toInt()
                        }
                    } catch (e: Exception) {
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }
                }

                if (bitrateKbps <= 0) bitrateKbps = 1064

                val sampleRateKhz = String.format(Locale.US, "%.1fkHz", sampleRate / 1000.0)
                val specsStr = "$sampleRateKhz | ${bitDepth}bits | ${bitrateKbps}kbps"
                _audioSpecs.value = specsStr
                if (sp.getBoolean("setting_auto_resume", true)) {
                    sp.edit().putString("cached_specs", specsStr).commit()
                }
            } catch (e: Exception) {
                _audioSpecs.value = "44.1kHz | 16bits | 1064kbps"
            }
        }
    }

    fun playLocalTrack(history: HistoryEntity, fullList: List<HistoryEntity> = _playlist.value, startPositionMs: Long = 0L) {
        val cleanRj = history.linkedRjId ?: history.rjid
        val earlyCover = if (!history.coverUrl.isNullOrBlank() && File(history.coverUrl).exists()) {
            val audioF = File(history.localFilePath)
            if (audioF.isFile && AudioStorageHelper.isMismatchedFolderCover(audioF, history.coverUrl, cleanRj)) {
                val cached = AudioStorageHelper.getLocalCoverFile(getApplication(), cleanRj, history.localFilePath)
                cached?.absolutePath ?: history.coverUrl
            } else {
                history.coverUrl
            }
        } else {
            val cachedCover = AudioStorageHelper.getLocalCoverFile(getApplication(), cleanRj, history.localFilePath)
            cachedCover?.absolutePath ?: history.coverUrl
        }
        if (!earlyCover.isNullOrBlank()) {
            _currentCoverUrl.value = earlyCover
            preloadCoverArtInMemory(earlyCover)
        }

        viewModelScope.launch {
            val p = player ?: return@launch
            _isOnlineStreaming.value = false
            val customDirStr = prefs.downloadDirFlow.first()
            val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()

            // Resolve target item directly without scanning the full playlist
            val targetFile = withContext(Dispatchers.IO) {
                AudioStorageHelper.resolveValidAudioFile(downloadDir, history.localFilePath, history.rjid)
            } ?: return@launch

            if (targetFile.absolutePath != history.localFilePath) {
                historyDao.insertHistory(history.copy(localFilePath = targetFile.absolutePath))
            }

            val isFolder = history.sourceType == SourceType.LOCAL_FOLDER.name || targetFile.isDirectory

            if (isFolder) {
                _playbackMode.value = PlaybackMode.OFFLINE_ALBUM
                isCurrentVirtualAlbum = true
                currentAlbumHistory = history
                val folder = targetFile
                val tracks = withContext(Dispatchers.IO) {
                    AudioStorageHelper.getAudioFilesInFolder(folder).ifEmpty {
                        AudioStorageHelper.getAudioFilesRecursive(folder)
                    }
                }
                if (tracks.isEmpty()) return@launch

                val cleanRj = history.linkedRjId ?: history.rjid
                val customCover = if (!history.coverUrl.isNullOrBlank() && File(history.coverUrl).exists()) {
                    if (targetFile.isFile && AudioStorageHelper.isMismatchedFolderCover(targetFile, history.coverUrl, cleanRj)) {
                        null
                    } else {
                        history.coverUrl
                    }
                } else null
                val localCover = customCover ?: withContext(Dispatchers.IO) {
                    AudioStorageHelper.getLocalCoverFile(getApplication(), cleanRj, targetFile.absolutePath)?.absolutePath
                }
                val finalCover = localCover ?: history.coverUrl
                val coverBytes = loadArtworkBytes(targetFile.absolutePath, finalCover)

                // Check RAM cache or DB chaptersJson
                val cachedChapters = AudioStorageHelper.getCachedVirtualChapters(history.rjid)
                    ?: if (!history.chaptersJson.isNullOrBlank()) {
                        ChapterItem.fromJsonArray(history.chaptersJson).takeIf { it.size == tracks.size }
                    } else null

                if (cachedChapters != null) {
                    AudioStorageHelper.cacheVirtualChapters(history.rjid, cachedChapters)
                }

                val initialChapters = cachedChapters ?: tracks.mapIndexed { idx, tr ->
                    ChapterItem(0L, 0L, tr.nameWithoutExtension, tr.name)
                }

                _currentChapters.value = initialChapters
                val totalDur = initialChapters.lastOrNull()?.endMs ?: 0L
                if (totalDur > 0L) {
                    _duration.value = totalDur
                }

                val mediaItems = tracks.mapIndexed { idx, tr ->
                    val trackTitle = initialChapters.getOrNull(idx)?.title ?: tr.nameWithoutExtension
                    val metaBuilder = MediaMetadata.Builder()
                        .setTitle(trackTitle)
                        .setArtist(history.cv)
                        .setAlbumTitle(history.title)
                        .setArtworkUri(Uri.parse(finalCover ?: ""))
                    if (coverBytes != null) {
                        metaBuilder.setArtworkData(coverBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(tr))
                        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.fromFile(tr)).build())
                        .setMediaId(history.rjid)
                        .setMediaMetadata(metaBuilder.build())
                        .build()
                }

                _currentRjId.value = history.rjid
                val isLocalV = history.sourceType != SourceType.DL_SITE.name || history.rjid.startsWith("LOCAL_")
                val tag = when {
                    isLocalV && !history.linkedRjId.isNullOrBlank() -> "IMPORT • ${history.linkedRjId}"
                    isLocalV -> "IMPORT"
                    else -> history.rjid
                }
                _currentTitle.value = "[$tag] ${history.title}"
                _currentArtist.value = history.cv
                _currentCoverUrl.value = finalCover
                preloadCoverArtInMemory(finalCover)

                val initialTrackIdx = if (startPositionMs > 0L && cachedChapters != null) {
                    cachedChapters.indexOfLast { startPositionMs >= it.startMs }.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
                } else 0
                _currentStreamTrackTitle.value = initialChapters.getOrNull(initialTrackIdx)?.title ?: tracks.getOrNull(initialTrackIdx)?.nameWithoutExtension
                val initialOffset = if (startPositionMs > 0L && cachedChapters != null) {
                    (startPositionMs - (cachedChapters.getOrNull(initialTrackIdx)?.startMs ?: 0L)).coerceAtLeast(0L)
                } else 0L

                if (tracks.isNotEmpty()) {
                    calculateAudioSpecs(tracks[initialTrackIdx])
                }

                // Play immediately with zero delay
                p.setMediaItems(mediaItems, initialTrackIdx, initialOffset)
                p.prepare()
                p.play()
                _currentPosition.value = startPositionMs
                saveCurrentState()

                // If chapters were not cached yet, calculate in background and save to DB
                if (cachedChapters == null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val computed = AudioStorageHelper.extractVirtualAlbumChapters(tracks)
                        AudioStorageHelper.cacheVirtualChapters(history.rjid, computed)
                        val json = ChapterItem.toJsonArray(computed)
                        val updated = history.copy(chaptersJson = json)
                        historyDao.insertHistory(updated)

                        val curList = _playlist.value.toMutableList()
                        val idx = curList.indexOfFirst { it.rjid == history.rjid }
                        if (idx != -1) {
                            curList[idx] = updated
                            _playlist.value = curList
                            saveFastCachedPlaylist(curList)
                        }

                        withContext(Dispatchers.Main) {
                            if (_currentRjId.value == history.rjid && isCurrentVirtualAlbum) {
                                _currentChapters.value = computed
                                val dur = computed.lastOrNull()?.endMs ?: 0L
                                if (dur > 0L) _duration.value = dur
                            }
                        }
                    }
                }
            } else {
                _playbackMode.value = PlaybackMode.OFFLINE_SINGLE
                isCurrentVirtualAlbum = false
                currentAlbumHistory = null
                calculateAudioSpecs(targetFile)

                val cleanRj = history.linkedRjId ?: history.rjid
                val customCover = if (!history.coverUrl.isNullOrBlank() && File(history.coverUrl).exists()) {
                    if (targetFile.isFile && AudioStorageHelper.isMismatchedFolderCover(targetFile, history.coverUrl, cleanRj)) {
                        null
                    } else {
                        history.coverUrl
                    }
                } else null
                val localCover = customCover ?: withContext(Dispatchers.IO) {
                    AudioStorageHelper.getLocalCoverFile(getApplication(), cleanRj, targetFile.absolutePath)?.absolutePath
                }
                val finalCover = localCover ?: history.coverUrl
                val coverBytes = loadArtworkBytes(targetFile.absolutePath, finalCover)

                val isLocalV = history.sourceType != SourceType.DL_SITE.name || history.rjid.startsWith("LOCAL_")
                val tag = when {
                    isLocalV && !history.linkedRjId.isNullOrBlank() -> "IMPORT • ${history.linkedRjId}"
                    isLocalV -> "IMPORT"
                    else -> history.rjid
                }
                val metaTitle = "[$tag] ${history.title}"

                val metaBuilder = MediaMetadata.Builder()
                    .setTitle(metaTitle)
                    .setArtist(history.cv)
                    .setAlbumTitle(history.circle)
                    .setArtworkUri(Uri.parse(finalCover ?: ""))

                if (coverBytes != null) {
                    metaBuilder.setArtworkData(coverBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }

                val singleMediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(targetFile))
                    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.fromFile(targetFile)).build())
                    .setMediaId(history.rjid)
                    .setMediaMetadata(metaBuilder.build())
                    .build()

                _currentRjId.value = history.rjid
                _currentTitle.value = metaTitle
                _currentArtist.value = history.cv
                _currentCoverUrl.value = finalCover
                preloadCoverArtInMemory(finalCover)
                loadChaptersForCurrentTrack(history.rjid)
                if (translationDelegate.isTranslationActive.value) {
                    loadOrFetchTranslations(history.rjid)
                } else {
                    translationDelegate.resetPlayerTranslations()
                }

                p.setMediaItems(listOf(singleMediaItem), 0, startPositionMs)
                p.prepare()
                p.play()
                saveCurrentState()
            }
        }
    }

    /**
     * Plays an online work with all audio tracks mapped into chapters.
     */
    fun playOnlineWork(
        rjid: String,
        workTitle: String,
        cv: String,
        coverUrl: String,
        tracks: List<AsmrOneTrack>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L
    ) {
        val p = player ?: run {
            initMediaController()
            return
        }
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            _playbackMode.value = PlaybackMode.ONLINE_STREAM
            _isOnlineStreaming.value = true
            isCurrentVirtualAlbum = false
            currentAlbumHistory = null

            val targetIndex = startIndex.coerceIn(0, tracks.size - 1)
            val currentTrack = tracks[targetIndex]
            val initialTitle = "[$rjid] $workTitle"

            _currentRjId.value = rjid
            _currentTitle.value = initialTitle
            _currentStreamTrackTitle.value = currentTrack.title
            _currentArtist.value = cv
            _currentCoverUrl.value = coverUrl
            preloadCoverArtInMemory(coverUrl)

            // Populate chapter drawer with all tracks of the online work accumulating real durations
            var accumulatedMs = 0L
            val chapters = tracks.map { tr ->
                val durMs = if (tr.duration > 0L) tr.duration * 1000L else 180_000L
                val start = accumulatedMs
                accumulatedMs += durMs
                ChapterItem(
                    startMs = start,
                    endMs = accumulatedMs,
                    title = tr.title
                )
            }
            _currentChapters.value = chapters
            _duration.value = accumulatedMs
            translationDelegate.resetPlayerTranslations()
            if (translationDelegate.isTranslationActive.value) {
                loadOrFetchTranslations(rjid, forceOnline = true)
            }
            _audioSpecs.value = "44.1kHz | 16bits | STREAM"

            val isPriv = privacyEnabled.value
            val blurNotif = isPriv && privacyBlurNotif.value
            val maskNotifTitle = isPriv && privacyMaskTitle.value
            val customMask = privacyCustomMask.value
            val coverBytes = loadArtworkBytes(null, coverUrl, shouldBlur = blurNotif)

            val mediaItems = tracks.mapIndexed { idx, tr ->
                val meta = buildMediaMetadataWithPrivacy(
                    title = tr.title,
                    artist = cv,
                    albumTitle = workTitle,
                    coverUrl = coverUrl,
                    coverBytes = coverBytes,
                    blurNotif = blurNotif,
                    maskTitle = maskNotifTitle,
                    customMask = customMask
                )

                MediaItem.Builder()
                    .setUri(Uri.parse(tr.streamUrl))
                    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(tr.streamUrl)).build())
                    .setMediaId("${rjid}_$idx")
                    .setMediaMetadata(meta)
                    .build()
            }

            val resolvedIndex = if (startIndex == 0 && startPositionMs > 0L && chapters.isNotEmpty()) {
                chapters.indexOfLast { startPositionMs >= it.startMs }.coerceIn(0, tracks.size - 1)
            } else targetIndex
            val resolvedOffsetMs = if (startIndex == 0 && startPositionMs > 0L && chapters.isNotEmpty()) {
                (startPositionMs - chapters[resolvedIndex].startMs).coerceAtLeast(0L)
            } else 0L

            p.setMediaItems(mediaItems, resolvedIndex, resolvedOffsetMs)
            p.prepare()
            p.play()
            loadLyricsForCurrentTrack()
        }
    }

    /**
     * Plays an online audio stream directly via ExoPlayer.
     */
    fun playOnlineStream(
        rjid: String,
        workTitle: String,
        trackTitle: String,
        cv: String,
        streamUrl: String,
        coverUrl: String
    ) {
        val singleTrack = AsmrOneTrack(title = trackTitle, streamUrl = streamUrl)
        playOnlineWork(rjid, workTitle, cv, coverUrl, listOf(singleTrack), 0)
    }

    private fun buildMediaMetadataWithPrivacy(
        title: String,
        artist: String,
        albumTitle: String,
        coverUrl: String?,
        coverBytes: ByteArray?,
        blurNotif: Boolean,
        maskTitle: Boolean,
        customMask: String
    ): MediaMetadata {
        val builder = MediaMetadata.Builder()
        if (maskTitle) {
            builder.setTitle(customMask.ifBlank { "Audio Book" })
                .setArtist("-")
                .setAlbumTitle("")
        } else {
            builder.setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(albumTitle)
        }
        if (blurNotif) {
            if (coverBytes != null) {
                builder.setArtworkData(coverBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        } else {
            if (!coverUrl.isNullOrBlank()) {
                builder.setArtworkUri(Uri.parse(coverUrl))
            }
            if (coverBytes != null) {
                builder.setArtworkData(coverBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        return builder.build()
    }

    private suspend fun loadArtworkBytes(
        localFilePath: String?,
        coverUrl: String?,
        shouldBlur: Boolean = false
    ): ByteArray? = withContext(Dispatchers.IO) {
        var rawBytes: ByteArray? = null
        try {
            if (!localFilePath.isNullOrBlank()) {
                val f = File(localFilePath)
                if (f.exists() && f.isFile) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(f.absolutePath)
                        val pic = retriever.embeddedPicture
                        if (pic != null && pic.isNotEmpty() && pic.size <= 512 * 1024) {
                            rawBytes = pic
                        }
                    } catch (_: Exception) {
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }
            }
            if (rawBytes == null && !coverUrl.isNullOrBlank()) {
                if (coverUrl.startsWith("http://") || coverUrl.startsWith("https://")) {
                    if (shouldBlur) {
                        rawBytes = URL(coverUrl).openStream().use { it.readBytes() }
                    }
                } else {
                    val coverFile = File(coverUrl)
                    if (coverFile.exists() && coverFile.isFile) {
                        rawBytes = coverFile.readBytes()
                    }
                }
            }
            if (shouldBlur && rawBytes != null) {
                return@withContext blurArtworkBytes(rawBytes) ?: rawBytes
            }
        } catch (_: Exception) {
        }
        return@withContext if (rawBytes != null && rawBytes.size <= 256 * 1024) rawBytes else null
    }

    private fun blurArtworkBytes(rawBytes: ByteArray): ByteArray? {
        return try {
            val orig = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
            // Strong privacy blur via downscale to 18x18 then upscale to 256x256 with bilinear filter
            val tiny = Bitmap.createScaledBitmap(orig, 18, 18, true)
            val blurred = Bitmap.createScaledBitmap(tiny, 256, 256, true)
            if (orig != tiny) orig.recycle()
            if (tiny != blurred) tiny.recycle()
            val out = ByteArrayOutputStream()
            blurred.compress(Bitmap.CompressFormat.JPEG, 80, out)
            blurred.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    fun togglePlayPause() {
        val p = player ?: run {
            initMediaController()
            return
        }
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_IDLE || p.mediaItemCount == 0) {
                val rj = _currentRjId.value
                if (!rj.isNullOrBlank()) {
                    viewModelScope.launch {
                        val savedPos = _currentPosition.value
                        val history = withContext(Dispatchers.IO) { historyDao.getHistoryById(rj) }
                        if (history != null) {
                            playLocalTrack(history, _playlist.value, startPositionMs = savedPos)
                        } else {
                            // Resume online stream from asmr.one when no local history exists
                            val priority = prefs.audioFormatPriorityFlow.first()
                            val tracks = withContext(Dispatchers.IO) {
                                AsmrOneApiService.getWorkTracks(rj, priority).getOrNull() ?: emptyList()
                            }
                            if (tracks.isNotEmpty()) {
                                val cleanTitle = _currentTitle.value
                                    .replace(Regex("^\\[(?:RJ|VJ|BJ)?\\d+]\\s*"), "")
                                    .trim()
                                    .ifBlank { rj }
                                playOnlineWork(
                                    rjid = rj,
                                    workTitle = cleanTitle,
                                    cv = _currentArtist.value,
                                    coverUrl = _currentCoverUrl.value ?: "",
                                    tracks = tracks,
                                    startPositionMs = savedPos
                                )
                            }
                        }
                    }
                }
            } else {
                p.play()
            }
        }
        saveCurrentState()
    }

    fun playNext() {
        val p = player
        if (_isOnlineStreaming.value && p != null) {
            if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && p.mediaItemCount > 0) {
                p.seekTo(0, 0L)
            }
            return
        }
        playAdjacentWork(forward = true)
        saveCurrentState()
    }

    fun playPrevious() {
        val p = player
        if (_isOnlineStreaming.value && p != null) {
            if (p.currentPosition > 3000) {
                p.seekTo(0)
            } else if (p.hasPreviousMediaItem()) {
                p.seekToPreviousMediaItem()
            } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && p.mediaItemCount > 0) {
                p.seekTo(p.mediaItemCount - 1, 0L)
            }
            return
        }
        val pos = if (isCurrentVirtualAlbum) _currentPosition.value else (p?.currentPosition ?: 0L)
        if (p != null && pos > 3000) {
            seekTo(0L)
        } else {
            playAdjacentWork(forward = false)
        }
        saveCurrentState()
    }

    private fun playAdjacentWork(forward: Boolean) {
        val list = filteredPlaylist.value.ifEmpty { _playlist.value }
        if (list.isEmpty()) return
        val curId = _currentRjId.value ?: currentAlbumHistory?.rjid
        var curIdx = list.indexOfFirst { it.rjid == curId }
        if (curIdx == -1 && currentAlbumHistory != null) {
            curIdx = list.indexOfFirst { it.rjid == currentAlbumHistory?.rjid || it.localFilePath == currentAlbumHistory?.localFilePath }
        }
        if (curIdx == -1 && !curId.isNullOrBlank()) {
            curIdx = list.indexOfFirst { it.localFilePath == curId || it.linkedRjId == curId }
        }
        if (curIdx != -1) {
            val targetIdx = if (_shuffleMode.value) {
                if (list.size > 1) {
                    var r = (0 until list.size).random()
                    while (r == curIdx) {
                        r = (0 until list.size).random()
                    }
                    r
                } else curIdx
            } else if (forward) {
                if (curIdx + 1 < list.size) curIdx + 1
                else if (_repeatMode.value == Player.REPEAT_MODE_ALL) 0 else -1
            } else {
                if (curIdx - 1 >= 0) curIdx - 1
                else if (_repeatMode.value == Player.REPEAT_MODE_ALL) list.size - 1 else -1
            }
            if (targetIdx != -1) {
                playLocalTrack(list[targetIdx], list)
            }
        } else if (list.isNotEmpty()) {
            playLocalTrack(list.first(), list)
        }
    }

    fun replay10() {
        seekTo((_currentPosition.value - 10000L).coerceAtLeast(0L))
    }

    fun forward10() {
        seekTo((_currentPosition.value + 10000L).coerceAtMost(_duration.value.coerceAtLeast(0L)))
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        if ((isCurrentVirtualAlbum || _isOnlineStreaming.value) && _currentChapters.value.isNotEmpty()) {
            val chapters = _currentChapters.value
            val trackIdx = chapters.indexOfLast { positionMs >= it.startMs }.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
            val offset = (positionMs - chapters[trackIdx].startMs).coerceAtLeast(0L)
            p.seekTo(trackIdx, offset)
            _currentPosition.value = positionMs
        } else {
            p.seekTo(positionMs.coerceIn(0L, _duration.value.coerceAtLeast(0L)))
            _currentPosition.value = positionMs
        }
        saveCurrentState()
    }

    fun cycleRepeatMode() {
        val p = player ?: return
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        p.repeatMode = if (nextMode == Player.REPEAT_MODE_ONE) {
            Player.REPEAT_MODE_ONE
        } else if (_isOnlineStreaming.value && nextMode == Player.REPEAT_MODE_ALL) {
            Player.REPEAT_MODE_ALL
        } else {
            Player.REPEAT_MODE_OFF
        }
        saveCurrentState()
    }

    fun toggleShuffleMode() {
        val p = player ?: return
        if (_isOnlineStreaming.value) return // shuffle is disabled during online streaming
        val nextState = !p.shuffleModeEnabled
        p.shuffleModeEnabled = nextState
        _shuffleMode.value = nextState
        saveCurrentState()
    }

    fun seekToChapter(chapter: ChapterItem) {
        val p = player ?: return
        if ((isCurrentVirtualAlbum || _isOnlineStreaming.value || p.mediaItemCount > 1) && _currentChapters.value.isNotEmpty()) {
            val idx = _currentChapters.value.indexOf(chapter)
            if (idx != -1 && idx < p.mediaItemCount) {
                p.seekToDefaultPosition(idx)
                _currentPosition.value = chapter.startMs
                saveCurrentState()
                return
            }
        }
        seekTo(chapter.startMs)
    }

    fun loadChaptersForCurrentTrack(targetRjid: String? = _currentRjId.value, forceOnline: Boolean = false) {
        if (targetRjid.isNullOrBlank()) {
            _currentChapters.value = emptyList()
            return
        }

        viewModelScope.launch {
            if (_isOnlineStreaming.value || _playbackMode.value == PlaybackMode.ONLINE_STREAM) {
                if (_currentChapters.value.isNotEmpty()) {
                    checkTranslateChaptersIfNeeded(targetRjid, _currentChapters.value, forceOnline = forceOnline)
                }
                return@launch
            }
            _isLoadingChapters.value = true
            try {
                fun isBogus(chapters: List<ChapterItem>): Boolean {
                    if (chapters.isEmpty()) return false
                    val hasGarbageTitle = chapters.any { ch ->
                        val t = ch.title
                        t.contains("http://") || t.contains("https://") ||
                                t.contains("x.com") || t.contains("twitter.com") ||
                                t.contains("CV:") || t.contains("CV：") ||
                                t.contains("クレジット") ||
                                t.contains("pixiv") || t.contains("dlsite") ||
                                t.startsWith("◆トラック") ||
                                t.contains("同時に犯す") || t.contains("妹の処女喪失")
                    }
                    val hasLateStart = chapters.first().startMs > 60000L
                    val hasMisplacedEpilogue = chapters.filterIndexed { idx, ch ->
                        idx < chapters.size - 1 && (ch.title.contains("エピローグ") || ch.title.contains("epilogue", ignoreCase = true))
                    }.isNotEmpty()
                    return hasGarbageTitle || hasLateStart || hasMisplacedEpilogue
                }

                // 0. JIKA INI MERUPAKAN VIRTUAL MULTI-TRACK ALBUM (FOLDER LOKAL):
                // Bab selalu bersumber dari file-file fisik di dalam folder tersebut!
                val history = withContext(Dispatchers.IO) { historyDao.getHistoryById(targetRjid) }
                val targetFile = history?.localFilePath?.let { File(it) }

                if (targetFile != null && targetFile.exists() && (history?.sourceType == SourceType.LOCAL_FOLDER.name || targetFile.isDirectory)) {
                    val folder = targetFile
                    val tracks = withContext(Dispatchers.IO) {
                    AudioStorageHelper.getAudioFilesInFolder(folder).ifEmpty {
                        AudioStorageHelper.getAudioFilesRecursive(folder)
                    }
                }
                    if (tracks.isNotEmpty()) {
                        val freshChapters = AudioStorageHelper.extractVirtualAlbumChapters(tracks)
                        AudioStorageHelper.cacheVirtualChapters(targetRjid, freshChapters)
                        withContext(Dispatchers.IO) {
                            historyDao.updateChapters(targetRjid, ChapterItem.toJsonArray(freshChapters))
                        }
                        _currentChapters.value = freshChapters
                        val totalDur = freshChapters.lastOrNull()?.endMs ?: 0L
                        if (totalDur > 0L) {
                            _duration.value = totalDur
                        }

                        // Jika ditautkan dengan kode RJ, sync juga metadata dari DLsite jika forceOnline
                        if (forceOnline && !history.linkedRjId.isNullOrBlank()) {
                            withContext(Dispatchers.IO) {
                                try {
                                    var meta = DLsiteScraper.fetchMetadata(history.linkedRjId)
                                    if (meta.title == history.linkedRjId || meta.cv == "-" || meta.circle == "-" || meta.genre == "-") {
                                        val asmrWork = AsmrOneApiService.getWork(history.linkedRjId).getOrNull()
                                        if (asmrWork != null) {
                                            meta = meta.copy(
                                                title = if (meta.title.isNotBlank() && meta.title != history.linkedRjId) meta.title else asmrWork.title,
                                                cv = if (meta.cv.isNotBlank() && meta.cv != "-") meta.cv else asmrWork.cv,
                                                circle = if (meta.circle.isNotBlank() && meta.circle != "-") meta.circle else asmrWork.circle,
                                                genre = if (meta.genre.isNotBlank() && meta.genre != "-") meta.genre else asmrWork.tags.joinToString(", "),
                                                ageRating = if (meta.ageRating.isNotBlank() && meta.ageRating != "-") meta.ageRating else asmrWork.ageRating
                                            )
                                        }
                                    }
                                    val updated = history.copy(
                                        title = if (meta.title.isNotBlank()) meta.title else history.title,
                                        cv = if (meta.cv.isNotBlank() && meta.cv != "-") meta.cv else history.cv,
                                        circle = if (meta.circle.isNotBlank() && meta.circle != "-") meta.circle else history.circle,
                                        genre = if (meta.genre.isNotBlank() && meta.genre != "-") meta.genre else history.genre,
                                        ageRating = if (meta.ageRating.isNotBlank() && meta.ageRating != "-") meta.ageRating else history.ageRating
                                    )
                                    historyDao.insertHistory(updated)
                                } catch (e: Exception) {}
                            }
                        }

                        checkTranslateChaptersIfNeeded(targetRjid, freshChapters)
                        return@launch
                    }
                }

                // 1. Cek cache database terlebih dahulu (jika bukan forceOnline)
                if (!forceOnline && !history?.chaptersJson.isNullOrBlank()) {
                    val cached = ChapterItem.fromJsonArray(history?.chaptersJson)
                    val cleaned = ChapterDiscoveryService.deduplicateChapters(cached)
                    val valid = if (_duration.value > 0) cleaned.filter { it.startMs < _duration.value - 1000L } else cleaned
                    if (valid.isNotEmpty() && !isBogus(valid)) {
                        _currentChapters.value = valid
                        if (valid.size != cached.size) {
                            withContext(Dispatchers.IO) {
                                historyDao.updateChapters(targetRjid, ChapterItem.toJsonArray(valid))
                            }
                        }
                        checkTranslateChaptersIfNeeded(targetRjid, valid)
                        return@launch
                    }
                }

                // 2. Cek file .cue lokal di storage
                val localChapters = withContext(Dispatchers.IO) {
                    val customDirStr = prefs.downloadDirFlow.first()
                    val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()
                    val audioFile = AudioStorageHelper.resolveValidAudioFile(downloadDir, history?.localFilePath, targetRjid)
                    if (audioFile != null && audioFile.exists()) {
                        val cueFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.cue")
                        if (cueFile.exists()) {
                            val parsed = ChapterDiscoveryService.parseCueFile(cueFile)
                            val deduped = ChapterDiscoveryService.deduplicateChapters(parsed)
                            val dur = AudioStorageHelper.resolveValidAudioFile(downloadDir, history?.localFilePath, targetRjid)?.let {
                                ChapterDiscoveryService.getAudioDurationMs(it)
                            } ?: 0L
                            if (dur > 0L) deduped.filter { it.startMs < dur - 1000L } else deduped
                        } else null
                    } else null
                }

                if (!forceOnline && !localChapters.isNullOrEmpty() && !isBogus(localChapters)) {
                    _currentChapters.value = localChapters
                    withContext(Dispatchers.IO) {
                        historyDao.updateChapters(targetRjid, ChapterItem.toJsonArray(localChapters))
                    }
                    checkTranslateChaptersIfNeeded(targetRjid, localChapters)
                    return@launch
                }

                // 3. Online Chapter Discovery fallback (japaneseasmr.com / asmr.one)
                val onlineChapters = withContext(Dispatchers.IO) {
                    val fetched = ChapterDiscoveryService.discoverChapters(targetRjid)
                    val deduped = ChapterDiscoveryService.deduplicateChapters(fetched)
                    if (_duration.value > 0L) deduped.filter { it.startMs < _duration.value - 1000L } else deduped
                }

                if (onlineChapters.isNotEmpty()) {
                    _currentChapters.value = onlineChapters
                    withContext(Dispatchers.IO) {
                        historyDao.updateChapters(targetRjid, ChapterItem.toJsonArray(onlineChapters))
                    }
                } else if (!localChapters.isNullOrEmpty()) {
                    _currentChapters.value = localChapters
                } else {
                    // JANGAN kosongkan bab yang sudah ada jika pencarian online tidak menemukan bab baru
                    val existingFromDb = if (!history?.chaptersJson.isNullOrBlank()) {
                        ChapterItem.fromJsonArray(history.chaptersJson)
                    } else emptyList()
                    if (existingFromDb.isNotEmpty()) {
                        _currentChapters.value = existingFromDb
                    } else {
                        _currentChapters.value = emptyList()
                    }
                }

                checkTranslateChaptersIfNeeded(targetRjid, _currentChapters.value)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingChapters.value = false
            }
        }
    }

    fun clearInMemoryTranslations() = translationDelegate.clearInMemoryTranslations()

    fun toggleTranslation(context: Context? = null) = translationDelegate.toggleTranslation()

    fun togglePlaylistTranslation() = translationDelegate.togglePlaylistTranslation()

    fun translateCurrentPageTitles(
        items: List<com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity>,
        forceRefresh: Boolean = false
    ) = translationDelegate.translateCurrentPageTitles(items, forceRefresh)

    fun translateCustomPlaylistTitles(
        items: List<com.saymaven.downloader.japaneseasmr.data.local.entity.PlaylistItemEntity>,
        forceRefresh: Boolean = false
    ) = translationDelegate.translateCustomPlaylistTitles(items, forceRefresh)

    fun translatePlaylistTitles(targetLang: String = TranslationService.getActiveTargetLanguage(getApplication())) {
        translateCurrentPageTitles(_playlist.value.take(40))
    }

    fun loadOrFetchTranslations(targetRjid: String? = _currentRjId.value, forceOnline: Boolean = false) =
        translationDelegate.loadOrFetchTranslations(targetRjid, forceOnline)

    private fun checkTranslateChaptersIfNeeded(
        targetRjid: String,
        chapters: List<ChapterItem>,
        forceOnline: Boolean = false
    ) = translationDelegate.checkTranslateChaptersIfNeeded(targetRjid, chapters, forceOnline)

    private fun saveCacheSynchronously() {
        val autoResume = sp.getBoolean("setting_auto_resume", true)
        if (autoResume) {
            sp.edit()
                .putString("cached_rjid", _currentRjId.value)
                .putString("cached_title", _currentTitle.value)
                .putString("cached_artist", _currentArtist.value)
                .putString("cached_cover", _currentCoverUrl.value)
                .putLong("cached_pos", _currentPosition.value)
                .putLong("cached_duration", _duration.value)
                .putString("cached_specs", _audioSpecs.value)
                .putInt("cached_repeat", _repeatMode.value)
                .putBoolean("cached_shuffle", _shuffleMode.value)
                .putBoolean("cached_show_remaining", _showRemainingTime.value)
                .apply()
        }
    }

    private fun saveCurrentState() {
        saveCacheSynchronously()
        viewModelScope.launch {
            val autoResume = prefs.autoResumeFlow.first()
            if (autoResume) {
                prefs.savePlaybackState(
                    rjid = _currentRjId.value,
                    positionMs = _currentPosition.value,
                    repeatMode = _repeatMode.value,
                    shuffleMode = _shuffleMode.value,
                    showRemainingTime = _showRemainingTime.value
                )
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                player?.let { p ->
                    if ((isCurrentVirtualAlbum || _isOnlineStreaming.value) && _currentChapters.value.isNotEmpty()) {
                        val curIdx = p.currentMediaItemIndex
                        val curChapter = _currentChapters.value.getOrNull(curIdx)
                        val baseMs = curChapter?.startMs ?: 0L
                        _currentPosition.value = (baseMs + p.currentPosition).coerceAtLeast(0L)
                        val totalDur = _currentChapters.value.lastOrNull()?.endMs ?: 0L
                        if (totalDur > 0L) {
                            _duration.value = totalDur
                        }
                    } else {
                        _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
                        _duration.value = p.duration.coerceAtLeast(0L)
                    }
                    lyricsDelegate.updateActiveLyricForPosition(p.currentPosition.coerceAtLeast(0L))
                    saveCacheSynchronously()
                }
                delay(350)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        saveCurrentState()
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingMs.value = null
            return
        }

        var remaining = minutes * 60 * 1000L
        _sleepTimerRemainingMs.value = remaining

        sleepTimerJob = viewModelScope.launch {
            while (remaining > 0L) {
                delay(1000L)
                // Hanya hitung mundur ketika audio sedang aktif diputar
                if (_isPlaying.value) {
                    remaining -= 1000L
                    _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0L)
                }
            }
            // Timer habis: pause audio
            player?.pause()
            _sleepTimerRemainingMs.value = null
        }
    }

    fun adjustSleepTimerMinutes(deltaMinutes: Int, currentDraftMinutes: Int = 15): Int {
        val currentRemaining = _sleepTimerRemainingMs.value
        return if (currentRemaining != null && currentRemaining > 0L) {
            val currentMins = ((currentRemaining + 59999) / 60000).toInt()
            val newMins = (currentMins + deltaMinutes).coerceAtLeast(1)
            startSleepTimer(newMins)
            newMins
        } else {
            (currentDraftMinutes + deltaMinutes).coerceAtLeast(5)
        }
    }

    fun stopSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = null
    }

    fun linkRjCode(currentRjid: String, targetRjInput: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val rjMatch = StorageSyncManager.RJ_REGEX.find(targetRjInput.trim())
            if (rjMatch == null) {
                onResult(false, "Kode RJ tidak valid")
                return@launch
            }
            val targetRj = rjMatch.value.uppercase()
            val meta = withContext(Dispatchers.IO) {
                try {
                    var m = DLsiteScraper.fetchMetadata(targetRj)
                    if (m.title == targetRj || m.cv == "-" || m.circle == "-" || m.genre == "-") {
                        val asmrWork = AsmrOneApiService.getWork(targetRj).getOrNull()
                        if (asmrWork != null) {
                            m = m.copy(
                                title = if (m.title.isNotBlank() && m.title != targetRj) m.title else asmrWork.title,
                                cv = if (m.cv.isNotBlank() && m.cv != "-") m.cv else asmrWork.cv,
                                circle = if (m.circle.isNotBlank() && m.circle != "-") m.circle else asmrWork.circle,
                                genre = if (m.genre.isNotBlank() && m.genre != "-") m.genre else asmrWork.tags.joinToString(", "),
                                ageRating = if (m.ageRating.isNotBlank() && m.ageRating != "-") m.ageRating else asmrWork.ageRating,
                                coverUrl = if (!m.coverUrl.isNullOrBlank()) m.coverUrl else asmrWork.coverUrl
                            )
                        }
                    }
                    m
                } catch (e: Exception) {
                    null
                }
            }

            if (meta == null || (meta.title.isBlank() && meta.cv.isBlank())) {
                onResult(false, "Karya $targetRj tidak ditemukan di DLsite")
                return@launch
            }

            withContext(Dispatchers.IO) {
                val existing = historyDao.getHistoryById(currentRjid)
                if (existing != null) {
                    var resolvedCover = existing.coverUrl
                    if (!meta.coverUrl.isNullOrBlank()) {
                        try {
                            val coversDir = File(getApplication<Application>().cacheDir, "covers").apply { if (!exists()) mkdirs() }
                            val targetCoverFile = File(coversDir, "${targetRj}.jpg")
                            val req = Request.Builder()
                                .url(meta.coverUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                .header("Referer", "https://japaneseasmr.com")
                                .build()
                            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
                            val resp = client.newCall(req).execute()
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { input ->
                                    FileOutputStream(targetCoverFile).use { output -> input.copyTo(output) }
                                }
                                if (targetCoverFile.exists() && targetCoverFile.length() > 0) {
                                    resolvedCover = targetCoverFile.absolutePath
                                }
                            }
                        } catch (e: Exception) {}
                    }

                    val updated = existing.copy(
                        title = if (meta.title.isNotBlank()) meta.title else existing.title,
                        cv = if (meta.cv.isNotBlank() && meta.cv != "-") meta.cv else existing.cv,
                        circle = if (meta.circle.isNotBlank() && meta.circle != "-") meta.circle else existing.circle,
                        genre = if (meta.genre.isNotBlank() && meta.genre != "-") meta.genre else existing.genre,
                        ageRating = if (meta.ageRating.isNotBlank() && meta.ageRating != "-") meta.ageRating else existing.ageRating,
                        coverUrl = resolvedCover,
                        linkedRjId = targetRj
                    )
                    historyDao.insertHistory(updated)

                    val curList = _playlist.value.toMutableList()
                    val idx = curList.indexOfFirst { it.rjid == currentRjid }
                    if (idx != -1) {
                        curList[idx] = updated
                        _playlist.value = curList
                        saveFastCachedPlaylist(curList)
                    }

                    withContext(Dispatchers.Main) {
                        if (_currentRjId.value == currentRjid) {
                            val tag = if (updated.sourceType != SourceType.DL_SITE.name || updated.rjid.startsWith("LOCAL_")) {
                                if (!updated.linkedRjId.isNullOrBlank()) "IMPORT • ${updated.linkedRjId}" else "IMPORT"
                            } else updated.rjid
                            _currentTitle.value = "[$tag] ${updated.title}"
                            _currentArtist.value = updated.cv
                            _currentCoverUrl.value = updated.coverUrl
                            preloadCoverArtInMemory(updated.coverUrl)
                        }
                    }
                }
            }
            onResult(true, targetRj)
        }
    }

    fun updateCustomCover(targetRjid: String, newCoverPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = historyDao.getHistoryById(targetRjid)
            if (existing != null) {
                var finalCoverPath = newCoverPath
                try {
                    val uniqueKey = existing.rjid.uppercase().trim()
                    val coversDir = File(getApplication<Application>().cacheDir, "covers").apply { if (!exists()) mkdirs() }
                    val targetCustomFile = File(coversDir, "custom_${uniqueKey}.jpg")
                    val src = File(newCoverPath)
                    if (src.exists() && src.isFile && src.canonicalPath != targetCustomFile.canonicalPath) {
                        src.copyTo(targetCustomFile, overwrite = true)
                    }
                    if (targetCustomFile.exists() && targetCustomFile.length() > 0) {
                        finalCoverPath = targetCustomFile.absolutePath
                    }
                    AudioStorageHelper.clearLocalCoverCacheFor(existing.rjid)
                } catch (e: Exception) {}

                val updated = existing.copy(coverUrl = finalCoverPath)
                historyDao.insertHistory(updated)

                val curList = _playlist.value.toMutableList()
                val idx = curList.indexOfFirst { it.rjid == targetRjid }
                if (idx != -1) {
                    curList[idx] = updated
                    _playlist.value = curList
                    saveFastCachedPlaylist(curList)
                }

                withContext(Dispatchers.Main) {
                    if (_currentRjId.value == targetRjid) {
                        _currentCoverUrl.value = finalCoverPath
                        preloadCoverArtInMemory(finalCoverPath)
                    }
                }
            }
        }
    }

    // --- Playlist Management (Room v6) ---

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) =
        playlistDelegate.createPlaylist(name, onCreated)

    fun renamePlaylist(id: Long, newName: String) =
        playlistDelegate.renamePlaylist(id, newName)

    fun deletePlaylist(playlist: UserPlaylistEntity) =
        playlistDelegate.deletePlaylist(playlist)

    fun addWorkToPlaylist(
        playlistId: Long,
        rjid: String,
        title: String,
        coverUrl: String? = null,
        circle: String? = null,
        cv: String? = null
    ) = playlistDelegate.addWorkToPlaylist(playlistId, rjid, title, coverUrl, circle, cv)

    fun removeWorkFromPlaylist(playlistId: Long, rjid: String) =
        playlistDelegate.removeWorkFromPlaylist(playlistId, rjid)

    suspend fun getPlaylistIdsForWork(rjid: String): List<Long> =
        playlistDelegate.getPlaylistIdsForWork(rjid)

    fun getItemsForPlaylist(playlistId: Long): kotlinx.coroutines.flow.Flow<List<PlaylistItemEntity>> =
        playlistDelegate.getItemsForPlaylist(playlistId)

    fun playPlaylistItem(item: PlaylistItemEntity) =
        playlistDelegate.playPlaylistItem(item)

    // --- Audio Gain & Privacy Mode ---

    fun setAudioGainDb(gainDb: Float) {
        viewModelScope.launch {
            prefs.setAudioGainDb(gainDb)
        }
    }

    fun setPrivacyEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setPrivacyEnabled(enabled) }
    }

    fun setPrivacyBlurNotif(blur: Boolean) {
        viewModelScope.launch { prefs.setPrivacyBlurNotif(blur) }
    }

    fun setPrivacyBlurInApp(blur: Boolean) {
        viewModelScope.launch { prefs.setPrivacyBlurInApp(blur) }
    }

    fun setPrivacyMaskTitle(mask: Boolean) {
        viewModelScope.launch { prefs.setPrivacyMaskTitle(mask) }
    }

    fun setPrivacyCustomMask(text: String) {
        viewModelScope.launch { prefs.setPrivacyCustomMask(text) }
    }

    // --- Lyrics & Floating Lyrics ---

    fun setFloatingLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setFloatingLyricsEnabled(enabled) }
    }

    fun setFloatingLyricsLocked(locked: Boolean) {
        viewModelScope.launch { prefs.setFloatingLyricsLocked(locked) }
    }

    fun seekToLyricLine(line: LyricLine) {
        val p = player ?: return
        p.seekTo(line.startMs.coerceAtLeast(0L))
    }

    fun selectSubtitleSource(source: SubtitleSource) = lyricsDelegate.selectSubtitleSource(source)

    fun toggleLyricsTranslation() = lyricsDelegate.toggleLyricsTranslation()

    fun loadLyricsForCurrentTrack() = lyricsDelegate.loadLyricsForCurrentTrack()

    override fun onCleared() {
        sleepTimerJob?.cancel()
        saveCurrentState()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
