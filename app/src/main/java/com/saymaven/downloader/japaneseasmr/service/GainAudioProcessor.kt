package com.saymaven.downloader.japaneseasmr.service

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Real-time PCM audio processor that applies configurable dB gain (-12 dB to +12 dB)
 * to ExoPlayer's audio pipeline with hard-clipping protection.
 */
@UnstableApi
class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var linearGain: Float = 1.0f

    fun setGainDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(-12.0f, 12.0f)
        linearGain = if (abs(clamped) < 0.01f) {
            1.0f
        } else {
            10.0.pow(clamped / 20.0).toFloat()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val gain = linearGain

        if (gain == 1.0f) {
            outputBuffer.put(inputBuffer)
        } else {
            when (inputAudioFormat.encoding) {
                C.ENCODING_PCM_16BIT -> {
                    while (inputBuffer.remaining() >= 2) {
                        val scaled = (inputBuffer.short * gain).roundToInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        outputBuffer.putShort(scaled.toShort())
                    }
                }
                C.ENCODING_PCM_FLOAT -> {
                    while (inputBuffer.remaining() >= 4) {
                        val scaled = (inputBuffer.float * gain).coerceIn(-1.0f, 1.0f)
                        outputBuffer.putFloat(scaled)
                    }
                }
                else -> outputBuffer.put(inputBuffer)
            }
        }

        outputBuffer.flip()
    }
}
