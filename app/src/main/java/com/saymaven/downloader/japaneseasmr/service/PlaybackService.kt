package com.saymaven.downloader.japaneseasmr.service

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.service.usb.UsbAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "PlaybackService"
    }

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var isExclusiveFocusActive: Boolean = true

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val prefs = PreferencesManager(this)

        // Restore initial volume immediately
        val initialVol = PreferencesManager.getFastCachedUsbVolume(this)
        NativeAudioPlayer.setVolume(initialVol)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                val defaultSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
                return UsbAudioSink(defaultSink)
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Sync Native C++ Audio Engine with ExoPlayer lifecycle
        setupNativePlayerBridge()

        serviceScope.launch {
            val skipSilence = prefs.skipSilenceFlow.first()
            val defaultSpeed = prefs.defaultSpeedFlow.first()
            val pauseOnUnplug = prefs.pauseOnUnplugFlow.first()
            val exclusiveUsb = prefs.exclusiveUsbDacFlow.first()

            player.skipSilenceEnabled = skipSilence
            player.setPlaybackSpeed(defaultSpeed)
            player.setHandleAudioBecomingNoisy(pauseOnUnplug)

            UsbDacManager.init(this@PlaybackService, exclusiveUsb)
        }

        // Exclusive Audio Focus synchronization
        serviceScope.launch {
            prefs.exclusiveAudioFocusFlow.collect { ignoreFocus ->
                isExclusiveFocusActive = ignoreFocus
                // When ignoreFocus is true, ExoPlayer does not automatically yield to other apps
                player.setAudioAttributes(audioAttributes, !ignoreFocus)
                Log.i(TAG, "Exclusive Audio Focus updated: ignoreFocus=$ignoreFocus")
            }
        }

        // Volume synchronization
        serviceScope.launch {
            combine(UsbDacManager.hardwareVolume, UsbDacManager.dacState) { vol, dacInfo ->
                Pair(vol, dacInfo.isExclusiveActive)
            }.collect { (vol, isExclusive) ->
                if (isExclusive) {
                    // Mute ExoPlayer software stream; Native Audio Engine handles volume smoothly
                    player.volume = 0.0f
                    NativeAudioPlayer.setVolume(vol)
                } else {
                    player.volume = vol
                    NativeAudioPlayer.setVolume(vol)
                }
            }
        }

        // Handle DAC connect/disconnect transitions
        serviceScope.launch {
            UsbDacManager.dacState.collect { dacInfo ->
                if (dacInfo.isExclusiveActive) {
                    player.volume = 0.0f
                    syncCurrentMediaItemToNative(player.currentMediaItem, player.currentPosition, player.playWhenReady)
                } else {
                    NativeAudioPlayer.stop()
                    player.volume = UsbDacManager.hardwareVolume.value
                }
            }
        }

        // Handle native auto-pause when USB DAC is unplugged
        serviceScope.launch {
            NativeAudioPlayer.deviceUnplugged.collect {
                player.pause()
                NativeAudioPlayer.pause()
            }
        }

        // Handle native track completion
        serviceScope.launch {
            NativeAudioPlayer.playbackEnded.collect {
                if (UsbDacManager.isExclusiveActivelyRunning()) {
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                    } else if (player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                        player.seekToDefaultPosition(0)
                    } else {
                        player.pause()
                    }
                }
            }
        }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun setupNativePlayerBridge() {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (UsbDacManager.isExclusiveActivelyRunning()) {
                    syncCurrentMediaItemToNative(mediaItem, 0L, player.playWhenReady)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    if (isExclusiveFocusActive || UsbDacManager.isExclusiveActivelyRunning()) {
                        Log.i(TAG, "Ignoring AUDIO_FOCUS_LOSS because Exclusive mode is active.")
                        player.playWhenReady = true
                        return
                    }
                }
                if (UsbDacManager.isExclusiveActivelyRunning()) {
                    if (playWhenReady) {
                        if (!NativeAudioPlayer.isPlaying()) {
                            syncCurrentMediaItemToNative(player.currentMediaItem, player.currentPosition, true)
                        } else {
                            NativeAudioPlayer.play()
                        }
                    } else {
                        NativeAudioPlayer.pause()
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (UsbDacManager.isExclusiveActivelyRunning()) {
                    NativeAudioPlayer.seekTo(newPosition.positionMs)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "ExoPlayer exception: ${error.message}", error)
                val cause = error.cause
                if (cause is AudioSink.InitializationException || cause is AudioSink.WriteException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            player.setPreferredAudioDevice(null)
                        } catch (ignored: Exception) {}
                    }
                    val curPos = player.currentPosition
                    val curIndex = player.currentMediaItemIndex
                    player.prepare()
                    player.seekTo(curIndex, curPos)
                    if (player.playWhenReady) {
                        player.play()
                    }
                }
            }
        })
    }

    private fun syncCurrentMediaItemToNative(mediaItem: MediaItem?, startPosMs: Long, shouldPlay: Boolean) {
        val uri = mediaItem?.localConfiguration?.uri ?: mediaItem?.requestMetadata?.mediaUri
        val mediaId = mediaItem?.mediaId
        Log.i(TAG, "syncCurrentMediaItemToNative: mediaItem=$mediaId, uri=$uri, localConfig=${mediaItem?.localConfiguration}")
        var path: String? = null
        if (uri != null) {
            if (uri.scheme == "file") {
                path = uri.path
            } else if (uri.scheme == "content") {
                path = AudioStorageHelper.resolvePhysicalPathFromUri(this, uri.toString())
            } else {
                path = uri.path
            }
        }
        if (path.isNullOrBlank() && !mediaId.isNullOrBlank() && (mediaId.startsWith("/") || mediaId.contains("/"))) {
            path = mediaId
        }

        if (!path.isNullOrBlank()) {
            Log.i(TAG, "Opening native file: $path (startPosMs=$startPosMs, shouldPlay=$shouldPlay)")
            val ok = NativeAudioPlayer.openFile(path)
            Log.i(TAG, "NativeAudioPlayer.openFile result: $ok")
            if (ok) {
                if (startPosMs > 0L) {
                    NativeAudioPlayer.seekTo(startPosMs)
                }
                if (shouldPlay) {
                    NativeAudioPlayer.play()
                }
            }
        } else {
            Log.w(TAG, "Cannot sync to native: uri is null or unresolvable ($uri, mediaId=$mediaId)")
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        NativeAudioPlayer.releaseEngine()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
