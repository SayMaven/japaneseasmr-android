#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"

#include "AudioDecoder.h"
#include <android/log.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <algorithm>
#include <cstring>

#define TAG "AudioDecoder_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

AudioDecoder::AudioDecoder() {}

AudioDecoder::~AudioDecoder() {
    close();
}

bool AudioDecoder::openFile(const std::string& filePath) {
    close();

    // 1. Try miniaudio first (fast, bit-perfect for MP3, FLAC, WAV, OGG)
    if (openWithMiniaudio(filePath)) {
        mType = DecoderType::MINIAUDIO;
        mIsLoaded = true;
        LOGI("Opened with miniaudio: %s (SR=%u, CH=%u, dur=%lld ms)",
             filePath.c_str(), mInfo.sampleRate, mInfo.channels, (long long)mInfo.durationMs);
        return true;
    }

    // 2. Fallback to Android NDK MediaCodec (for M4A, AAC, etc.)
    LOGI("Miniaudio did not accept file, attempting NDK MediaCodec: %s", filePath.c_str());
    if (openWithNdkMediaCodec(filePath)) {
        mType = DecoderType::NDK_MEDIACODEC;
        mIsLoaded = true;
        LOGI("Opened with NDK MediaCodec: %s (SR=%u, CH=%u, dur=%lld ms)",
             filePath.c_str(), mInfo.sampleRate, mInfo.channels, (long long)mInfo.durationMs);
        return true;
    }

    LOGE("Failed to open audio file with any decoder: %s", filePath.c_str());
    return false;
}

bool AudioDecoder::openWithMiniaudio(const std::string& filePath) {
    ma_decoder* dec = new ma_decoder();
    // Decode to stereo float32, keep native sample rate
    ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 2, 0);

    ma_result res = ma_decoder_init_file(filePath.c_str(), &config, dec);
    if (res != MA_SUCCESS) {
        delete dec;
        return false;
    }

    mMaDecoder = dec;
    mInfo.sampleRate = dec->outputSampleRate;
    mInfo.channels = 2;

    ma_uint64 totalFrames = 0;
    if (ma_decoder_get_length_in_pcm_frames(dec, &totalFrames) == MA_SUCCESS) {
        mInfo.totalFrameCount = totalFrames;
        if (mInfo.sampleRate > 0) {
            mInfo.durationMs = static_cast<int64_t>((totalFrames * 1000ULL) / mInfo.sampleRate);
        }
    }

    mCurrentFrame = 0;
    return true;
}

bool AudioDecoder::openWithNdkMediaCodec(const std::string& filePath) {
    AMediaExtractor* extractor = AMediaExtractor_new();
    if (!extractor) return false;

    media_status_t status = AMediaExtractor_setDataSource(extractor, filePath.c_str());
    if (status != AMEDIA_OK) {
        AMediaExtractor_delete(extractor);
        return false;
    }

    int trackCount = AMediaExtractor_getTrackCount(extractor);
    int audioTrackIndex = -1;
    AMediaFormat* audioFormat = nullptr;
    const char* mime = nullptr;

    for (int i = 0; i < trackCount; ++i) {
        AMediaFormat* format = AMediaExtractor_getTrackFormat(extractor, i);
        if (AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            if (strncmp(mime, "audio/", 6) == 0) {
                audioTrackIndex = i;
                audioFormat = format;
                break;
            }
        }
        AMediaFormat_delete(format);
    }

    if (audioTrackIndex < 0 || !audioFormat) {
        AMediaExtractor_delete(extractor);
        return false;
    }

    AMediaExtractor_selectTrack(extractor, audioTrackIndex);

    int32_t sr = 44100;
    int32_t ch = 2;
    int64_t durUs = 0;
    AMediaFormat_getInt32(audioFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sr);
    AMediaFormat_getInt32(audioFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &ch);
    AMediaFormat_getInt64(audioFormat, AMEDIAFORMAT_KEY_DURATION, &durUs);

    AMediaCodec* codec = AMediaCodec_createDecoderByType(mime);
    if (!codec) {
        AMediaFormat_delete(audioFormat);
        AMediaExtractor_delete(extractor);
        return false;
    }

    media_status_t cfgStatus = AMediaCodec_configure(codec, audioFormat, nullptr, nullptr, 0);
    AMediaFormat_delete(audioFormat);

    if (cfgStatus != AMEDIA_OK) {
        AMediaCodec_delete(codec);
        AMediaExtractor_delete(extractor);
        return false;
    }

    if (AMediaCodec_start(codec) != AMEDIA_OK) {
        AMediaCodec_delete(codec);
        AMediaExtractor_delete(extractor);
        return false;
    }

    mNdkExtractor = extractor;
    mNdkCodec = codec;
    mNdkAudioTrackIndex = audioTrackIndex;
    mNdkSawInputEOS = false;
    mNdkSawOutputEOS = false;

    mInfo.sampleRate = static_cast<uint32_t>(sr);
    mInfo.channels = 2; // We normalize output to stereo
    mInfo.durationMs = durUs / 1000;
    if (mInfo.sampleRate > 0) {
        mInfo.totalFrameCount = (mInfo.durationMs * mInfo.sampleRate) / 1000;
    }

    mCurrentFrame = 0;
    return true;
}

uint64_t AudioDecoder::readPcmFrames(float* outputBuffer, uint64_t frameCount) {
    if (!mIsLoaded) return 0;

    if (mType == DecoderType::MINIAUDIO && mMaDecoder) {
        ma_uint64 framesRead = 0;
        ma_result res = ma_decoder_read_pcm_frames(
            static_cast<ma_decoder*>(mMaDecoder),
            outputBuffer,
            frameCount,
            &framesRead
        );
        if (res != MA_SUCCESS && res != MA_AT_END) {
            return 0;
        }
        mCurrentFrame += framesRead;
        return framesRead;
    }

    if (mType == DecoderType::NDK_MEDIACODEC && mNdkCodec && mNdkExtractor) {
        AMediaCodec* codec = static_cast<AMediaCodec*>(mNdkCodec);
        AMediaExtractor* extractor = static_cast<AMediaExtractor*>(mNdkExtractor);

        uint64_t totalFramesOutput = 0;

        while (totalFramesOutput < frameCount && !mNdkSawOutputEOS) {
            // First, drain any buffered decoded samples
            if (mNdkPcmBuffer && mNdkPcmBufferOffset < mNdkPcmBufferSize) {
                size_t availableFrames = (mNdkPcmBufferSize - mNdkPcmBufferOffset) / 2; // stereo
                size_t neededFrames = frameCount - totalFramesOutput;
                size_t framesToCopy = std::min(availableFrames, neededFrames);

                std::memcpy(
                    outputBuffer + (totalFramesOutput * 2),
                    mNdkPcmBuffer + mNdkPcmBufferOffset,
                    framesToCopy * 2 * sizeof(float)
                );

                mNdkPcmBufferOffset += framesToCopy * 2;
                totalFramesOutput += framesToCopy;
                mCurrentFrame += framesToCopy;

                if (totalFramesOutput >= frameCount) {
                    return totalFramesOutput;
                }
            }

            // Feed input to codec
            if (!mNdkSawInputEOS) {
                ssize_t inIdx = AMediaCodec_dequeueInputBuffer(codec, 2000);
                if (inIdx >= 0) {
                    size_t inBufSize = 0;
                    uint8_t* inBuf = AMediaCodec_getInputBuffer(codec, inIdx, &inBufSize);
                    if (inBuf) {
                        ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, inBuf, inBufSize);
                        if (sampleSize < 0) {
                            mNdkSawInputEOS = true;
                            AMediaCodec_queueInputBuffer(codec, inIdx, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            int64_t presentationTimeUs = AMediaExtractor_getSampleTime(extractor);
                            AMediaCodec_queueInputBuffer(codec, inIdx, 0, sampleSize, presentationTimeUs, 0);
                            AMediaExtractor_advance(extractor);
                        }
                    }
                }
            }

            // Drain output from codec
            AMediaCodecBufferInfo info;
            ssize_t outIdx = AMediaCodec_dequeueOutputBuffer(codec, &info, 2000);
            if (outIdx >= 0) {
                if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                    mNdkSawOutputEOS = true;
                }
                if (info.size > 0) {
                    size_t outBufSize = 0;
                    uint8_t* outBuf = AMediaCodec_getOutputBuffer(codec, outIdx, &outBufSize);
                    if (outBuf) {
                        // Assuming 16-bit PCM output from MediaCodec, convert to float32 stereo
                        int16_t* pcm16 = reinterpret_cast<int16_t*>(outBuf + info.offset);
                        size_t sampleCount = info.size / sizeof(int16_t);

                        delete[] mNdkPcmBuffer;
                        mNdkPcmBuffer = new float[sampleCount];
                        mNdkPcmBufferSize = sampleCount;
                        mNdkPcmBufferOffset = 0;

                        for (size_t s = 0; s < sampleCount; ++s) {
                            mNdkPcmBuffer[s] = pcm16[s] / 32768.0f;
                        }
                    }
                }
                AMediaCodec_releaseOutputBuffer(codec, outIdx, false);
            } else if (outIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                AMediaFormat* format = AMediaCodec_getOutputFormat(codec);
                int32_t newSr = 0;
                if (AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &newSr) && newSr > 0) {
                    mInfo.sampleRate = static_cast<uint32_t>(newSr);
                }
                AMediaFormat_delete(format);
            } else if (outIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                if (mNdkSawInputEOS) {
                    break;
                }
            }
        }

        return totalFramesOutput;
    }

    return 0;
}

uint64_t AudioDecoder::readPcmFramesInt16(int16_t* outputBuffer, uint64_t frameCount) {
    if (!mIsLoaded || !outputBuffer || frameCount == 0) return 0;

    constexpr size_t CHUNK_FRAMES = 1024;
    float tempFloat[CHUNK_FRAMES * 2]; // 2 channels stereo

    uint64_t totalFramesRead = 0;
    while (totalFramesRead < frameCount) {
        uint64_t framesToRead = std::min<uint64_t>(frameCount - totalFramesRead, CHUNK_FRAMES);
        uint64_t read = readPcmFrames(tempFloat, framesToRead);
        if (read == 0) break;

        size_t sampleCount = read * 2;
        int16_t* dst = outputBuffer + totalFramesRead * 2;
        for (size_t i = 0; i < sampleCount; ++i) {
            float s = tempFloat[i];
            if (s > 1.0f) s = 1.0f;
            else if (s < -1.0f) s = -1.0f;
            dst[i] = static_cast<int16_t>(s * 32767.0f);
        }

        totalFramesRead += read;
        if (read < framesToRead) break;
    }

    return totalFramesRead;
}

bool AudioDecoder::seekToFrame(uint64_t targetFrame) {
    if (!mIsLoaded) return false;

    if (mType == DecoderType::MINIAUDIO && mMaDecoder) {
        ma_result res = ma_decoder_seek_to_pcm_frame(static_cast<ma_decoder*>(mMaDecoder), targetFrame);
        if (res == MA_SUCCESS) {
            mCurrentFrame = targetFrame;
            return true;
        }
        return false;
    }

    if (mType == DecoderType::NDK_MEDIACODEC && mNdkExtractor && mNdkCodec) {
        int64_t seekTimeUs = 0;
        if (mInfo.sampleRate > 0) {
            seekTimeUs = static_cast<int64_t>((targetFrame * 1000000ULL) / mInfo.sampleRate);
        }
        AMediaExtractor_seekTo(static_cast<AMediaExtractor*>(mNdkExtractor), seekTimeUs, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
        AMediaCodec_flush(static_cast<AMediaCodec*>(mNdkCodec));
        mNdkSawInputEOS = false;
        mNdkSawOutputEOS = false;
        mNdkPcmBufferOffset = 0;
        mNdkPcmBufferSize = 0;
        mCurrentFrame = targetFrame;
        return true;
    }

    return false;
}

bool AudioDecoder::seekToMs(int64_t targetMs) {
    if (!mIsLoaded || mInfo.sampleRate == 0) return false;
    uint64_t targetFrame = (targetMs * mInfo.sampleRate) / 1000ULL;
    return seekToFrame(targetFrame);
}

int64_t AudioDecoder::getCurrentPositionMs() const {
    if (!mIsLoaded.load() || mInfo.sampleRate == 0) return 0;
    return static_cast<int64_t>((mCurrentFrame.load() * 1000ULL) / mInfo.sampleRate);
}

void AudioDecoder::close() {
    if (mMaDecoder) {
        ma_decoder_uninit(static_cast<ma_decoder*>(mMaDecoder));
        delete static_cast<ma_decoder*>(mMaDecoder);
        mMaDecoder = nullptr;
    }

    if (mNdkCodec) {
        AMediaCodec_stop(static_cast<AMediaCodec*>(mNdkCodec));
        AMediaCodec_delete(static_cast<AMediaCodec*>(mNdkCodec));
        mNdkCodec = nullptr;
    }

    if (mNdkExtractor) {
        AMediaExtractor_delete(static_cast<AMediaExtractor*>(mNdkExtractor));
        mNdkExtractor = nullptr;
    }

    delete[] mNdkPcmBuffer;
    mNdkPcmBuffer = nullptr;
    mNdkPcmBufferSize = 0;
    mNdkPcmBufferOffset = 0;

    mType = DecoderType::NONE;
    mIsLoaded = false;
    mCurrentFrame = 0;
    mInfo = AudioFileInfo();
}

