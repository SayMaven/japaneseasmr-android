package com.saymaven.downloader.japaneseasmr.service.usb

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * UsbAudioSink:
 * AudioSink wrapper that delegates to DefaultAudioSink.
 * ExoPlayer routes audio through the preferred USB Audio device, while UsbAudioEngine
 * controls DAC hardware mixer / registers directly via USB Control Transfers.
 */
@UnstableApi
class UsbAudioSink(
    private val defaultSink: DefaultAudioSink
) : AudioSink by defaultSink
