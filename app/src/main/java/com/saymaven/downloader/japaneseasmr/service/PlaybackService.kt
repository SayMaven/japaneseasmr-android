package com.saymaven.downloader.japaneseasmr.service

import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val prefs = PreferencesManager(this)

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

        // Synchronize hardware volume with player volume
        serviceScope.launch {
            UsbDacManager.hardwareVolume.collect { vol ->
                player.volume = vol
            }
        }

        // Listen to USB DAC state and route preferred audio device
        serviceScope.launch {
            UsbDacManager.dacState.collect { dacInfo ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (dacInfo.audioDeviceInfo != null) {
                        try {
                            player.setPreferredAudioDevice(dacInfo.audioDeviceInfo)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        try {
                            player.setPreferredAudioDevice(null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
