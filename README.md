# JapaneseASMR Android 🎧

Aplikasi mobile Android modern dan ringan untuk mengunduh audio JapaneseASMR, menggabungkan *multi-track* & bonus/omake secara otomatis, menyematkan *cover art* HD & metadata resmi DLsite ke dalam tag ID3 MP3 secara native, serta dilengkapi pemutar audio bawaan.

---

## ✨ Fitur Utama

- 🎨 **Modern Jetpack Compose UI**: Desain antarmuka responsif Material 3 dengan dukungan multi-tema (Dark Theme Dracula, Light Theme, System Default, serta Dynamic Color / Material You).
- 🔍 **Real-time DLsite Metadata Scraper**: Mengambil metadata resmi langsung dari DLsite (Judul, CV / Voice Actor, Circle / Maker, Genre, Rating Usia, dan Cover Art).
- ⚡ **Pendeteksi Multi-Track & Omake Cerdas**: Otomatis mendeteksi Track 1, Track 2..20, Omake, Bonus, Tokuten, dan EX secara paralel dengan Kotlin Coroutines.
- 🏷️ **Penyematan Tag ID3v2.3 Asli**: Menanamkan Cover Art JPEG, Judul, Artis (CV), Album (Circle), dan Genre ke dalam file MP3 secara native di Android.
- 🎵 **Integrated Background Audio Player**: Pemutar musik bawaan berbasis Jetpack Media3 (ExoPlayer) yang mendukung pemutaran latar belakang, kontrol notifikasi, dan lockscreen.
- 🌐 **Instant Streaming Preview**: Mendengarkan karya secara instan dari internet tanpa harus mengunduh seluruh file terlebih dahulu.
- 💾 **Manajemen Riwayat & Offline Storage**: Riwayat unduhan tersimpan rapi di database lokal (Room SQLite).

---

## 📲 Cara Instalasi & Penggunaan

1. Unduh file **pp-debug.apk** atau **JapaneseASMR-Release.apk** dari menu **[Releases](https://github.com/SayMaven/japaneseasmr-android/releases)** atau tab **[Actions](https://github.com/SayMaven/japaneseasmr-android/actions)**.
2. Pasang (install) file .apk tersebut di perangkat Android Anda.
3. Buka aplikasi, masukkan satu atau beberapa Kode RJ (contoh: RJ01673437, RJ278932), lalu tekan tombol **Mulai Unduh**.

---

## 🛠️ Tech Stack

- **Language**: Kotlin 2.0+ (JVM 21)
- **UI Toolkit**: Jetpack Compose + Material 3
- **Media Engine**: Jetpack Media3 (ExoPlayer + MediaSessionService)
- **Networking**: OkHttp 4 + Jsoup
- **Image Loading & Cache**: Coil Compose
- **Database & Storage**: Room Database + Jetpack DataStore Preferences
- **Audio Tagging**: Jaudiotagger
- **Background Service**: Android Foreground Service

---

## 📄 Lisensi
Proyek ini dilisensikan di bawah **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)**. Hak cipta © 2026 **SayMaven**.
