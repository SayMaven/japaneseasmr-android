#pragma once

#include <oboe/Oboe.h>
#include "AudioDecoder.h"
#include "UsbAudioStreamer.h"
#include "AudioRingBuffer.h"
#include <memory>
#include <mutex>
#include <atomic>
#include <functional>
#include <thread>
#include <condition_variable>

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    using StateChangeCallback = std::function<void(bool isPlaying, bool isEnded)>;
    using UnplugCallback = std::function<void()>;

    AudioEngine();
    virtual ~AudioEngine();

    bool openFile(const std::string& filePath);
    void play();
    void pause();
    void stop();
    void seekTo(int64_t positionMs);

    void setExclusiveMode(bool exclusive);
    void setPreferredDeviceId(int32_t deviceId);
    void setVolume(float volume);

    bool setUsbDevice(int fd, int acIface, int asIface, int epOut, int clockSourceId);
    void releaseUsbDevice();
    bool isUsbDirectActive() const { return mUsbFd >= 0 && mExclusiveMode.load(); }

    int64_t getCurrentPositionMs();
    int64_t getDurationMs();
    bool isPlaying() const { return mIsPlaying.load(); }
    AudioFileInfo getAudioInfo();

    void setStateChangeCallback(StateChangeCallback cb) { mStateCallback = cb; }
    void setUnplugCallback(UnplugCallback cb) { mUnplugCallback = cb; }

    // Oboe Real-time Thread Callback (SCHED_FIFO priority)
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames
    ) override;

    // Error / Hot-Unplug Callbacks
    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool createStream();
    void closeStream();
    bool startUsbPlayback();

    void startDecoderThread();
    void stopDecoderThread();
    void decoderLoop();

    std::shared_ptr<oboe::AudioStream> mStream;
    AudioDecoder mDecoder;
    std::mutex mDecoderMutex;

    // Lock-Free Ring Buffer (Jitter Buffer ~3.0s capacity)
    AudioRingBuffer mRingBuffer{131072};
    std::thread mDecoderThread;
    std::atomic<bool> mDecoderRunning{false};
    std::atomic<bool> mDecoderEof{false};
    std::condition_variable mDecoderCv;
    std::mutex mDecoderThreadMutex;

    // Direct Kernel USB Streamer for True Exclusive Bit-Perfect Audio
    UsbAudioStreamer mUsbStreamer;
    int mUsbFd = -1;
    int mAcIface = 0;
    int mAsIface = 1;
    int mEpOut = 0x04;
    int mClockSourceId = 6;

    std::atomic<bool> mIsPlaying{false};
    std::atomic<bool> mExclusiveMode{true};
    std::atomic<int32_t> mPreferredDeviceId{oboe::kUnspecified};
    std::atomic<float> mVolume{0.25f};

    int32_t mSampleRate = 44100;
    int32_t mChannelCount = 2;

    StateChangeCallback mStateCallback;
    UnplugCallback mUnplugCallback;
};

