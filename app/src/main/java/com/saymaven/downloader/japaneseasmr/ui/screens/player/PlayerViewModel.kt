package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
        restorePlaybackState()
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

    private fun restorePlaybackState() {
        viewModelScope.launch {
            val savedRjid = prefs.lastPlayedRjidFlow.first()
            val savedPos = prefs.lastPositionMsFlow.first()
            val savedRepeat = prefs.repeatModeFlow.first()
            val savedShuffle = prefs.shuffleModeFlow.first()

            _repeatMode.value = savedRepeat
            _shuffleMode.value = savedShuffle
            _currentPosition.value = savedPos

            if (!savedRjid.isNullOrBlank()) {
                val history = historyDao.getHistoryById(savedRjid)
                if (history != null) {
                    _currentRjId.value = history.rjid
                    _currentTitle.value = "[${history.rjid}] ${history.title}"
                    _currentArtist.value = history.cv
                    _currentCoverUrl.value = history.coverUrl
                }
            }
        }
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
                    saveCurrentState()
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

        // Terapkan saved repeat/shuffle ke player
        p.repeatMode = _repeatMode.value
        p.shuffleModeEnabled = _shuffleMode.value
    }

    fun playLocalTrack(history: HistoryEntity, fullList: List<HistoryEntity> = playlist.value) {
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

            // Siapkan Artwork Bytes untuk Lockscreen Notification
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
            // 1. Coba baca embedded cover dari file audio lokal
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

            // 2. Jika tidak ada di file lokal, muat via Coil
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

    private fun saveCurrentState() {
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
