package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import coil.request.SuccessResult
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val sp = application.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
    private val prefs = PreferencesManager(application)
    private val historyDao = AsmrDatabase.getDatabase(application).historyDao()

    private val _playlist = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val playlist = _playlist.asStateFlow()

    val dacState = UsbDacManager.dacState

    val keepScreenOn = prefs.keepScreenOnFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    private var controllerFuture: ListenableFuture<MediaController>? = null
    val player: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    // Fast Cache Restoration at 0ms
    private val _currentRjId = MutableStateFlow<String?>(sp.getString("cached_rjid", null))
    val currentRjId = _currentRjId.asStateFlow()

    private val _currentTitle = MutableStateFlow(sp.getString("cached_title", "Belum ada lagu yang diputar") ?: "Belum ada lagu yang diputar")
    val currentTitle = _currentTitle.asStateFlow()

    private val _currentArtist = MutableStateFlow(sp.getString("cached_artist", "-") ?: "-")
    val currentArtist = _currentArtist.asStateFlow()

    private val _currentCoverUrl = MutableStateFlow<String?>(sp.getString("cached_cover", null))
    val currentCoverUrl = _currentCoverUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(sp.getLong("cached_pos", 0L))
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(sp.getLong("cached_duration", 0L))
    val duration = _duration.asStateFlow()

    private val _repeatMode = MutableStateFlow(sp.getInt("cached_repeat", Player.REPEAT_MODE_OFF))
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffleMode = MutableStateFlow(sp.getBoolean("cached_shuffle", false))
    val shuffleMode = _shuffleMode.asStateFlow()

    // Dynamic Audio Specs (- | - | - by default when no audio is playing)
    private val _audioSpecs = MutableStateFlow("- | - | -")
    val audioSpecs = _audioSpecs.asStateFlow()

    private var progressJob: Job? = null

    init {
        initMediaController()
        observeDatabasePlaylist()
        restorePlaybackState()
        observeAudioSettings()
    }

    private fun observeDatabasePlaylist() {
        viewModelScope.launch {
            historyDao.getAllHistory().collect { dbList ->
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
                    dbList.sortedBy { orderMap[it.rjid] ?: Int.MAX_VALUE }
                } else {
                    dbList
                }
                _playlist.value = sorted
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

    private fun saveCustomPlaylistOrder(rjidList: List<String>) {
        val str = rjidList.joinToString(",")
        sp.edit().putString("custom_playlist_order", str).apply()
    }

    fun reorderPlaylist(fromIndex: Int, toIndex: Int) {
        val current = _playlist.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _playlist.value = current
            saveCustomPlaylistOrder(current.map { it.rjid })

            player?.let { p ->
                if (p.mediaItemCount > 0 && fromIndex < p.mediaItemCount && toIndex < p.mediaItemCount) {
                    p.moveMediaItem(fromIndex, toIndex)
                }
            }
        }
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
        _isPlaying.value = p.isPlaying
        _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
        _duration.value = p.duration.coerceAtLeast(0L)
        _repeatMode.value = p.repeatMode
        _shuffleMode.value = p.shuffleModeEnabled

        p.currentMediaItem?.let { item ->
            _currentRjId.value = item.mediaId
            item.mediaMetadata.let { meta ->
                _currentTitle.value = meta.title?.toString() ?: _currentTitle.value
                _currentArtist.value = meta.artist?.toString() ?: _currentArtist.value
                _currentCoverUrl.value = meta.artworkUri?.toString() ?: _currentCoverUrl.value
            }
        }

        if (p.isPlaying) {
            startProgressTracking()
        }
    }

    private fun restorePlaybackState() {
        viewModelScope.launch {
            val autoResume = prefs.autoResumeFlow.first()
            if (!autoResume) return@launch

            val lastRj = prefs.lastPlayedRjidFlow.first()
            if (!lastRj.isNullOrBlank() && _currentRjId.value == null) {
                _currentRjId.value = lastRj
                _currentPosition.value = prefs.lastPositionMsFlow.first()
                _repeatMode.value = prefs.repeatModeFlow.first()
                _shuffleMode.value = prefs.shuffleModeFlow.first()
            }
        }
    }

    private fun setupPlayerListener(p: Player) {
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
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
                        _currentCoverUrl.value = meta.artworkUri?.toString()
                    }
                    _currentPosition.value = 0L
                    _duration.value = p.duration.coerceAtLeast(0L)
                    saveCurrentState()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = p.duration.coerceAtLeast(0L)
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
                _audioSpecs.value = "$sampleRateKhz | ${bitDepth}bits | ${bitrateKbps}kbps"
            } catch (e: Exception) {
                _audioSpecs.value = "44.1kHz | 16bits | 1064kbps"
            }
        }
    }

    fun playLocalTrack(history: HistoryEntity, fullList: List<HistoryEntity> = _playlist.value) {
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

            val coverBytes = loadArtworkBytes(targetPair.second.absolutePath, targetPair.first.coverUrl)

            val mediaItems = validItemsWithFiles.map { (item, file) ->
                val metaBuilder = MediaMetadata.Builder()
                    .setTitle("[${item.rjid}] ${item.title}")
                    .setArtist(item.cv)
                    .setAlbumTitle(item.circle)
                    .setArtworkUri(Uri.parse(item.coverUrl))

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
            _currentCoverUrl.value = targetPair.first.coverUrl

            p.setMediaItems(mediaItems, targetIndex, 0L)
            p.prepare()
            p.play()
            saveCurrentState()
        }
    }

    private suspend fun loadArtworkBytes(localFilePath: String?, coverUrl: String?): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (localFilePath != null) {
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

            if (!coverUrl.isNullOrBlank()) {
                val req = ImageRequest.Builder(getApplication())
                    .data(coverUrl)
                    .allowHardware(false)
                    .build()
                val result = (Coil.imageLoader(getApplication()).execute(req) as? SuccessResult)?.drawable
                if (result is BitmapDrawable) {
                    val stream = ByteArrayOutputStream()
                    result.bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    return@withContext stream.toByteArray()
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
                .setArtworkUri(Uri.parse(meta.coverUrl))

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
                        if (history != null) playLocalTrack(history)
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
        sp.edit()
            .putString("cached_rjid", _currentRjId.value)
            .putString("cached_title", _currentTitle.value)
            .putString("cached_artist", _currentArtist.value)
            .putString("cached_cover", _currentCoverUrl.value)
            .putLong("cached_pos", _currentPosition.value)
            .putLong("cached_duration", _duration.value)
            .putInt("cached_repeat", _repeatMode.value)
            .putBoolean("cached_shuffle", _shuffleMode.value)
            .apply()
    }

    private fun saveCurrentState() {
        saveCacheSynchronously()
        viewModelScope.launch {
            prefs.savePlaybackState(
                rjid = _currentRjId.value,
                positionMs = _currentPosition.value,
                repeatMode = _repeatMode.value,
                shuffleMode = _shuffleMode.value
            )
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                player?.let { p ->
                    _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
                    _duration.value = p.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        saveCurrentState()
    }

    override fun onCleared() {
        saveCurrentState()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
