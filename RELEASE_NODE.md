## What's New

### Complete UI Overhaul, ASMR.one Browse & Online Streaming
JapaneseASMR v2.0.0 introduces a brand-new **ASMR Browse** experience with Pinterest-style grid and horizontal list views, KikoFlu-aligned sorting, multi-tag filtering, All-Ages safe mode, and an interactive Work Detail file tree. You can now **stream audio works directly online** without downloading them first, alongside a redesigned **Navigation Drawer** and **15+ dedicated Settings sub-screens**.

### Custom Playlists, Explorer Folders & Card Customization
Local Collection has been rebuilt with a **Pinterest-style grid & list layout**, **Windows Explorer-style folder thumbnails**, and **Custom User Playlists** (powered by Room v6) that track both offline and online works. You can customize which badges appear on Work Cards (Rating, Price, Sales, Release Date, Circle, Subtitle, Age Rating) and reorder the Player action bar buttons to fit your workflow.

### Synchronized Subtitles, Floating Lyrics & Multi-Provider Translation
Enjoy built-in `.vtt` / `.lrc` / `.srt` subtitle support with automatic ASMR backfill, an interactive synchronized lyrics panel in the Player, and an **in-app Floating Lyrics Bar**. The all-new translation engine supports Google Translate, Microsoft Edge, Youdao, MyMemory, and custom **LLM providers** (Claude, Gemini, OpenAI-compatible) with specialized Japanese and Chinese ASMR glossaries and viewport-first incremental translation.

### Audio Gain Boost (dB), Privacy Mode & Download Merge Mode
* **Audio Gain Amplification (dB):** Boost quiet ASMR whispers directly through both ExoPlayer and the native C++ Oboe / USB DAC engine.
* **Privacy Mode:** Blur cover art in-app and in system notifications, and mask work titles with custom placeholder text.
* **Download Audio Merge & Auto-Enrichment:** Choose whether to merge multi-track downloads into a single file, and automatically enrich metadata for imported local works.

---

## Bug Fixes

* Fixed `VJ` and `BJ` work ID mapping when resolving metadata and cover art from DLsite and ASMR.one
* Fixed cover art leakage across individual audio tracks inside multi-file folders
* Fixed player mode conflicts when switching between local playback, virtual albums, and online streams
* Fixed local collection pagination scroll position, page size sync, and false subtitle badges
* Fixed USB DAC exclusive mode audio gain wiring and stability across track transitions

---

## Improvements

* Enabled R8 code and resource shrinking for Release builds, cutting APK size nearly in half (~11.2 MB)
* Migrated dependencies to Gradle Version Catalog (`gradle/libs.versions.toml`) and added Dual-APK GitHub Actions CI
* Modularized core UI screens, Player delegates, and translation providers for smoother 120 FPS performance and easier maintenance
* Updated in-app User Guide and Settings across English, Indonesian, Japanese, and Simplified Chinese
* Bumped application version to v2.0.0 (`versionCode = 12`)
