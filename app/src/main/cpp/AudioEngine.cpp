#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <errno.h>
#include <sys/resource.h>
#include <chrono>

#define TAG "AudioEngine_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {}

AudioEngine::~AudioEngine() {
    stopDecoderThread();
    releaseUsbDevice();
    closeStream();
}

bool AudioEngine::openFile(const std::string& filePath) {
    stopDecoderThread();

    std::lock_guard<std::mutex> lock(mDecoderMutex);
    bool ok = mDecoder.openFile(filePath);
    if (!ok) return false;

    AudioFileInfo info = mDecoder.getInfo();
    mSampleRate = info.sampleRate > 0 ? info.sampleRate : 44100;
    mChannelCount = 2;

    startDecoderThread();

    if (isUsbDirectActive()) {
        closeStream();
        LOGI("Opened file for True Exclusive USB Streaming: %s (SR=%d)", filePath.c_str(), mSampleRate);
        return true;
    } else {
        return createStream();
    }
}

void AudioEngine::startDecoderThread() {
    stopDecoderThread();
    mDecoderRunning.store(true);
    mDecoderEof.store(false);
    mRingBuffer.reset();
    mDecoderThread = std::thread(&AudioEngine::decoderLoop, this);
}

void AudioEngine::stopDecoderThread() {
    mDecoderRunning.store(false);
    mDecoderCv.notify_all();
    if (mDecoderThread.joinable()) {
        mDecoderThread.join();
    }
}

void AudioEngine::decoderLoop() {
    // High priority audio decoder worker thread (higher priority than normal UI apps)
    setpriority(PRIO_PROCESS, 0, -16);

    constexpr size_t CHUNK_FRAMES = 4096;
    int16_t tempChunk[CHUNK_FRAMES * 2];

    while (mDecoderRunning.load()) {
        if (!mIsPlaying.load() || mDecoderEof.load()) {
            std::unique_lock<std::mutex> lock(mDecoderThreadMutex);
            mDecoderCv.wait_for(lock, std::chrono::milliseconds(25), [this]() {
                return !mDecoderRunning.load() || (mIsPlaying.load() && !mDecoderEof.load() && mRingBuffer.getAvailableWriteFrames() >= CHUNK_FRAMES);
            });
            continue;
        }

        size_t availableWrite = mRingBuffer.getAvailableWriteFrames();
        if (availableWrite < CHUNK_FRAMES) {
            std::unique_lock<std::mutex> lock(mDecoderThreadMutex);
            mDecoderCv.wait_for(lock, std::chrono::milliseconds(15), [this]() {
                return !mDecoderRunning.load() || mRingBuffer.getAvailableWriteFrames() >= CHUNK_FRAMES;
            });
            continue;
        }

        uint64_t framesRead = 0;
        {
            std::lock_guard<std::mutex> lock(mDecoderMutex);
            if (!mDecoder.isLoaded()) {
                mDecoderEof.store(true);
                continue;
            }
            framesRead = mDecoder.readPcmFramesInt16(tempChunk, CHUNK_FRAMES);
        }

        if (framesRead > 0) {
            mRingBuffer.write(tempChunk, framesRead);
        }

        if (framesRead < CHUNK_FRAMES) {
            mDecoderEof.store(true);
        }
    }
}

bool AudioEngine::createStream() {
    closeStream();

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(mChannelCount)
           ->setSampleRate(mSampleRate)
           ->setDataCallback(this)
           ->setErrorCallback(this);

    if (mExclusiveMode.load()) {
        builder.setSharingMode(oboe::SharingMode::Exclusive);
    } else {
        builder.setSharingMode(oboe::SharingMode::Shared);
    }

    if (mPreferredDeviceId.load() != oboe::kUnspecified) {
        builder.setDeviceId(mPreferredDeviceId.load());
    }

    oboe::Result res = builder.openStream(mStream);
    if (res != oboe::Result::OK) {
        LOGW("Failed opening Oboe stream (%s). Trying fallback to Shared mode...", oboe::convertToText(res));
        builder.setSharingMode(oboe::SharingMode::Shared);
        res = builder.openStream(mStream);
        if (res != oboe::Result::OK) {
            LOGE("Failed opening Oboe stream in fallback Shared mode: %s", oboe::convertToText(res));
            mStream.reset();
            return false;
        }
    }

    if (!mStream) {
        LOGE("mStream is null after openStream");
        return false;
    }

    LOGI("Audio Stream successfully created! SampleRate: %d, Format: Float, SharingMode: %s, DeviceId: %d",
         mStream->getSampleRate(),
         mStream->getSharingMode() == oboe::SharingMode::Exclusive ? "EXCLUSIVE (Direct MMAP)" : "SHARED",
         mStream->getDeviceId());

    return true;
}

bool AudioEngine::setUsbDevice(int fd, int acIface, int asIface, int epOut, int clockSourceId) {
    LOGI("AudioEngine::setUsbDevice fd=%d, ac=%d, as=%d, ep=0x%02X, cs=%d", fd, acIface, asIface, epOut, clockSourceId);
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    mUsbFd = fd;
    mAcIface = acIface;
    mAsIface = asIface;
    mEpOut = epOut;
    mClockSourceId = clockSourceId;

    if (mUsbFd >= 0) {
        closeStream();

        // 1. Detach kernel snd-usb-audio driver from all 3 interfaces of the DAC
        for (int i = 0; i < 3; i++) {
            struct usbdevfs_ioctl oper;
            oper.ifno = i;
            oper.ioctl_code = USBDEVFS_DISCONNECT;
            oper.data = nullptr;
            int retDis = ioctl(mUsbFd, USBDEVFS_IOCTL, &oper);
            LOGI("USBDEVFS_DISCONNECT iface %d result: %d (errno %d)", i, retDis, errno);
        }

        // 2. Claim all interfaces
        for (int i = 0; i < 3; i++) {
            int ifaceNum = i;
            int retClaim = ioctl(mUsbFd, USBDEVFS_CLAIMINTERFACE, &ifaceNum);
            LOGI("USBDEVFS_CLAIMINTERFACE iface %d result: %d (errno %d)", i, retClaim, errno);
        }

        bool initOk = mUsbStreamer.init(mUsbFd, mAcIface, mAsIface, mEpOut, mClockSourceId);
        if (!initOk) {
            LOGE("Failed to init UsbAudioStreamer!");
            return false;
        }

        if (mDecoder.isLoaded() && mIsPlaying.load()) {
            startUsbPlayback();
        }
        return true;
    } else {
        releaseUsbDevice();
        return false;
    }
}

void AudioEngine::releaseUsbDevice() {
    LOGI("AudioEngine::releaseUsbDevice");
    std::lock_guard<std::mutex> lock(mDecoderMutex);
    mUsbStreamer.stop();
    mUsbStreamer.release();

    if (mUsbFd >= 0) {
        for (int i = 0; i < 3; i++) {
            int ifaceNum = i;
            ioctl(mUsbFd, USBDEVFS_RELEASEINTERFACE, &ifaceNum);
            struct usbdevfs_ioctl oper;
            oper.ifno = i;
            oper.ioctl_code = USBDEVFS_CONNECT;
            oper.data = nullptr;
            ioctl(mUsbFd, USBDEVFS_IOCTL, &oper);
        }
        mUsbFd = -1;
    }
}

bool AudioEngine::startUsbPlayback() {
    if (mUsbFd < 0) return false;

    return mUsbStreamer.start(mSampleRate, [this](int16_t* buffer, uint64_t frames) -> uint64_t {
        if (!mIsPlaying.load()) {
            return 0;
        }

        // Read pre-decoded frames directly from Lock-Free Ring Buffer (0 disk I/O, 0 mutex)
        uint64_t framesRead = mRingBuffer.read(buffer, frames);

        // Apply volume scaling with smooth quadratic acoustic curve
        float vol = mVolume.load();
        if (vol < 0.999f) {
            float gain = vol * vol;
            size_t totalSamples = framesRead * mChannelCount;
            for (size_t i = 0; i < totalSamples; ++i) {
                float sample = buffer[i] * gain;
                if (sample > 32767.0f) sample = 32767.0f;
                else if (sample < -32768.0f) sample = -32768.0f;
                buffer[i] = static_cast<int16_t>(sample);
            }
        }

        // Wake up decoder worker thread to refill buffer ahead of time
        if (mRingBuffer.getAvailableWriteFrames() >= 4096) {
            mDecoderCv.notify_one();
        }

        if (framesRead < frames && mDecoderEof.load() && mRingBuffer.getAvailableReadFrames() == 0) {
            mIsPlaying.store(false);
            if (mStateCallback) {
                mStateCallback(false, true);
            }
        }

        return framesRead;
    });
}

void AudioEngine::play() {
    mIsPlaying.store(true);
    mDecoderCv.notify_all();

    if (isUsbDirectActive()) {
        if (!mUsbStreamer.isRunning()) {
            startUsbPlayback();
        } else if (mUsbStreamer.isPaused()) {
            mUsbStreamer.resume();
        }
    } else {
        if (mStream && mStream->getState() != oboe::StreamState::Started) {
            mStream->requestStart();
        }
    }

    if (mStateCallback) {
        mStateCallback(true, false);
    }
}

void AudioEngine::pause() {
    mIsPlaying.store(false);

    if (isUsbDirectActive()) {
        mUsbStreamer.pause();
    } else {
        if (mStream && mStream->getState() == oboe::StreamState::Started) {
            mStream->requestPause();
        }
    }

    if (mStateCallback) {
        mStateCallback(false, false);
    }
}

void AudioEngine::stop() {
    mIsPlaying.store(false);

    if (isUsbDirectActive()) {
        mUsbStreamer.stop();
    } else {
        if (mStream) {
            mStream->requestStop();
        }
    }

    {
        std::lock_guard<std::mutex> lock(mDecoderMutex);
        mDecoder.seekToFrame(0);
    }
    mRingBuffer.reset();
    mDecoderEof.store(false);

    if (mStateCallback) {
        mStateCallback(false, false);
    }
}

void AudioEngine::seekTo(int64_t positionMs) {
    {
        std::lock_guard<std::mutex> lock(mDecoderMutex);
        mDecoder.seekToMs(positionMs);
    }
    mRingBuffer.reset();
    mDecoderEof.store(false);
    mDecoderCv.notify_all();
}

int64_t AudioEngine::getCurrentPositionMs() {
    // Lock-free atomic read
    return mDecoder.getCurrentPositionMs();
}

int64_t AudioEngine::getDurationMs() {
    return mDecoder.getInfo().durationMs;
}

AudioFileInfo AudioEngine::getAudioInfo() {
    return mDecoder.getInfo();
}

void AudioEngine::setExclusiveMode(bool exclusive) {
    if (mExclusiveMode.load() != exclusive) {
        mExclusiveMode.store(exclusive);
        if (mDecoder.isLoaded()) {
            int64_t pos = getCurrentPositionMs();
            bool wasPlaying = isPlaying();

            if (isUsbDirectActive()) {
                closeStream();
                seekTo(pos);
                if (wasPlaying) play();
            } else {
                mUsbStreamer.stop();
                createStream();
                seekTo(pos);
                if (wasPlaying) play();
            }
        }
    }
}

void AudioEngine::setPreferredDeviceId(int32_t deviceId) {
    if (mPreferredDeviceId.load() != deviceId) {
        mPreferredDeviceId.store(deviceId);
        if (mDecoder.isLoaded() && !isUsbDirectActive()) {
            int64_t pos = getCurrentPositionMs();
            bool wasPlaying = isPlaying();
            createStream();
            seekTo(pos);
            if (wasPlaying) play();
        }
    }
}

void AudioEngine::setVolume(float volume) {
    mVolume.store(std::max(0.0f, std::min(1.0f, volume)));
}

void AudioEngine::closeStream() {
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }
}

// --------------------------------------------------------------------------
// Real-time Audio Callback for Oboe (Fallback / Non-USB)
// --------------------------------------------------------------------------
oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames
) {
    float *floatBuffer = static_cast<float *>(audioData);

    if (!mIsPlaying.load()) {
        std::fill_n(floatBuffer, numFrames * mChannelCount, 0.0f);
        return oboe::DataCallbackResult::Continue;
    }

    constexpr size_t TEMP_CHUNK = 2048;
    int16_t tempInt16[TEMP_CHUNK * 2];
    size_t framesToRead = std::min<size_t>(numFrames, TEMP_CHUNK);
    uint64_t framesRead = mRingBuffer.read(tempInt16, framesToRead);

    float vol = mVolume.load();
    float gain = (vol < 0.999f) ? (vol * vol) : 1.0f;

    for (size_t i = 0; i < framesRead * mChannelCount; ++i) {
        floatBuffer[i] = (tempInt16[i] / 32768.0f) * gain;
    }

    if (framesRead < static_cast<uint64_t>(numFrames)) {
        std::fill_n(
            floatBuffer + (framesRead * mChannelCount),
            (numFrames - framesRead) * mChannelCount,
            0.0f
        );

        if (framesRead == 0 && mDecoderEof.load()) {
            mIsPlaying.store(false);
            if (mStateCallback) {
                mStateCallback(false, true);
            }
            return oboe::DataCallbackResult::Stop;
        }
    }

    if (mRingBuffer.getAvailableWriteFrames() >= 4096) {
        mDecoderCv.notify_one();
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGW("Oboe Error detected: %s. Safely closing stream...", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGI("USB DAC or audio route disconnected. Pausing playback to prevent speaker leakage...");
    mIsPlaying.store(false);
    mPreferredDeviceId.store(oboe::kUnspecified);

    if (mUnplugCallback) {
        mUnplugCallback();
    }
    if (mStateCallback) {
        mStateCallback(false, false);
    }
}
