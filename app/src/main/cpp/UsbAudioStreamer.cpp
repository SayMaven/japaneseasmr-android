#include "UsbAudioStreamer.h"
#include <android/log.h>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <errno.h>
#include <sys/resource.h>

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

    // 2. Set Alternate Setting 1 (16-bit PCM Stereo AudioStreaming)
    struct usbdevfs_setinterface setintf;
    setintf.interface = mAsInterface;
    setintf.altsetting = 1;
    int retAlt = ioctl(mFd, USBDEVFS_SETINTERFACE, &setintf);
    LOGI("Set AS Interface %d to Alt 1 result: %d", mAsInterface, retAlt);

    // 3. Prepare and submit initial URBs
    mIsRunning.store(true);
    mIsPaused.store(false);

    for (int i = 0; i < NUM_URBS; i++) {
        fillUrb(mUrbs[i].urb);
        int retSub = ioctl(mFd, USBDEVFS_SUBMITURB, mUrbs[i].urb);
        if (retSub < 0) {
            LOGE("Initial USBDEVFS_SUBMITURB %d failed: %d (%s)", i, errno, strerror(errno));
            mIsRunning.store(false);
            return false;
        }
    }

    // 4. Launch dedicated real-time worker thread
    pthread_create(&mThread, nullptr, threadFunc, this);
    LOGI("UsbAudioStreamer started successfully for %u Hz!", sampleRate);
    return true;
}

void UsbAudioStreamer::pause() {
    mIsPaused.store(true);
}

void UsbAudioStreamer::resume() {
    mIsPaused.store(false);
}

void UsbAudioStreamer::stop() {
    if (!mIsRunning.load()) return;

    mIsRunning.store(false);

    // Discard all queued URBs
    for (int i = 0; i < NUM_URBS; i++) {
        if (mUrbs[i].urb) {
            ioctl(mFd, USBDEVFS_DISCARDURB, mUrbs[i].urb);
        }
    }

    // Wait for thread to finish
    if (mThread) {
        pthread_join(mThread, nullptr);
        mThread = 0;
    }

    // Drain any remaining discarded URBs
    for (int i = 0; i < NUM_URBS; i++) {
        struct usbdevfs_urb* reaped = nullptr;
        ioctl(mFd, USBDEVFS_REAPURBNDELAY, &reaped);
    }

    // Reset interface to Alt 0 (Zero Bandwidth)
    if (mFd >= 0) {
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

    // Set maximum real-time priority for urgent audio thread
    setpriority(PRIO_PROCESS, 0, -20);

    struct sched_param param;
    param.sched_priority = sched_get_priority_max(SCHED_FIFO);
    pthread_setschedparam(pthread_self(), SCHED_FIFO, &param);

    self->streamLoop();
    return nullptr;
}

void UsbAudioStreamer::streamLoop() {
    while (mIsRunning.load()) {
        struct usbdevfs_urb* reaped = nullptr;
        int ret = ioctl(mFd, USBDEVFS_REAPURB, &reaped);
        if (ret < 0) {
            if (errno == EAGAIN || errno == EINTR) continue;
            if (mIsRunning.load()) {
                LOGE("USBDEVFS_REAPURB fatal error: %d (%s)", errno, strerror(errno));
            }
            break;
        }

        if (!reaped || !mIsRunning.load()) break;

        fillUrb(reaped);

        int subRet = ioctl(mFd, USBDEVFS_SUBMITURB, reaped);
        if (subRet < 0 && mIsRunning.load()) {
            LOGE("USBDEVFS_SUBMITURB failed in loop: %d (%s)", errno, strerror(errno));
            break;
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

