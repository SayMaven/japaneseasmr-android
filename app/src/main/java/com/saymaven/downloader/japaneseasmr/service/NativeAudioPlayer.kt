package com.saymaven.downloader.japaneseasmr.service

import androidx.annotation.Keep
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Keep
object NativeAudioPlayer {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val playbackEnded = _playbackEnded.asSharedFlow()

    private val _deviceUnplugged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deviceUnplugged = _deviceUnplugged.asSharedFlow()

    init {
        try {
            System.loadLibrary("native-audio-engine")
            initEngine()
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    private external fun initEngine()
    external fun openFile(filePath: String): Boolean
    external fun play()
    external fun pause()
    external fun stop()
    external fun seekTo(positionMs: Long)
    external fun getPositionMs(): Long
    external fun getDurationMs(): Long
    external fun isPlaying(): Boolean
    external fun setExclusiveMode(exclusive: Boolean)
    external fun setPreferredDeviceId(deviceId: Int)
    external fun setVolume(volume: Float)
    external fun getSampleRate(): Int
    external fun releaseEngine()
    external fun testUsbClaim(fd: Int, iface: Int): Int
    external fun testUsbRelease(fd: Int, iface: Int): Int
    external fun setUsbStreamerDevice(fd: Int, acIface: Int, asIface: Int, epOut: Int, clockSourceId: Int): Boolean
    external fun releaseUsbStreamerDevice()

    /**
     * JNI Callback from C++ thread when playback state changes.
     */
    @Keep
    fun onNativeStateChanged(isPlaying: Boolean, isEnded: Boolean) {
        _isPlaying.value = isPlaying
        if (isEnded) {
            _playbackEnded.tryEmit(Unit)
        }
    }

    /**
     * JNI Callback from C++ thread when USB DAC is unplugged.
     */
    @Keep
    fun onNativeDeviceUnplugged() {
        _isPlaying.value = false
        _deviceUnplugged.tryEmit(Unit)
    }
}

