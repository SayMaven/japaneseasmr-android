#pragma once

#include <vector>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <algorithm>

/**
 * High-performance, Lock-Free Single-Producer Single-Consumer (SPSC) Audio Ring Buffer.
 * Stores interleaved 16-bit stereo PCM audio frames.
 * Producer (Decoder Thread) writes pre-decoded audio ahead of time.
 * Consumer (USB Isochronous Streamer / Oboe Thread) reads in nanoseconds without any mutex or disk I/O.
 */
class AudioRingBuffer {
public:
    explicit AudioRingBuffer(size_t capacityFrames = 65536)
        : mCapacity(capacityFrames),
          mBuffer(capacityFrames * 2, 0),
          mReadIndex(0),
          mWriteIndex(0) {}

    void resize(size_t capacityFrames) {
        mCapacity = capacityFrames;
        mBuffer.assign(capacityFrames * 2, 0);
        mReadIndex.store(0, std::memory_order_relaxed);
        mWriteIndex.store(0, std::memory_order_relaxed);
    }

    void reset() {
        mReadIndex.store(0, std::memory_order_relaxed);
        mWriteIndex.store(0, std::memory_order_relaxed);
    }

    size_t getAvailableReadFrames() const {
        size_t write = mWriteIndex.load(std::memory_order_acquire);
        size_t read = mReadIndex.load(std::memory_order_relaxed);
        if (write >= read) {
            return write - read;
        } else {
            return mCapacity - (read - write);
        }
    }

    size_t getAvailableWriteFrames() const {
        return (mCapacity - 1) - getAvailableReadFrames();
    }

    // Write stereo frames (each frame = 2 samples of int16_t)
    size_t write(const int16_t* src, size_t frames) {
        size_t available = getAvailableWriteFrames();
        size_t toWrite = std::min(frames, available);
        if (toWrite == 0) return 0;

        size_t writeIdx = mWriteIndex.load(std::memory_order_relaxed);

        size_t firstPart = std::min(toWrite, mCapacity - writeIdx);
        std::memcpy(&mBuffer[writeIdx * 2], src, firstPart * 2 * sizeof(int16_t));

        size_t secondPart = toWrite - firstPart;
        if (secondPart > 0) {
            std::memcpy(&mBuffer[0], src + (firstPart * 2), secondPart * 2 * sizeof(int16_t));
        }

        size_t nextWriteIdx = (writeIdx + toWrite) % mCapacity;
        mWriteIndex.store(nextWriteIdx, std::memory_order_release);
        return toWrite;
    }

    // Read stereo frames (each frame = 2 samples of int16_t)
    size_t read(int16_t* dst, size_t frames) {
        size_t available = getAvailableReadFrames();
        size_t toRead = std::min(frames, available);
        if (toRead == 0) return 0;

        size_t readIdx = mReadIndex.load(std::memory_order_relaxed);

        size_t firstPart = std::min(toRead, mCapacity - readIdx);
        std::memcpy(dst, &mBuffer[readIdx * 2], firstPart * 2 * sizeof(int16_t));

        size_t secondPart = toRead - firstPart;
        if (secondPart > 0) {
            std::memcpy(dst + (firstPart * 2), &mBuffer[0], secondPart * 2 * sizeof(int16_t));
        }

        size_t nextReadIdx = (readIdx + toRead) % mCapacity;
        mReadIndex.store(nextReadIdx, std::memory_order_release);
        return toRead;
    }

private:
    size_t mCapacity;
    std::vector<int16_t> mBuffer;
    std::atomic<size_t> mReadIndex;
    std::atomic<size_t> mWriteIndex;
};

