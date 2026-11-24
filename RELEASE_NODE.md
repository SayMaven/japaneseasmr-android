## What's New

### Complete UI Overhaul, ASMR Browse & Online Streaming
JapaneseASMR v2.0.0 introduces a brand-new **ASMR Browse** experience with Pinterest-style grid and horizontal list views, KikoFlu-aligned sorting, multi-tag filtering, All-Ages safe mode, and an interactive Work Detail file tree. You can now **stream audio works directly online** without downloading them first, alongside a redesigned **Navigation Drawer** and **15+ dedicated Settings sub-screens**.

### Custom Playlists, Explorer Folders & Card Customization
Local Collection has been rebuilt with a **Pinterest-style grid & list layout**, **Windows Explorer-style folder thumbnails**, and **Custom User Playlists** (powered by Room v6) that track both offline and online works. You can customize which badges appear on Work Cards (Rating, Price, Sales, Release Date, Circle, Subtitle, Age Rating) and reorder the Player action bar buttons to fit your workflow.

### Synchronized Subtitles, Floating Lyrics & Multi-Provider Translation
Enjoy built-in `.vtt` / `.lrc` / `.srt` subtitle support with automatic ASMR backfill, an interactive synchronized lyrics panel in the Player, and an **in-app Floating Lyrics Bar**. The all-new translation engine supports Google Translate, Microsoft Edge, Youdao, MyMemory, and custom **LLM providers** (Claude, Gemini, OpenAI-compatible) with specialized Japanese and Chinese ASMR glossaries and viewport-first incremental translation.

### Audio Gain Boost (dB), Privacy Mode & Download Merge Mode
* **Audio Gain Amplification (dB):** Boost quiet ASMR whispers directly through both ExoPlayer and the native C++ Oboe / USB DAC engine.
* **Privacy Mode:** Blur cover art in-app and in system notifications, and mask work titles with custom placeholder text.
* **Download Audio Merge & Auto-Enrichment:** Choose whether to merge multi-track downloads into a single file (with automatic metadata & genre tagging in both merged and multi-track folder modes), and automatically enrich metadata for imported local works.

### OKLCH Color System & Playlist/History Backup
* **OKLCH Perceptual Palette Engine:** Rebuilt Light and Dark Mode color generation across all 12 palettes (alongside Material You Dynamic Color) with WCAG AA contrast, brand-tinted neutral canvases, crisp rounded cards, and distinct genre/CV badges.
* **Backup & Restore:** Export and restore your Custom Playlists and Download History as JSON from Download & Storage Settings, backed by an automatic mirror file (`Download/JapaneseASMR/japaneseasmr_backup_auto.json`).

---

## Bug Fixes

* Fixed `VJ` and `BJ` work ID mapping when resolving metadata and cover art from DLsite and ASMR.one
* Fixed cover art leakage across individual audio tracks inside multi-file folders
* Fixed player mode conflicts when switching between local playback, virtual albums, and online streams
* Fixed local collection pagination scroll position, page size sync, and false subtitle badges
* Fixed USB DAC exclusive mode audio gain wiring and stability across track transitions

---

## Improvements

* **Security & Privacy Hardening:** Encrypted LLM API key storage using Android Keystore AES-256-GCM (`SecurePreferences`) with automatic one-time migration, plus `network_security_config.xml` enforcing HTTPS by default
* **Modern Architecture & Adaptive UI:** Adopted Koin Dependency Injection, extracted `PlayerPlaylistDelegate`, added `@Serializable` type-safe `AppRoute` definitions, and added adaptive `NavigationRail` support for tablets/landscape screens
* **Testing, Quality & Build Optimization:** Added Detekt static analysis, `MockWebServer` offline translation tests, OKLCH WCAG contrast tests, Room schema export (`exportSchema = true`) with migration tests, R8 shrinking (~11.2 MB Release APK), Gradle Version Catalog, and Dual-APK GitHub Actions CI
* **Diagnostics & Localization:** Added centralized `AppLogger` with diagnostic error ring buffer, and updated in-app User Guide and Settings across English, Indonesian, Japanese, and Simplified Chinese
* Bumped application version to v2.0.0 (`versionCode = 12`)
