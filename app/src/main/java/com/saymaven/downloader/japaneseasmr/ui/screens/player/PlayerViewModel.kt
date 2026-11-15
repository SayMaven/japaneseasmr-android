package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import com.saymaven.downloader.japaneseasmr.data.model.SourceType
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import com.saymaven.downloader.japaneseasmr.data.remote.TrackDiscoveryService
import com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper
import com.saymaven.downloader.japaneseasmr.service.ChapterDiscoveryService
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.service.PlaybackService
import com.saymaven.downloader.japaneseasmr.service.StorageSyncManager
import com.saymaven.downloader.japaneseasmr.service.TranslationService
import com.saymaven.downloader.japaneseasmr.service.UsbDacManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    RJID
}

data class PlaylistSortState(
    val type: PlaylistSortType = PlaylistSortType.CUSTOM,
    val isAscending: Boolean = false
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val sp = application.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
    private val prefs = PreferencesManager(application)
    private val historyDao = AsmrDatabase.getDatabase(application).historyDao()
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
                PlaylistSortType.TITLE -> true
                else -> false
            }
            _sortState.value = PlaylistSortState(type = type, isAscending = defaultAsc)
            sp.edit()
                .putString("playlist_sort_type", type.name)
                .putBoolean("playlist_sort_asc", defaultAsc)
                .apply()
        }
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

    val filteredPlaylist = combine(_playlist, _selectedFolder, _downloadDir, _sortState) { list, folder, rawDirStr, sort ->
        val folderFiltered = if (folder == "Semua") {
            list
        } else {
            val resolvedPath = AudioStorageHelper.resolvePhysicalPathFromUri(getApplication(), rawDirStr) ?: rawDirStr
            val downloadDir = if (!resolvedPath.isNullOrBlank()) File(resolvedPath) else DownloadService.getDefaultDownloadDirectory()
            list.filter { item ->
                val itemFolder = AudioStorageHelper.getRelativeFolderName(downloadDir, File(item.localFilePath))
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
    private val _currentRjId = MutableStateFlow<String?>(if (isAutoResumeActive) sp.getString("cached_rjid", null) else null)
    val currentRjId = _currentRjId.asStateFlow()

    val currentDisplayBadge = combine(_currentRjId, _playlist) { rj, list ->
        if (rj.isNullOrBlank()) return@combine ""
        val entity = list.firstOrNull { it.rjid == rj }
        if (entity == null) {
            if (rj.startsWith("LOCAL_")) "IMPORT" else rj
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

    // Translation States
    private val _isTranslationActive = MutableStateFlow(false)
    val isTranslationActive = _isTranslationActive.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating = _isTranslating.asStateFlow()

    private val _translatedTitle = MutableStateFlow<String?>(null)
    val translatedTitle = _translatedTitle.asStateFlow()

    private val _translatedChapters = MutableStateFlow<List<ChapterItem>>(emptyList())
    val translatedChapters = _translatedChapters.asStateFlow()

    val activeChapterIndex = combine(_currentPosition, _currentChapters) { pos, chapters ->
        if (chapters.isEmpty()) -1
        else chapters.indexOfLast { pos >= it.startMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    val activeChapter = combine(activeChapterIndex, _currentChapters) { idx, chapters ->
        if (idx in chapters.indices) chapters[idx] else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var isCurrentVirtualAlbum = false
        private set
    var currentAlbumHistory: HistoryEntity? = null
        private set

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    init {
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
            prefs.downloadDirFlow.collect { dir ->
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
            checkAndMigrateTranslationEngine()
            if (!_currentRjId.value.isNullOrBlank()) {
                loadChaptersForCurrentTrack(_currentRjId.value)
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

        // Pindai subfolder fisik yang ada di direktori unduhan
        val subfolders = AudioStorageHelper.getExistingSubfolders(downloadDir)
        val allFolders = (listOf("Semua", "Utama") + subfolders).distinct()
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
                _availableFolders.value = (listOf("Semua", "Utama") + subfolders).distinct()
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

    private fun observeAudioSettings() {
        viewModelScope.launch {
            prefs.defaultSpeedFlow.collect { speed ->
                player?.setPlaybackSpeed(speed)
            }
        }
    }

    private fun observeTranslationPreference() {
        viewModelScope.launch {
            prefs.isTranslationActiveFlow.collect { active ->
                _isTranslationActive.value = active
                if (active && !_currentRjId.value.isNullOrBlank()) {
                    loadOrFetchTranslations(_currentRjId.value)
                }
            }
        }
        viewModelScope.launch {
            prefs.translationLanguageFlow.collect { _ ->
                if (_isTranslationActive.value && !_currentRjId.value.isNullOrBlank()) {
                    loadOrFetchTranslations(_currentRjId.value, forceOnline = true)
                }
            }
        }
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
            _repeatMode.value = p.repeatMode
            _shuffleMode.value = p.shuffleModeEnabled

            p.currentMediaItem?.let { item ->
                val prevRj = _currentRjId.value
                _currentRjId.value = item.mediaId
                if (prevRj != item.mediaId) {
                    loadChaptersForCurrentTrack(item.mediaId)
                }
                item.mediaMetadata.let { meta ->
                    _currentTitle.value = meta.title?.toString() ?: _currentTitle.value
                    _currentArtist.value = meta.artist?.toString() ?: _currentArtist.value
                    val cover = meta.artworkUri?.toString() ?: _currentCoverUrl.value
                    _currentCoverUrl.value = cover
                    preloadCoverArtInMemory(cover)
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

                    if (_currentPosition.value == 0L && lastPos > 0L) {
                        _currentPosition.value = lastPos
                    }
                    _repeatMode.value = prefs.repeatModeFlow.first()
                    _shuffleMode.value = prefs.shuffleModeFlow.first()
                    if (_isTranslationActive.value) {
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
            val customDirStr = prefs.downloadDirFlow.first()
            val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()

            val validItemsWithFiles = fullList.mapNotNull { item ->
                val resolved = AudioStorageHelper.resolveValidAudioFile(downloadDir, item.localFilePath, item.rjid)
                if (resolved != null) Pair(item, resolved) else null
            }

            if (validItemsWithFiles.isEmpty()) return@launch

            val targetPair = validItemsWithFiles.firstOrNull { it.first.rjid == history.rjid } ?: validItemsWithFiles.first()

            val coverBytes = loadArtworkBytes(targetPair.second.absolutePath, targetPair.first.coverUrl)

            if (targetPair.first.sourceType == SourceType.LOCAL_FOLDER.name || targetPair.second.isDirectory) {
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
                    val metaBuilder = MediaMetadata.Builder()
                        .setTitle(trackTitle)
                        .setArtist(targetPair.first.cv)
                        .setAlbumTitle(targetPair.first.title)
                        .setArtworkUri(Uri.parse(targetPair.first.coverUrl ?: ""))
                    if (coverBytes != null) {
                        metaBuilder.setArtworkData(coverBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(tr))
                        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.fromFile(tr)).build())
                        .setMediaId(tr.absolutePath)
                        .setMediaMetadata(metaBuilder.build())
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
                isCurrentVirtualAlbum = false
                currentAlbumHistory = null
                val targetIndex = validItemsWithFiles.indexOf(targetPair).coerceAtLeast(0)
                calculateAudioSpecs(targetPair.second)

                val mediaItems = validItemsWithFiles.map { (item, file) ->
                    val metaBuilder = MediaMetadata.Builder()
                        .setTitle("[${item.rjid}] ${item.title}")
                        .setArtist(item.cv)
                        .setAlbumTitle(item.circle)
                        .setArtworkUri(Uri.parse(item.coverUrl ?: ""))

                    if (coverBytes != null && item.rjid == targetPair.first.rjid) {
                        metaBuilder.setArtworkData(coverBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }

                    MediaItem.Builder()
                        .setUri(Uri.fromFile(file))
                        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.fromFile(file)).build())
                        .setMediaId(item.rjid)
                        .setMediaMetadata(metaBuilder.build())
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
                    if (isCurrentVirtualAlbum) {
                        val album = currentAlbumHistory
                        val curIdx = p.currentMediaItemIndex
                        val curChapter = _currentChapters.value.getOrNull(curIdx)
                        val trackTitle = curChapter?.title ?: mediaItem.mediaMetadata.title?.toString() ?: ""
                        val isLocalAlbum = album?.sourceType != SourceType.DL_SITE.name || album?.rjid?.startsWith("LOCAL_") == true
                        val albumTag = when {
                            isLocalAlbum && !album?.linkedRjId.isNullOrBlank() -> "IMPORT • ${album?.linkedRjId}"
                            isLocalAlbum -> "IMPORT"
                            else -> album?.rjid ?: ""
                        }
                        _currentTitle.value = if (album != null) "[$albumTag] ${album.title} - $trackTitle" else trackTitle
                        val baseMs = curChapter?.startMs ?: 0L
                        _currentPosition.value = (baseMs + p.currentPosition).coerceAtLeast(0L)
                        val totalDur = _currentChapters.value.lastOrNull()?.endMs ?: 0L
                        if (totalDur > 0L) {
                            _duration.value = totalDur
                        }
                        saveCurrentState()
                    } else {
                        val prevRj = _currentRjId.value
                        _currentRjId.value = mediaItem.mediaId
                        if (prevRj != mediaItem.mediaId) {
                            loadChaptersForCurrentTrack(mediaItem.mediaId)
                            if (_isTranslationActive.value) {
                                loadOrFetchTranslations(mediaItem.mediaId)
                            } else {
                                _translatedTitle.value = null
                                _translatedChapters.value = emptyList()
                            }
                        }
                        mediaItem.mediaMetadata.let { meta ->
                            _currentTitle.value = meta.title?.toString() ?: ""
                            _currentArtist.value = meta.artist?.toString() ?: "-"
                            val cover = meta.artworkUri?.toString()
                            _currentCoverUrl.value = cover
                            preloadCoverArtInMemory(cover)
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
                updatePlayingState(p)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlayingState(p)
                if (playbackState == Player.STATE_ENDED) {
                    if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
                        p.seekTo(0, 0L)
                        p.play()
                    } else {
                        playAdjacentWork(forward = true)
                    }
                } else if (playbackState == Player.STATE_READY) {
                    if (isCurrentVirtualAlbum) {
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
                _repeatMode.value = repeatMode
                saveCurrentState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleMode.value = shuffleModeEnabled
                saveCurrentState()
            }
        })

        p.repeatMode = _repeatMode.value
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
        val earlyCover = if (!history.coverUrl.isNullOrBlank() && File(history.coverUrl).exists()) {
            history.coverUrl
        } else {
            val cachedCover = AudioStorageHelper.getLocalCoverFile(getApplication(), history.rjid, history.localFilePath)
            cachedCover?.absolutePath ?: history.coverUrl
        }
        if (!earlyCover.isNullOrBlank()) {
            _currentCoverUrl.value = earlyCover
            preloadCoverArtInMemory(earlyCover)
        }

        viewModelScope.launch {
            val p = player ?: return@launch
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
                isCurrentVirtualAlbum = true
                currentAlbumHistory = history
                val folder = targetFile
                val tracks = withContext(Dispatchers.IO) { AudioStorageHelper.getAudioFilesInFolder(folder) }
                if (tracks.isEmpty()) return@launch

                val customCover = if (!history.coverUrl.isNullOrBlank() && File(history.coverUrl).exists()) history.coverUrl else null
                val localCover = customCover ?: withContext(Dispatchers.IO) {
                    AudioStorageHelper.getLocalCoverFile(getApplication(), history.rjid, targetFile.absolutePath)?.absolutePath
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
                        .setMediaId(tr.absolutePath)
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
                isCurrentVirtualAlbum = false
                currentAlbumHistory = null
                calculateAudioSpecs(targetFile)

                val customCover = if (!history.coverUrl.isNullOrBlank() && File(history.coverUrl).exists()) history.coverUrl else null
                val localCover = customCover ?: withContext(Dispatchers.IO) {
                    AudioStorageHelper.getLocalCoverFile(getApplication(), history.rjid, targetFile.absolutePath)?.absolutePath
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
                if (_isTranslationActive.value) {
                    loadOrFetchTranslations(history.rjid)
                } else {
                    _translatedTitle.value = null
                    _translatedChapters.value = emptyList()
                }

                p.setMediaItems(listOf(singleMediaItem), 0, startPositionMs)
                p.prepare()
                p.play()
                saveCurrentState()
            }
        }
    }

    private suspend fun loadArtworkBytes(localFilePath: String?, coverUrl: String?): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (!localFilePath.isNullOrBlank()) {
                val f = File(localFilePath)
                if (f.exists() && f.isFile) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(f.absolutePath)
                        val pic = retriever.embeddedPicture
                        if (pic != null && pic.isNotEmpty() && pic.size <= 256 * 1024) return@withContext pic
                    } catch (e: Exception) {
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
        }
        return@withContext null
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
                        val history = historyDao.getHistoryById(rj)
                        if (history != null) {
                            val savedPos = _currentPosition.value
                            playLocalTrack(history, _playlist.value, startPositionMs = savedPos)
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
        playAdjacentWork(forward = true)
        saveCurrentState()
    }

    fun playPrevious() {
        val p = player
        if (p != null && p.currentPosition > 3000) {
            p.seekTo(0)
        } else {
            playAdjacentWork(forward = false)
        }
        saveCurrentState()
    }

    private fun playAdjacentWork(forward: Boolean) {
        val list = filteredPlaylist.value.ifEmpty { _playlist.value }
        if (list.isEmpty()) return
        val curId = _currentRjId.value
        val curIdx = list.indexOfFirst { it.rjid == curId }
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
        if (isCurrentVirtualAlbum && _currentChapters.value.isNotEmpty()) {
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
        val nextMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        p.repeatMode = nextMode
        _repeatMode.value = nextMode
        saveCurrentState()
    }

    fun toggleShuffleMode() {
        val p = player ?: return
        val nextState = !p.shuffleModeEnabled
        p.shuffleModeEnabled = nextState
        _shuffleMode.value = nextState
        saveCurrentState()
    }

    fun seekToChapter(chapter: ChapterItem) {
        val p = player ?: return
        if (isCurrentVirtualAlbum && _currentChapters.value.isNotEmpty()) {
            val idx = _currentChapters.value.indexOf(chapter)
            if (idx != -1) {
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
                    val tracks = withContext(Dispatchers.IO) { AudioStorageHelper.getAudioFilesInFolder(folder) }
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
                                    val meta = DLsiteScraper.fetchMetadata(history.linkedRjId)
                                    if (meta != null) {
                                        val updated = history.copy(
                                            title = if (meta.title.isNotBlank()) meta.title else history.title,
                                            cv = if (meta.cv.isNotBlank() && meta.cv != "-") meta.cv else history.cv,
                                            circle = if (meta.circle.isNotBlank() && meta.circle != "-") meta.circle else history.circle
                                        )
                                        historyDao.insertHistory(updated)
                                    }
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

    fun toggleTranslation() {
        val next = !_isTranslationActive.value
        _isTranslationActive.value = next
        viewModelScope.launch {
            prefs.setTranslationActive(next)
            if (next && !_currentRjId.value.isNullOrBlank()) {
                loadOrFetchTranslations(_currentRjId.value)
            } else if (!next) {
                _translatedTitle.value = null
                _translatedChapters.value = emptyList()
            }
        }
    }

    fun loadOrFetchTranslations(targetRjid: String? = _currentRjId.value, forceOnline: Boolean = false) {
        if (targetRjid.isNullOrBlank()) {
            _translatedTitle.value = null
            _translatedChapters.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                val targetLang = prefs.translationLanguageFlow.first()
                val history = withContext(Dispatchers.IO) { historyDao.getHistoryById(targetRjid) }

                // 1. Cek cache DB jika tidak forceOnline
                if (!forceOnline && history != null && !history.translatedTitle.isNullOrBlank()) {
                    _translatedTitle.value = history.translatedTitle
                    if (!history.translatedChaptersJson.isNullOrBlank()) {
                        val parsed = ChapterItem.fromJsonArray(history.translatedChaptersJson)
                        _translatedChapters.value = parsed
                    }
                    return@launch
                }

                _isTranslating.value = true

                // Terjemahkan Judul Karya
                val rawTitle = (history?.title ?: _currentTitle.value)
                    .replace("\\[$targetRjid\\]".toRegex(RegexOption.IGNORE_CASE), "")
                    .trim()

                val translatedTitleRes = withContext(Dispatchers.IO) {
                    TranslationService.translateText(rawTitle, targetLang)
                }
                _translatedTitle.value = translatedTitleRes

                // Terjemahkan Daftar Bab jika sudah ada
                val currentChaps = _currentChapters.value
                val translatedChapsList = if (currentChaps.isNotEmpty()) {
                    translateChaptersBatch(currentChaps, targetLang).also {
                        _translatedChapters.value = it
                    }
                } else emptyList()

                val chapsJson = if (translatedChapsList.isNotEmpty()) {
                    ChapterItem.toJsonArray(translatedChapsList)
                } else history?.translatedChaptersJson

                withContext(Dispatchers.IO) {
                    historyDao.updateTranslations(targetRjid, translatedTitleRes, chapsJson)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isTranslating.value = false
            }
        }
    }

    private suspend fun translateChaptersBatch(
        chapters: List<ChapterItem>,
        targetLang: String
    ): List<ChapterItem> = withContext(Dispatchers.IO) {
        if (chapters.isEmpty()) return@withContext emptyList()
        val titles = chapters.map { it.title }
        val translatedBatch = TranslationService.translateBatch(titles, targetLang)
        chapters.mapIndexed { idx, orig ->
            val trans = if (idx < translatedBatch.size) translatedBatch[idx] else orig.title
            orig.copy(title = trans)
        }
    }

    private fun checkTranslateChaptersIfNeeded(targetRjid: String, chapters: List<ChapterItem>) {
        if (!_isTranslationActive.value || chapters.isEmpty()) return
        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) { historyDao.getHistoryById(targetRjid) }
                if (!history?.translatedChaptersJson.isNullOrBlank()) {
                    val parsed = ChapterItem.fromJsonArray(history.translatedChaptersJson)
                    if (parsed.size == chapters.size) {
                        _translatedChapters.value = parsed
                        return@launch
                    }
                }
                val targetLang = prefs.translationLanguageFlow.first()
                val translatedChapsList = translateChaptersBatch(chapters, targetLang)
                _translatedChapters.value = translatedChapsList
                withContext(Dispatchers.IO) {
                    historyDao.updateTranslations(
                        targetRjid,
                        _translatedTitle.value ?: history?.translatedTitle,
                        ChapterItem.toJsonArray(translatedChapsList)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
                    if (isCurrentVirtualAlbum && _currentChapters.value.isNotEmpty()) {
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
                    saveCacheSynchronously()
                }
                delay(1000)
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
                    DLsiteScraper.fetchMetadata(targetRj)
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
                val updated = existing.copy(coverUrl = newCoverPath)
                historyDao.insertHistory(updated)

                try {
                    val cleanRj = (existing.linkedRjId ?: existing.rjid).uppercase().trim()
                    val coversDir = File(getApplication<Application>().cacheDir, "covers").apply { if (!exists()) mkdirs() }
                    val targetCoverFile = File(coversDir, "$cleanRj.jpg")
                    val src = File(newCoverPath)
                    if (src.exists() && src.isFile && src.canonicalPath != targetCoverFile.canonicalPath) {
                        src.copyTo(targetCoverFile, overwrite = true)
                    }
                    AudioStorageHelper.clearLocalCoverCacheFor(cleanRj)
                    AudioStorageHelper.clearLocalCoverCacheFor(existing.rjid)
                } catch (e: Exception) {}

                val curList = _playlist.value.toMutableList()
                val idx = curList.indexOfFirst { it.rjid == targetRjid }
                if (idx != -1) {
                    curList[idx] = updated
                    _playlist.value = curList
                    saveFastCachedPlaylist(curList)
                }

                withContext(Dispatchers.Main) {
                    if (_currentRjId.value == targetRjid) {
                        _currentCoverUrl.value = newCoverPath
                        preloadCoverArtInMemory(newCoverPath)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        saveCurrentState()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
