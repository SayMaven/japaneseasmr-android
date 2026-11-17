#include "UsbAudioStreamer.h"
#include <android/log.h>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <errno.h>
#include <sys/resource.h>
#include <poll.h>

#define TAG "UsbAudioStreamer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

UsbAudioStreamer::UsbAudioStreamer() {}

UsbAudioStreamer::~UsbAudioStreamer() {
    release();
}

bool UsbAudioStreamer::init(int fd, int acInterface, int asInterface, int epOut, int clockSourceId) {
    release();

    mFd = fd;
    mAcInterface = acInterface;
    mAsInterface = asInterface;
    mEpOut = epOut;
    mClockSourceId = clockSourceId;

    size_t urbMemSize = sizeof(struct usbdevfs_urb) + PACKETS_PER_URB * sizeof(struct usbdevfs_iso_packet_desc);

    for (int i = 0; i < NUM_URBS; i++) {
        mUrbs[i].urb = (struct usbdevfs_urb*)calloc(1, urbMemSize);
        mUrbs[i].buffer = (uint8_t*)calloc(1, URB_BUF_SIZE);
        if (!mUrbs[i].urb || !mUrbs[i].buffer) {
            LOGE("Failed to allocate URB memory for index %d", i);
            release();
            return false;
        }
    }

    LOGI("UsbAudioStreamer initialized with fd=%d, ac=%d, as=%d, ep=0x%02X, cs=%d",
         mFd, mAcInterface, mAsInterface, mEpOut, mClockSourceId);
    return true;
}

bool UsbAudioStreamer::start(uint32_t sampleRate, ReadCallback callback) {
    if (mFd < 0 || !callback) return false;

    stop();

    mSampleRate = sampleRate;
    mReadCallback = callback;
    mSubframeAcc = 0;

    // 1. Configure Clock Source Frequency via UAC2 Control Transfer
    uint32_t freq = sampleRate;
    struct usbdevfs_ctrltransfer ctrl;
    ctrl.bRequestType = 0x21; // CS request to Interface OUT
    ctrl.bRequest = 0x01;     // UAC2_CS_REQ_CUR
    ctrl.wValue = (0x01 << 8) | 0x00; // CS_SAM_FREQ_CONTROL
    ctrl.wIndex = (mClockSourceId << 8) | (mAcInterface & 0xFF);
    ctrl.wLength = 4;
    ctrl.timeout = 500;
    ctrl.data = &freq;

    int retCtrl = ioctl(mFd, USBDEVFS_CONTROL, &ctrl);
    LOGI("Set Clock Source %d sampling rate to %u Hz result: %d", mClockSourceId, sampleRate, retCtrl);

    // If UAC2 clock control failed, try UAC1 endpoint sampling frequency control
    if (retCtrl < 0) {
        uint8_t uac1Rate[3];
        uac1Rate[0] = static_cast<uint8_t>(sampleRate & 0xFF);
        uac1Rate[1] = static_cast<uint8_t>((sampleRate >> 8) & 0xFF);
        uac1Rate[2] = static_cast<uint8_t>((sampleRate >> 16) & 0xFF);

        struct usbdevfs_ctrltransfer ctrl1;
        ctrl1.bRequestType = 0x22; // Class Endpoint OUT
        ctrl1.bRequest = 0x01;     // SET_CUR
        ctrl1.wValue = 0x0100;     // SAMPLING_FREQ_CONTROL
        ctrl1.wIndex = mEpOut;     // Target is the audio streaming endpoint
        ctrl1.wLength = 3;
        ctrl1.timeout = 500;
        ctrl1.data = uac1Rate;

        int retUac1 = ioctl(mFd, USBDEVFS_CONTROL, &ctrl1);
        LOGI("Set UAC1 Endpoint 0x%02X sampling rate to %u Hz result: %d", mEpOut, sampleRate, retUac1);
    }

    // 2. Set Alternate Setting 1 (16-bit PCM Stereo AudioStreaming)
    struct usbdevfs_setinterface setintf;
    setintf.interface = mAsInterface;
    setintf.altsetting = 1;
    int retAlt = ioctl(mFd, USBDEVFS_SETINTERFACE, &setintf);
    LOGI("Set AS Interface %d to Alt 1 result: %d", mAsInterface, retAlt);

    // 3. Prepare and submit initial URBs
    mIsRunning.store(true);
    mIsPaused.store(false);

    int submittedCount = 0;
    for (int i = 0; i < NUM_URBS; i++) {
        fillUrb(mUrbs[i].urb);
        int retSub = ioctl(mFd, USBDEVFS_SUBMITURB, mUrbs[i].urb);
        if (retSub < 0) {
            LOGE("Initial USBDEVFS_SUBMITURB %d failed: %d (%s)", i, errno, strerror(errno));
            if (submittedCount == 0) {
                mIsRunning.store(false);
                return false;
            }
            break;
        }
        submittedCount++;
    }

    // 4. Launch dedicated worker thread
    pthread_create(&mThread, nullptr, threadFunc, this);
    LOGI("UsbAudioStreamer started successfully for %u Hz with %d initial URBs!", sampleRate, submittedCount);
    return true;
}

void UsbAudioStreamer::pause() {
    mIsPaused.store(true);
}

void UsbAudioStreamer::resume() {
    mIsPaused.store(false);
}

void UsbAudioStreamer::stop() {
    if (!mIsRunning.load() && mThread == 0) return;

    mIsRunning.store(false);

    // Discard all queued URBs non-blocking
    if (mFd >= 0) {
        for (int i = 0; i < NUM_URBS; i++) {
            if (mUrbs[i].urb) {
                ioctl(mFd, USBDEVFS_DISCARDURB, mUrbs[i].urb);
            }
        }
    }

    // Wait for worker thread to exit cleanly
    if (mThread != 0) {
        pthread_join(mThread, nullptr);
        mThread = 0;
    }

    // Drain any remaining discarded URBs without blocking
    if (mFd >= 0) {
        for (int i = 0; i < NUM_URBS; i++) {
            struct usbdevfs_urb* reaped = nullptr;
            ioctl(mFd, USBDEVFS_REAPURBNDELAY, &reaped);
        }

        // Reset interface to Alt 0 (Zero Bandwidth)
        struct usbdevfs_setinterface setintf;
        setintf.interface = mAsInterface;
        setintf.altsetting = 0;
        ioctl(mFd, USBDEVFS_SETINTERFACE, &setintf);
    }

    LOGI("UsbAudioStreamer stopped and interface set to Alt 0");
}

void UsbAudioStreamer::release() {
    stop();

    for (int i = 0; i < NUM_URBS; i++) {
        if (mUrbs[i].urb) {
            free(mUrbs[i].urb);
            mUrbs[i].urb = nullptr;
        }
        if (mUrbs[i].buffer) {
            free(mUrbs[i].buffer);
            mUrbs[i].buffer = nullptr;
        }
    }

    mFd = -1;
}

void* UsbAudioStreamer::threadFunc(void* context) {
    auto* self = static_cast<UsbAudioStreamer*>(context);

    // Dedicated high-priority audio streamer thread
    setpriority(PRIO_PROCESS, 0, -19);

    // Use moderate SCHED_FIFO priority (3) instead of max (99) to avoid starving UI & GC
    struct sched_param param;
    param.sched_priority = 3;
    pthread_setschedparam(pthread_self(), SCHED_FIFO, &param);

    self->streamLoop();
    return nullptr;
}

void UsbAudioStreamer::streamLoop() {
    while (mIsRunning.load()) {
        struct usbdevfs_urb* reaped = nullptr;
        int ret = ioctl(mFd, USBDEVFS_REAPURB, &reaped);
        if (ret < 0) {
            int err = errno;
            if (err == EAGAIN || err == EINTR) {
                // Non-blocking timed poll: wait up to 1ms for USB completion instead of busy-spinning
                struct pollfd pfd;
                pfd.fd = mFd;
                pfd.events = POLLOUT | POLLIN;
                pfd.revents = 0;
                poll(&pfd, 1, 1);
                continue;
            }
            if (mIsRunning.load()) {
                LOGE("USBDEVFS_REAPURB fatal error: %d (%s)", err, strerror(err));
                mIsRunning.store(false);
            }
            break;
        }

        if (!reaped || !mIsRunning.load()) break;

        // Reset status on reaped URB
        reaped->status = 0;

        fillUrb(reaped);

        int subRet = ioctl(mFd, USBDEVFS_SUBMITURB, reaped);
        if (subRet < 0) {
            int subErr = errno;
            if (subErr == EXDEV || subErr == EPIPE) {
                // Temporary timing/schedule gap: re-sync frame without terminating
                usleep(500);
                subRet = ioctl(mFd, USBDEVFS_SUBMITURB, reaped);
            }
            if (subRet < 0 && mIsRunning.load()) {
                LOGE("USBDEVFS_SUBMITURB failed in loop: %d (%s)", subErr, strerror(subErr));
                mIsRunning.store(false);
                break;
            }
        }
    }
}

void UsbAudioStreamer::fillUrb(struct usbdevfs_urb* urb) {
    urb->type = USBDEVFS_URB_TYPE_ISO;
    urb->endpoint = mEpOut;
    urb->flags = USBDEVFS_URB_ISO_ASAP;
    urb->number_of_packets = PACKETS_PER_URB;
    urb->status = 0;
    urb->actual_length = 0;
    urb->error_count = 0;
    urb->signr = 0;
    urb->usercontext = this;

    // Locate buffer
    uint8_t* buf = nullptr;
    for (int i = 0; i < NUM_URBS; i++) {
        if (mUrbs[i].urb == urb) {
            buf = mUrbs[i].buffer;
            break;
        }
    }
    if (!buf) return;

    urb->buffer = buf;

    uint32_t offset = 0;
    int16_t* pcm16 = reinterpret_cast<int16_t*>(buf);

    for (int p = 0; p < PACKETS_PER_URB; p++) {
        // Calculate number of stereo frames for this microframe (125us)
        // sampleRate / 8000 microframes per second
        uint32_t framesThisPacket = mSampleRate / 8000;
        mSubframeAcc += (mSampleRate % 8000);
        if (mSubframeAcc >= 8000) {
            framesThisPacket += 1;
            mSubframeAcc -= 8000;
        }

        uint32_t bytesThisPacket = framesThisPacket * 4; // 16-bit stereo = 4 bytes per frame

        urb->iso_frame_desc[p].length = bytesThisPacket;
        urb->iso_frame_desc[p].actual_length = 0;
        urb->iso_frame_desc[p].status = 0;

        int16_t* destPtr = pcm16 + (offset / sizeof(int16_t));

        if (!mIsPaused.load() && mReadCallback) {
            uint64_t readCount = mReadCallback(destPtr, framesThisPacket);
            if (readCount < framesThisPacket) {
                // Pad remaining frames with silence
                std::memset(destPtr + (readCount * 2), 0, (framesThisPacket - readCount) * 4);
            }
        } else {
            // Silence when paused
            std::memset(destPtr, 0, bytesThisPacket);
        }

        offset += bytesThisPacket;
    }

    urb->buffer_length = offset;
}


