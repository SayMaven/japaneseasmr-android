#pragma once

#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <pthread.h>
#include <atomic>
#include <cstdint>
#include <functional>
#include <vector>

class AudioDecoder;

class UsbAudioStreamer {
public:
    // Callback to get PCM frames: returns number of frames read
    using ReadCallback = std::function<uint64_t(int16_t* buffer, uint64_t frames)>;

    UsbAudioStreamer();
    ~UsbAudioStreamer();

    bool init(int fd, int acInterface, int asInterface, int epOut, int clockSourceId);
    bool start(uint32_t sampleRate, ReadCallback callback);
    void pause();
    void resume();
    void stop();
    void release();

    bool isRunning() const { return mIsRunning.load(); }
    bool isPaused() const { return mIsPaused.load(); }

private:
    static void* threadFunc(void* context);
    void streamLoop();
    void fillUrb(struct usbdevfs_urb* urb);

    int mFd = -1;
    int mAcInterface = 0;
    int mAsInterface = 1;
    int mEpOut = 0x04;
    int mClockSourceId = 6;
    uint32_t mSampleRate = 44100;

    static constexpr int NUM_URBS = 32;
    static constexpr int PACKETS_PER_URB = 32; // 32 microframes = 4 milliseconds per URB (Total 128ms hardware queue)
    static constexpr int MAX_PACKET_SIZE = 196;
    static constexpr int URB_BUF_SIZE = PACKETS_PER_URB * MAX_PACKET_SIZE;

    struct UrbEntry {
        struct usbdevfs_urb* urb = nullptr;
        uint8_t* buffer = nullptr;
    };

    UrbEntry mUrbs[NUM_URBS];
    pthread_t mThread = 0;
    std::atomic<bool> mIsRunning{false};
    std::atomic<bool> mIsPaused{false};

    ReadCallback mReadCallback;
    uint32_t mSubframeAcc = 0; // Fractional sample accumulator for 44.1kHz
};

