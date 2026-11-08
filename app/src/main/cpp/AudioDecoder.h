#pragma once

#include <string>
#include <cstdint>
#include <atomic>

struct AudioFileInfo {
    uint32_t sampleRate = 0;
    uint32_t channels = 2; // Always 2 (stereo)
    uint64_t totalFrameCount = 0;
    int64_t durationMs = 0;
};

class AudioDecoder {
public:
    AudioDecoder();
    ~AudioDecoder();

    bool openFile(const std::string& filePath);
    void close();

    // Read interleaved float32 PCM frames: [L, R, L, R, ...]
    // Returns the number of frames actually read.
    uint64_t readPcmFrames(float* outputBuffer, uint64_t frameCount);

    // Read interleaved int16 PCM frames: [L, R, L, R, ...]
    // Returns the number of frames actually read.
    uint64_t readPcmFramesInt16(int16_t* outputBuffer, uint64_t frameCount);

    bool seekToFrame(uint64_t targetFrame);
    bool seekToMs(int64_t targetMs);

    AudioFileInfo getInfo() const { return mInfo; }
    uint64_t getCurrentFrame() const { return mCurrentFrame.load(); }
    int64_t getCurrentPositionMs() const;
    bool isLoaded() const { return mIsLoaded.load(); }

private:
    bool openWithMiniaudio(const std::string& filePath);
    bool openWithNdkMediaCodec(const std::string& filePath);

    enum class DecoderType {
        NONE,
        MINIAUDIO,
        NDK_MEDIACODEC
    };

    DecoderType mType = DecoderType::NONE;
    void* mMaDecoder = nullptr; // ma_decoder*

    // NDK MediaCodec state
    void* mNdkExtractor = nullptr; // AMediaExtractor*
    void* mNdkCodec = nullptr;     // AMediaCodec*
    int mNdkAudioTrackIndex = -1;
    bool mNdkSawInputEOS = false;
    bool mNdkSawOutputEOS = false;

    // Temporary PCM buffer for NDK decoded samples
    float* mNdkPcmBuffer = nullptr;
    size_t mNdkPcmBufferSize = 0;
    size_t mNdkPcmBufferOffset = 0;

    AudioFileInfo mInfo;
    std::atomic<uint64_t> mCurrentFrame{0};
    std::atomic<bool> mIsLoaded{false};
};

