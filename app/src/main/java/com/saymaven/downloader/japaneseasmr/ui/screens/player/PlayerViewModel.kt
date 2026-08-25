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
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import com.saymaven.downloader.japaneseasmr.data.remote.TrackDiscoveryService
import com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.service.PlaybackService
import com.saymaven.downloader.japaneseasmr.service.StorageSyncManager
import com.saymaven.downloader.japaneseasmr.service.UsbDacManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val sp = application.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
    private val prefs = PreferencesManager(application)
    private val historyDao = AsmrDatabase.getDatabase(application).historyDao()

    // Playlist: HANYA berisi audio yang benar-benar ada filenya di HP (0 missing tracks)
    private val _playlist = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val playlist = _playlist.asStateFlow()

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

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    init {
        resolveAndPreloadInitialCover()
        initMediaController()
        observeDatabasePlaylist()
        observeSyncTicks()
        restorePlaybackState()
        observeAudioSettings()
        StorageSyncManager.syncStorageWithDatabase(application)
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

    private suspend fun filterAndSetPlaylist(dbList: List<HistoryEntity>) {
        val customDirStr = prefs.downloadDirFlow.first()
        val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()

        // Filter HANYA audio yang file fisiknya benar-benar ada di HP
        val validItems = withContext(Dispatchers.IO) {
            dbList.mapNotNull { item ->
                val direct = if (!item.localFilePath.isNullOrBlank()) File(item.localFilePath) else null
                val validFile = if (direct != null && direct.exists() && direct.length() > 0L) {
                    direct
                } else {
                    AudioStorageHelper.findExistingAudioFile(downloadDir, item.rjid)
                }

                if (validFile != null && validFile.exists() && validFile.length() > 0L) {
                    val localCover = AudioStorageHelper.getLocalCoverFile(getApplication(), item.rjid, validFile.absolutePath)
                    val resolvedCover = localCover?.absolutePath ?: item.coverUrl
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
        } else if (_playlist.value.isNotEmpty()) {
            _playlist.value.mapIndexed { idx, item -> item.rjid to idx }.toMap()
        } else {
            emptyMap()
        }

        val sorted = if (orderMap.isNotEmpty()) {
            validItems.sortedBy { orderMap[it.rjid] ?: Int.MAX_VALUE }
        } else {
            validItems
        }
        _playlist.value = sorted

        sorted.take(20).forEach { item ->
            preloadCoverArtInMemory(item.coverUrl)
        }
    }

    private fun observeAudioSettings() {
        viewModelScope.launch {
            prefs.defaultSpeedFlow.collect { speed ->
                player?.setPlaybackSpeed(speed)
            }
        }
    }

    // Ultra-Fast In-Memory Reorder (0 disk I/O, 0 IPC calls during drag)
    fun reorderPlaylistInMemory(fromIndex: Int, toIndex: Int) {
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
                _currentRjId.value = item.mediaId
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
                viewModelScope.launch {
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
                    _currentTitle.value = "[${history.rjid}] ${history.title}"
                    _currentArtist.value = history.cv
                    _currentCoverUrl.value = finalCover
                    preloadCoverArtInMemory(finalCover)

                    if (_currentPosition.value == 0L && lastPos > 0L) {
                        _currentPosition.value = lastPos
                    }
                    _repeatMode.value = prefs.repeatModeFlow.first()
                    _shuffleMode.value = prefs.shuffleModeFlow.first()

                    val p = player
                    if (p != null && p.mediaItemCount == 0) {
                        prepareTrackSilently(history.copy(coverUrl = finalCover), _playlist.value, _currentPosition.value)
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
            val targetIndex = validItemsWithFiles.indexOf(targetPair).coerceAtLeast(0)

            calculateAudioSpecs(targetPair.second)

            val coverBytes = loadArtworkBytes(targetPair.second.absolutePath, targetPair.first.coverUrl)

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
                    .setMediaId(item.rjid)
                    .setMediaMetadata(metaBuilder.build())
                    .build()
            }

            p.setMediaItems(mediaItems, targetIndex, startPositionMs)
            p.prepare()
            _currentPosition.value = startPositionMs
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
                    _currentRjId.value = mediaItem.mediaId
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
                updatePlayingState(p)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlayingState(p)
                if (playbackState == Player.STATE_READY) {
                    if (p.duration > 0L) {
                        _duration.value = p.duration.coerceAtLeast(0L)
                    }
                    if (p.isPlaying) {
                        _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
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
        viewModelScope.launch {
            val p = player ?: return@launch
            val customDirStr = prefs.downloadDirFlow.first()
            val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()

            val validItemsWithFiles = fullList.mapNotNull { item ->
                val resolved = AudioStorageHelper.resolveValidAudioFile(downloadDir, item.localFilePath, item.rjid)
                if (resolved != null) {
                    if (resolved.absolutePath != item.localFilePath) {
                        historyDao.insertHistory(item.copy(localFilePath = resolved.absolutePath))
                    }
                    Pair(item, resolved)
                } else null
            }

            if (validItemsWithFiles.isEmpty()) return@launch

            val targetPair = validItemsWithFiles.firstOrNull { it.first.rjid == history.rjid } ?: validItemsWithFiles.first()
            val targetIndex = validItemsWithFiles.indexOf(targetPair).coerceAtLeast(0)

            calculateAudioSpecs(targetPair.second)

            val localCover = withContext(Dispatchers.IO) {
                AudioStorageHelper.getLocalCoverFile(getApplication(), targetPair.first.rjid, targetPair.second.absolutePath)
            }
            val finalCover = localCover?.absolutePath ?: targetPair.first.coverUrl

            val coverBytes = loadArtworkBytes(targetPair.second.absolutePath, finalCover)

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
                    .setMediaId(item.rjid)
                    .setMediaMetadata(metaBuilder.build())
                    .build()
            }

            _currentRjId.value = targetPair.first.rjid
            _currentTitle.value = "[${targetPair.first.rjid}] ${targetPair.first.title}"
            _currentArtist.value = targetPair.first.cv
            _currentCoverUrl.value = finalCover
            preloadCoverArtInMemory(finalCover)

            p.setMediaItems(mediaItems, targetIndex, startPositionMs)
            p.prepare()
            p.play()
            saveCurrentState()
        }
    }

    private suspend fun loadArtworkBytes(localFilePath: String?, coverUrl: String?): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (!localFilePath.isNullOrBlank()) {
                val f = File(localFilePath)
                if (f.exists()) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(f.absolutePath)
                        val pic = retriever.embeddedPicture
                        if (pic != null && pic.isNotEmpty()) return@withContext pic
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

    fun streamOnline(rawRjId: String) {
        viewModelScope.launch {
            val meta = DLsiteScraper.fetchMetadata(rawRjId)
            val tracks = TrackDiscoveryService.discoverAllTracks(rawRjId)
            val trackUrl = tracks.firstOrNull()?.url ?: return@launch

            _currentRjId.value = meta.rjid
            val p = player ?: return@launch
            val coverBytes = loadArtworkBytes(null, meta.coverUrl)

            _audioSpecs.value = "44.1kHz | 16bits | 320kbps"

            val metaBuilder = MediaMetadata.Builder()
                .setTitle("[${meta.rjid}] ${meta.title}")
                .setArtist(meta.cv)
                .setAlbumTitle(meta.circle)
                .setArtworkUri(Uri.parse(meta.coverUrl ?: ""))

            if (coverBytes != null) {
                metaBuilder.setArtworkData(coverBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(trackUrl))
                .setMediaId(meta.rjid)
                .setMediaMetadata(metaBuilder.build())
                .build()

            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
            saveCurrentState()
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
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
        val p = player ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
        } else if (p.repeatMode == Player.REPEAT_MODE_ALL && p.mediaItemCount > 0) {
            p.seekToDefaultPosition(0)
        }
        saveCurrentState()
    }

    fun playPrevious() {
        val p = player ?: return
        if (p.currentPosition > 3000) {
            p.seekTo(0)
        } else if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
        } else if (p.repeatMode == Player.REPEAT_MODE_ALL && p.mediaItemCount > 0) {
            p.seekToDefaultPosition(p.mediaItemCount - 1)
        }
        saveCurrentState()
    }

    fun replay10() {
        val p = player ?: return
        val newPos = (p.currentPosition - 10000L).coerceAtLeast(0L)
        p.seekTo(newPos)
        _currentPosition.value = newPos
        saveCurrentState()
    }

    fun forward10() {
        val p = player ?: return
        val newPos = (p.currentPosition + 10000L).coerceAtMost(_duration.value.coerceAtLeast(0L))
        p.seekTo(newPos)
        _currentPosition.value = newPos
        saveCurrentState()
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        p.seekTo(positionMs.coerceIn(0L, _duration.value.coerceAtLeast(0L)))
        _currentPosition.value = positionMs
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
                .commit()
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
                    _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
                    _duration.value = p.duration.coerceAtLeast(0L)
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

    override fun onCleared() {
        sleepTimerJob?.cancel()
        saveCurrentState()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
