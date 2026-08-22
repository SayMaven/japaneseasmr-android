# JapaneseASMR Downloader for Android 🎧

Aplikasi Android Native (Kotlin + Jetpack Compose) modern, cepat, dan elegan untuk mengunduh, mengelola koleksi, dan memutar karya audio ASMR Jepang (Kode RJ) langsung dari perangkat smartphone Anda.

---

## ✨ Fitur Utama

- ⚡ **High-Speed Native Engine**:
  - Mengunduh aliran audio HLS (`.m3u8`) dan direct audio dengan koneksi paralel simultan berkecepatan tinggi (dapat diatur 4 hingga 32 koneksi, default 16 koneksi).
  - Mendukung pengunduhan karya single-track, multi-track (Track 1 s/d 20), serta bonus/omake/tokuten/EX.
- 🎵 **Format Audio Standar & Kompatibilitas Penuh**:
  - Remuxing native ke container standar (**`.m4a`** untuk stream AAC & **`.mp3`** untuk direct audio).
  - Dilengkapi tabel indeks durasi dan sample table presisi sehingga audio **100% seekable** (bebas digeser ke menit/detik manapun) dan kompatibel dengan semua pemutar audio (HiBy Music, Poweramp, VLC, Samsung/MIUI Music, Google Files, dll.).
  - Otomatis menanamkan cover art resolusi tinggi dan metadata lengkap (Judul, Artis/CV, Album/Circle, Genre).
- 📋 **Deteksi & Tempel Clipboard Pintar**:
  - Tombol tempel satu klik di kolom input.
  - Fitur deteksi otomatis kode RJ langsung dari clipboard saat membuka halaman aplikasi.
- 📟 **Konsol Log Real-time**:
  - Menampilkan proses unduhan secara live (progress segmen, kecepatan unduh, perkiraan sisa waktu/ETA, serta status penyematan tag metadata).
  - Tampilan konsol otomatis menyesuaikan dengan tema aktif (Light Mode & Dark Mode).
- 📂 **Penyimpanan Fleksibel**:
  - Lokasi unduhan default ke `/storage/emulated/0/Download/JapaneseASMR`.
  - Integrasi dengan File Manager bawaan sistem Android (Storage Access Framework) untuk memilih folder tujuan unduhan secara bebas dan mudah.
  - Opsi penamaan file rapi: menggunakan judul karya asli `[RJxxxxxx] Judul.m4a` atau hanya kode RJ `RJxxxxxx.m4a`.
- 🎧 **Pemutar Audio Bawaan Lengkap**:
  - Pemutar audio lokal & streaming instan dengan visual cover art resolusi tinggi.
  - Mode ulangi 3 tahap (*Mati*, *Ulangi Semua*, dan *Ulangi Track Ini*).
  - Waktu di sisi kanan slider dapat diklik untuk berganti antara **Total Durasi** dan **Sisa Waktu (-menit:detik)**.
  - Notifikasi kontrol media di status bar & lockscreen (didukung Android Jetpack Media3).
- 🎨 **Desain Material You Modern**:
  - Mendukung Tema Gelap (Dark/Dracula), Tema Terang (Light), Mengikuti Sistem (Auto), serta Warna Dinamis Material You (Android 12+).
- 💾 **Manajemen Riwayat & Offline Storage**:
  - Riwayat koleksi tersimpan di database lokal Room dengan fitur pencarian instan berdasarkan judul, CV, maupun Circle.

---

## 📥 Cara Instalasi (Pengguna)

1. Buka halaman **[Releases](https://github.com/SayMaven/japaneseasmr-android/releases)** pada repositori ini.
2. Unduh file **`app-release.apk`** atau **`app-debug.apk`** versi terbaru.
3. Buka file APK yang telah diunduh di HP Android Anda dan lakukan instalasi.
4. Buka aplikasi **JapaneseASMR Downloader**, izinkan akses penyimpanan/notifikasi, dan aplikasi siap digunakan.

---

## 📱 Cara Penggunaan

1. **Unduh Karya**:
   - Salin kode RJ (contoh: `RJ01673437`) atau teks yang mengandung kode RJ.
   - Buka tab **Antrean**, kode RJ akan terdeteksi otomatis (atau tekan tombol tempel).
   - Tekan tombol **Tambah** $\rightarrow$ Tekan **Mulai Unduh**.
   - Pantau proses unduhan secara real-time pada panel log di bawahnya.
2. **Putar Koleksi**:
   - Buka tab **Riwayat** $\rightarrow$ Tekan tombol **Play** pada karya yang diinginkan untuk memutarnya langsung di tab **Pemutar**.
3. **Pengaturan**:
   - Buka tab **Pengaturan** untuk mengubah folder penyimpanan, mengatur jumlah koneksi paralel, mengganti tema tampilan, atau mengaktifkan format nama file lengkap.

---

## 🛠️ Teknologi yang Digunakan

- **Bahasa**: Kotlin (100% Native Android)
- **UI Framework**: Jetpack Compose & Material 3
- **Media Engine**: Jetpack Media3 (ExoPlayer & MediaSession)
- **Database**: Room Database & DataStore Preferences
- **Jaringan**: OkHttp 4 & Jsoup
- **Metadata Audio**: Jaudiotagger (ID3v2 & MP4 atom tagger)
- **Image Loader**: Coil 2

---

## 📄 Lisensi

Proyek ini dilisensikan di bawah [GNU General Public License v3.0](LICENSE).
