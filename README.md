<div align="center">
  <img src="assets/icon.png" width="128" height="128" alt="JapaneseASMR Downloader Logo" style="border-radius: 28px;" />
  <h1>JapaneseASMR Downloader</h1>
  <p><b>A high-performance native Android application for downloading, managing, and playing Japanese ASMR audio works.</b></p>

  <p>
    <a href="README.md">English</a> |
    <a href="README.id.md">Bahasa Indonesia</a>
  </p>

  <p>
    <a href="https://github.com/SayMaven/japaneseasmr-android/releases/latest"><img src="https://img.shields.io/badge/Version-v1.2.0-blue?style=for-the-badge" alt="Version v1.2.0" /></a>
    <a href="https://github.com/SayMaven/japaneseasmr-android/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3.0-10b981?style=for-the-badge" alt="GPL v3.0 License" /></a>
    <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-059669?style=for-the-badge" alt="Platform" />
    <img src="https://img.shields.io/badge/Language-Kotlin-7c3aed?style=for-the-badge" alt="Language" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-1e293b?style=for-the-badge" alt="Jetpack Compose" />
  </p>

  <p>
    <a href="#whats-new-in-v120">What's New</a> •
    <a href="#features">Features</a> •
    <a href="#screenshots">Screenshots</a> •
    <a href="#download">Download</a> •
    <a href="#building-from-source">Building</a> •
    <a href="#architecture">Architecture</a> •
    <a href="#license">License</a>
  </p>
</div>

---

## Overview

JapaneseASMR Downloader is a full-featured, open-source Android application engineered specifically for downloading, archiving, and listening to Japanese ASMR voice dramas (RJ Works). Built from the ground up with Jetpack Compose, Kotlin Coroutines, Room Database, and AndroidX Media3 (ExoPlayer), the app delivers desktop-grade streaming performance, zero-OOM memory safety, and seamless background audio playback on mobile devices.

---

## What's New in v1.2.0

* **Smart Sleep Timer**: Integrated countdown timer (5, 10, 15, 30, 60, 120 min and +/- 5 min stepper) that automatically pauses its countdown when playback is paused.
* **Floating Mini Player**: Tab-aware interactive mini-bar over Unduhan, Riwayat, and Pengaturan tabs for instant playback control and one-tap expansion.
* **120 FPS History Library**: Zero main-thread disk I/O with precomputed models and stable keys for buttery-smooth 120 FPS list scrolling.
* **0ms Instant Playlist Drawer**: Pre-cached reactive list with drag-and-drop reordering and instantaneous bottom sheet opening.
* **Modern Speed Dialog**: Continuous smooth slider (0.25x – 2.0x) with zero tick dots and a balanced 2x3 quick preset grid.
* **Dynamic Continuous Download Queue**: Automatically processes dynamically added queue items without requiring manual restarts.
* **Solid Floating Volume HUD**: Ultra-slim 100% solid floating volume bar with direct hardware DAC volume feedback.
* **Deterministic Tab Retention**: Cold starts always begin at Home, while minimizing and reopening the app preserves the exact active tab.
* **Streamlined Settings & Path Formatting**: Clean, human-readable directory display (`Download/JapaneseASMR`) and intuitive multithread download labels.

---

## Screenshots

<div align="center">
  <img src="assets/screenshots/1.jpg" width="32%" alt="Screenshot 1" />
  <img src="assets/screenshots/2.jpg" width="32%" alt="Screenshot 2" />
  <img src="assets/screenshots/3.jpg" width="32%" alt="Screenshot 3" />
  <br />
  <img src="assets/screenshots/4.jpg" width="32%" alt="Screenshot 4" />
  <img src="assets/screenshots/5.jpg" width="32%" alt="Screenshot 5" />
  <img src="assets/screenshots/6.jpg" width="32%" alt="Screenshot 6" />
</div>

---

## Features

### High-Speed Download & Stream Demuxing Engine
* **Concurrent Parallel Connections (Multithread)**: Multi-threaded segment downloader supporting 4 to 32 parallel connections (default 16) for line-speed performance.
* **Dynamic Continuous Download Queue**: Adding new audio items while downloads are running automatically continues the queue until 100% complete without requiring manual restarts.
* **Zero-RAM Disk-Streaming Pipeline**: Stream demuxing directly to flash storage, enabling safe handling of multi-gigabyte audio tracks without risking `OutOfMemoryError`.
* **Standard Audio Remuxing**: Native ISO M4A / AAC container packaging ensuring complete seekability (0-second timeline scrubbing) across third-party audio players (Poweramp, HiBy Music, VLC, Google Files, etc.).
* **Automatic Storage Deduplication**: Detects existing files in your download folder and automatically skips redundant network requests.
* **Persistent Live Console**: Real-time progress monitoring, transfer speed calculation, and estimated completion time (ETA) with a persistent toggle button (`>_` / `</>`).

### Integrated Native Audio Player
* **Android MediaSession Integration**: Foreground audio playback service with rich notification controls and lock screen artwork display.
* **Smart Sleep Timer**: Built-in countdown timer with presets (5, 10, 15, 30, 60, 120 min) and +/- 5 min stepper buttons. Countdown automatically pauses when audio playback is paused.
* **Floating Mini Player (Tab-Aware)**: Interactive floating mini player bar displayed above the bottom navigation bar across Unduhan, Riwayat, and Pengaturan tabs for instant play/pause/skip and one-tap expansion.
* **True USB DAC Bit-Perfect Exclusive Takeover**: Direct USB Audio Class (UAC1 / UAC2) interface claiming with full Android kernel driver detach, isolating audio exclusively to the connected DAC with top priority.
* **DAC Hardware Volume Control & Solid Floating HUD**: Direct control of USB DAC hardware mixer via USB Control Transfers (`SET_CUR` / `GET_RANGE`), hardware volume key interception, and an ultra-slim 100% solid floating volume bar.
* **0ms Instant Playlist Drawer**: Pre-cached reactive list with drag-and-drop reordering and instantaneous bottom sheet opening.
* **Modern Playback Speed Dialog**: Continuous smooth slider (0.25x - 2.0x) with zero tick dots and a balanced 2x3 quick preset grid.
* **Replay 10s & Forward 10s Controls**: Quick jump controls for navigating audio dialogue and chapters effortlessly.
* **Persistent Timeline Mode**: Toggling between total elapsed duration and remaining time (`-mm:ss`) persists across app restarts.
* **Strict Physical File Validation**: Automatically hides missing files in the playlist collection and refreshes in real-time when audio files are restored to storage.

### Library & Metadata Management
* **120 FPS High-Performance History Library**: Zero main-thread disk I/O blocking with precomputed UI models and stable keys for buttery-smooth 120 FPS list scrolling.
* **Realtime Storage Synchronization (StorageSyncManager)**: Automatically scans physical storage on launch/resume, synchronizing new, moved, or deleted audio files with the local Room database.
* **Smart Sorting Filters**: Instant ordering by Date (Newest / Oldest) with real millisecond timestamp precision and Title (A-Z / Z-A) with rock-solid list anchoring.
* **Uniform Date Formatting**: Clean, standardized date strings (`dd MMM yyyy`) across the entire app.
* **Complete Metadata Tagging**: Embeds high-resolution cover artwork, Voice Actor (CV), Circle/Author, Work Title, and Genre tags directly into audio files (ID3v2 / MP4 atom).
* **Search & Collection Filtering**: Search your offline library by RJ Code, title, voice actor, or circle.

### Modern Material Design 3 UI & Navigation
* **4 Independent Navigation Tabs**: Home (Full Audio Player), Unduhan (Download Queue), Riwayat (Audio Library), and Pengaturan (Settings).
* **Deterministic Tab Retention**: Cold starts always begin at Home, while minimizing and reopening the app preserves the exact active tab without unwanted jumps.
* **Dynamic Material You / Monet Engine**: Automatic color palette generation based on Android 12+ wallpaper colors.
* **36 Curated Color Palettes**: Comprehensive theme selection with 3-segment circle previews, smooth horizontal carousel, and 9-dot pagination.
* **0ms Instant Startup Fast Cache**: Synchronous caching of theme preferences eliminates cold start dynamic color flicker.
* **Instant 0ms Tab Switching**: Retained GPU RenderNode layer architecture for zero-delay switching across tabs.
* **System, Dark, and Light Modes**: Full day/night theme support with smooth transitions.

---

## Architecture & Tech Stack

JapaneseASMR Downloader adheres strictly to Modern Android Architecture guidelines with separation of concerns and reactive data flow:

| Layer | Component | Description |
| :--- | :--- | :--- |
| **UI Layer** | Jetpack Compose + Material 3 | Declarative UI, Animations, Custom Theming |
| **State Management** | Kotlin Coroutines + StateFlow | Reactive, lifecycle-aware unidirectional data flow |
| **Media Player** | AndroidX Media3 (ExoPlayer) | Native audio decoding, MediaSession, Foreground Service |
| **Local Database** | Room Database (SQLite) | Local library persistence, query caching, reactive observers |
| **Preferences** | AndroidX DataStore + SharedPreferences Fast Cache | Type-safe persistence with 0ms synchronous startup cache |
| **Networking** | OkHttp 4 | HTTP/2 connection pooling, multi-thread segment downloads |
| **Audio Tagging** | Jaudiotagger | ID3v2, MP4 atom, and metadata embedding |
| **Image Loading** | Coil Compose | Asynchronous image decoding, memory and disk caching |

---

## Download

Pre-compiled universal release APKs signed with the official SayMaven certificate are available on the GitHub Releases page:

[**Download Latest Release (GitHub Releases)**](https://github.com/SayMaven/japaneseasmr-android/releases/latest)

---

## Building from Source

### Prerequisites
* JDK 17 or higher
* Android SDK with Build Tools 35.0.0+
* Gradle 8.7+ (included via Gradle Wrapper)

### Clone and Compile
```bash
# Clone the repository
git clone https://github.com/SayMaven/japaneseasmr-android.git
cd japaneseasmr-android

# Compile Debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/JapaneseASMR-v1.2.0-debug.apk
```

---

## Disclaimer

This software is developed strictly for personal, educational, and backup archiving purposes. The developer does not host, distribute, or promote copyrighted media. Users are solely responsible for ensuring compliance with applicable copyright laws and service terms in their jurisdiction.

---

## License

This project is licensed under the [GNU General Public License v3.0 (GPL-3.0)](LICENSE).
