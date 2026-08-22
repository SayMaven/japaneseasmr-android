package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val historyDao = AsmrDatabase.getDatabase(application).historyDao()
    val playlist = historyDao.getAllHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    val player: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private val _currentRjId = MutableStateFlow<String?>(null)
    val currentRjId = _currentRjId.asStateFlow()

    private val _currentTitle = MutableStateFlow("Belum ada lagu yang diputar")
    val currentTitle = _currentTitle.asStateFlow()

    private val _currentArtist = MutableStateFlow("-")
    val currentArtist = _currentArtist.asStateFlow()

    private val _currentCoverUrl = MutableStateFlow<String?>(null)
    val currentCoverUrl = _currentCoverUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode = _shuffleMode.asStateFlow()

    private var progressJob: Job? = null

    init {
        initMediaController()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            setupPlayerListener()
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val p = player ?: return
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startProgressTracking() else stopProgressTracking()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = p.duration.coerceAtLeast(0L)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    _currentRjId.value = mediaItem.mediaId
                    _currentTitle.value = mediaItem.mediaMetadata.title?.toString() ?: "JapaneseASMR"
                    _currentArtist.value = mediaItem.mediaMetadata.artist?.toString() ?: "-"
                    _currentCoverUrl.value = mediaItem.mediaMetadata.artworkUri?.toString()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleMode.value = shuffleModeEnabled
            }
        })
    }

    fun playLocalTrack(history: HistoryEntity, fullList: List<HistoryEntity> = playlist.value) {
        viewModelScope.launch {
            val p = player ?: return@launch
            val customDirStr = prefs.downloadDirFlow.first()
            val downloadDir = if (!customDirStr.isNullOrBlank()) File(customDirStr) else DownloadService.getDefaultDownloadDirectory()

            val validItemsWithFiles = fullList.mapNotNull { item ->
                val resolved = AudioStorageHelper.resolveValidAudioFile(downloadDir, item.localFilePath, item.rjid)
                if (resolved != null) {
                    // Update database jika path lokal berubah (misal dari RJxxxxxx ke [RJxxxxxx] Judul)
                    if (resolved.absolutePath != item.localFilePath) {
                        historyDao.insertHistory(item.copy(localFilePath = resolved.absolutePath))
                    }
                    Pair(item, resolved)
                } else null
            }

            if (validItemsWithFiles.isEmpty()) return@launch

            val targetPair = validItemsWithFiles.firstOrNull { it.first.rjid == history.rjid } ?: validItemsWithFiles.first()
            val targetIndex = validItemsWithFiles.indexOf(targetPair).coerceAtLeast(0)

            val mediaItems = validItemsWithFiles.map { (item, file) ->
                val metadata = MediaMetadata.Builder()
                    .setTitle("[${item.rjid}] ${item.title}")
                    .setArtist(item.cv)
                    .setAlbumTitle(item.circle)
                    .setArtworkUri(Uri.parse(item.coverUrl))
                    .build()

                MediaItem.Builder()
                    .setUri(Uri.fromFile(file))
                    .setMediaId(item.rjid)
                    .setMediaMetadata(metadata)
                    .build()
            }

            _currentRjId.value = targetPair.first.rjid
            _currentTitle.value = "[${targetPair.first.rjid}] ${targetPair.first.title}"
            _currentArtist.value = targetPair.first.cv
            _currentCoverUrl.value = targetPair.first.coverUrl

            p.setMediaItems(mediaItems, targetIndex, 0L)
            p.prepare()
            p.play()
        }
    }

    fun streamOnline(rawRjId: String) {
        viewModelScope.launch {
            val meta = DLsiteScraper.fetchMetadata(rawRjId)
            val tracks = TrackDiscoveryService.discoverAllTracks(rawRjId)
            val trackUrl = tracks.firstOrNull()?.url ?: return@launch

            _currentRjId.value = meta.rjid
            val p = player ?: return@launch
            val metadata = MediaMetadata.Builder()
                .setTitle("[${meta.rjid}] ${meta.title}")
                .setArtist(meta.cv)
                .setAlbumTitle(meta.circle)
                .setArtworkUri(Uri.parse(meta.coverUrl))
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(trackUrl))
                .setMediaId(meta.rjid)
                .setMediaMetadata(metadata)
                .build()

            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    fun playNext() {
        val p = player ?: return
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
        } else if (p.repeatMode == Player.REPEAT_MODE_ALL && p.mediaItemCount > 0) {
            p.seekToDefaultPosition(0)
        }
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
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        p.seekTo(positionMs.coerceIn(0L, _duration.value.coerceAtLeast(0L)))
        _currentPosition.value = positionMs
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
    }

    fun toggleShuffleMode() {
        val p = player ?: return
        val nextState = !p.shuffleModeEnabled
        p.shuffleModeEnabled = nextState
        _shuffleMode.value = nextState
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
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
