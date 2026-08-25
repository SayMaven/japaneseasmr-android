<div align="center">
  <img src="assets/icon.png" width="128" height="128" alt="Logo JapaneseASMR Downloader" style="border-radius: 28px;" />
  <h1>JapaneseASMR Downloader</h1>
  <p><b>Aplikasi Android native berperforma tinggi untuk mengunduh, mengelola, dan memutar karya audio Japanese ASMR.</b></p>

  <p>
    <a href="README.md">English</a> |
    <a href="README.id.md">Bahasa Indonesia</a>
  </p>

  <p>
    <a href="https://github.com/SayMaven/japaneseasmr-android/releases/latest"><img src="https://img.shields.io/badge/Versi-v1.2.0-blue?style=for-the-badge" alt="Versi v1.2.0" /></a>
    <a href="https://github.com/SayMaven/japaneseasmr-android/blob/main/LICENSE"><img src="https://img.shields.io/badge/Lisensi-GPL%20v3.0-10b981?style=for-the-badge" alt="Lisensi GPL v3.0" /></a>
    <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-059669?style=for-the-badge" alt="Platform" />
    <img src="https://img.shields.io/badge/Bahasa-Kotlin-7c3aed?style=for-the-badge" alt="Bahasa" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-1e293b?style=for-the-badge" alt="Jetpack Compose" />
  </p>

  <p>
    <a href="#apa-yang-baru-di-v120">Pembaruan</a> •
    <a href="#fitur-utama">Fitur Utama</a> •
    <a href="#tangkapan-layar">Tangkapan Layar</a> •
    <a href="#unduh-aplikasi">Unduh</a> •
    <a href="#kompilasi-dari-kode-sumber">Kompilasi</a> •
    <a href="#arsitektur-aplikasi">Arsitektur</a> •
    <a href="#lisensi">Lisensi</a>
  </p>
</div>

---

## Gambaran Umum

JapaneseASMR Downloader adalah aplikasi Android sumber terbuka (open-source) lengkap yang dirancang khusus untuk mengunduh, mengarsipkan, dan mendengarkan drama suara ASMR Jepang (Karya RJ). Dibangun menggunakan teknologi modern Android seperti Jetpack Compose, Kotlin Coroutines, Room Database, dan AndroidX Media3 (ExoPlayer), aplikasi ini menghadirkan performa pengunduhan berkecepatan tinggi, keamanan memori tanpa risiko OOM (Out of Memory), serta pemutaran audio latar belakang yang stabil dan hemat daya.

---

## Apa yang Baru di v1.2.0

* **Sleep Timer Cerdas**: Pengatur waktu tidur terintegrasi (preset 5, 10, 15, 30, 60, 120 menit dan penyesuaian kelipatan +/- 5 menit) yang otomatis menjeda hitung mundur saat audio dihentikan sementara (pause).
* **Floating Mini Player**: Bar mini interaktif di atas bar navigasi pada tab Unduhan, Riwayat, dan Pengaturan untuk kontrol pemutaran instan dan ekspansi ke pemutar penuh dengan satu ketukan.
* **Scroll Riwayat 120 FPS**: Bebas dari pemindaian I/O disk blocking pada UI thread dengan model data terkomputasi awal dan stable keys untuk scroll daftar koleksi yang sangat mulus.
* **Akses Daftar Putar 0ms**: Akses instan drawer daftar putar dengan fitur drag-and-drop reorder tanpa penundaan pemindaian database.
* **Dialog Kecepatan Putar Modern**: Slider horizontal mulus tanpa titik-titik (0.25x – 2.0x) dilengkapi grid tombol pintas preset 2x3 yang proporsional.
* **Antrean Unduhan Berkelanjutan Dinamis**: Penambahan item unduhan baru saat proses download sedang berjalan akan diproses secara otomatis hingga tuntas tanpa perlu memulai ulang.
* **Floating Hardware Volume HUD Solid**: Bar indikator volume perangkat keras DAC melayang yang ultra-ramping dan 100% solid.
* **Retensi Tab Deterministik**: Aplikasi selalu memulai di tab Home saat dibuka dari kondisi mati (cold start), dan tetap mempertahankan tab terakhir saat diminimize.
* **Penyederhanaan UI Pengaturan**: Format path penyimpanan yang ringkas dan sinkron (`Download/JapaneseASMR`) serta penamaan opsi jalur unduhan yang ramah pengguna.

---

## Tangkapan Layar

<div align="center">
  <img src="assets/screenshots/1.jpg" width="32%" alt="Tangkapan Layar 1" />
  <img src="assets/screenshots/2.jpg" width="32%" alt="Tangkapan Layar 2" />
  <img src="assets/screenshots/3.jpg" width="32%" alt="Tangkapan Layar 3" />
  <br />
  <img src="assets/screenshots/4.jpg" width="32%" alt="Tangkapan Layar 4" />
  <img src="assets/screenshots/5.jpg" width="32%" alt="Tangkapan Layar 5" />
  <img src="assets/screenshots/6.jpg" width="32%" alt="Tangkapan Layar 6" />
</div>

---

## Fitur Utama

### Mesin Pengunduhan & Demuxing Berkecepatan Tinggi
* **Koneksi Simultan Multithread**: Pengunduh segmen multi-jalur yang mendukung 4 hingga 32 jalur simultan (default 16) untuk memaksimalkan bandwidth internet.
* **Antrean Dinamis Otomatis**: Antrean baru langsung terintegrasi ke dalam proses unduh aktif secara berkelanjutan.
* **Streaming Disk Tanpa Beban RAM**: Demuxing langsung ke penyimpanan internal/eksternal untuk menangani berkas audio berukuran gigabyte tanpa risiko `OutOfMemoryError`.
* **Remuxing Kontainer Standar**: Pengemasan kontainer ISO M4A / AAC native yang menjamin fungsi scrubbing timeline instan (0 detik) di semua pemutar audio pihak ketiga (Poweramp, HiBy Music, VLC, dsb.).
* **Deduplikasi Berkas Otomatis**: Mendeteksi keberadaan berkas di penyimpanan dan secara otomatis melewati pengunduhan ulang yang tidak diperlukan.
* **Konsol Log Interaktif**: Pemantauan progres unduhan, kecepatan transfer waktu-nyata, dan estimasi waktu selesai (ETA) dengan tombol toggle (`>_` / `</>`).

### Pemutar Audio Native Terintegrasi
* **Integrasi Android MediaSession**: Layanan pemutar latar belakang foreground dengan kontrol notifikasi lengkap dan tampilan cover art di layar kunci.
* **Sleep Timer Otomatis**: Penghitung waktu mundur yang tersinkronisasi dengan status pemutaran audio.
* **Floating Mini Player**: Kontrol pemutar melayang yang responsif di berbagai tab navigasi.
* **Mode Eksklusif Bit-Perfect USB DAC**: Pengambilalihan antarmuka USB Audio Class (UAC1 / UAC2) langsung dengan pemisahan driver kernel Android untuk audio murni prioritas tinggi.
* **Kontrol Volume Perangkat Keras DAC**: Pengaturan langsung chip mixer DAC melalui USB Control Transfers (`SET_CUR` / `GET_RANGE`), pencegatan tombol volume fisik HP, dan HUD volume melayang.
* **Drawer Daftar Putar 0ms**: Daftar putar koleksi reaktif dengan fitur pengurutan ulang (drag-and-drop) instan.
* **Dialog Kecepatan Putar Halus**: Pengaturan kecepatan fleksibel dari 0.25x hingga 2.0x.
* **Tombol Lompat 10 Detik**: Mundur 10 detik dan Maju 10 detik untuk navigasi dialog audio dengan mudah.
* **Format Waktu Persisten**: Pilihan format waktu putar (durasi total vs sisa waktu `-mm:ss`) tersimpan secara permanen.
* **Validasi Berkas Fisik**: Otomatis menyembunyikan berkas yang terhapus dari penyimpanan fisik dan memperbarui daftar saat berkas dikembalikan.

### Manajemen Koleksi & Metadata
* **Sinkronisasi Penyimpanan Otomatis (StorageSyncManager)**: Memindai folder penyimpanan saat aplikasi dibuka/dilanjutkan, menyinkronkan berkas baru, berpindah, atau terhapus dengan database Room.
* **Filter Pengurutan Cerdas**: Pengurutan instan berdasarkan Waktu (Terbaru / Terlama) dengan presisi milidetik dan Judul (A-Z / Z-A).
* **Format Tanggal Standar**: Format tampilan tanggal yang seragam (`dd MMM yyyy`) di seluruh aplikasi.
* **Penyematan Metadata Lengkap**: Menyematkan cover art resolusi tinggi, Pengisi Suara (CV), Lingkaran/Pembuat (Circle), Judul Karya, dan Genre langsung ke dalam tag berkas audio (ID3v2 / MP4 atom).
* **Pencarian Cepat Offline**: Cari karya berdasarkan Kode RJ, judul, nama CV, atau circle pembuat.

### Tampilan Modern Material Design 3
* **4 Tab Navigasi Mandiri**: Home (Pemutar Penuh), Unduhan (Antrean Unduh), Riwayat (Koleksi Berkas), dan Pengaturan.
* **Retensi Sesi Stabil**: Buka aplikasi awal selalu di tab Home, dan tetap berada di tab aktif saat aplikasi diminimize.
* **Mesin Warna Dinamis Material You / Monet**: Penyesuaian tema otomatis berdasarkan warna wallpaper Android 12+.
* **36 Palet Warna Pilihan**: Pilihan palet warna lengkap dengan pratinjau lingkaran 3 segmen dan carousel horizontal.
* **Fast Cache Startup 0ms**: Penyimpanan cache tema sinkron untuk menghilangkan kedipan warna saat peluncuran awal.
* **Perpindahan Tab Instan**: Arsitektur GPU RenderNode layer yang mempertahankan seluruh tab di memori tanpa jeda rendering.
* **Dukungan Mode Gelap & Terang**: Tema sistem, gelap, dan terang penuh dengan transisi halus.

---

## Arsitektur Aplikasi

JapaneseASMR Downloader mengimplementasikan prinsip Modern Android Architecture dengan pemisahan modul yang jelas dan alur data searah (unidirectional data flow):

| Lapisan | Komponen | Deskripsi |
| :--- | :--- | :--- |
| **UI Layer** | Jetpack Compose + Material 3 | Antarmuka deklaratif, Animasi, Tema kustom |
| **State Management** | Kotlin Coroutines + StateFlow | Aliran data reaktif yang sadar siklus hidup (lifecycle-aware) |
| **Media Player** | AndroidX Media3 (ExoPlayer) | Pemutar audio native, MediaSession, Foreground Service |
| **Database Lokal** | Room Database (SQLite) | Penyimpanan riwayat lokal, cache kueri, observer reaktif |
| **Preferensi** | AndroidX DataStore + SharedPreferences Fast Cache | Penyimpanan preferensi tipe-aman dengan cache sinkron 0ms |
| **Jaringan** | OkHttp 4 | HTTP/2 connection pooling, unduhan multi-jalur simultan |
| **Tagging Audio** | Jaudiotagger | Penulisan tag metadata ID3v2 dan MP4 atom |
| **Pemuatan Gambar** | Coil Compose | Dekoding gambar asinkron, cache memori dan disk |

---

## Unduh Aplikasi

Berkas APK rilis yang telah dikompilasi dan ditandatangani secara resmi tersedia di halaman GitHub Releases:

[**Unduh Rilis Terbaru (GitHub Releases)**](https://github.com/SayMaven/japaneseasmr-android/releases/latest)

---

## Kompilasi dari Kode Sumber

### Prasyarat
* JDK 17 atau lebih baru
* Android SDK dengan Build Tools 35.0.0+
* Gradle 8.7+ (sudah termasuk melalui Gradle Wrapper)

### Kloning dan Kompilasi
```bash
# Kloning repositori
git clone https://github.com/SayMaven/japaneseasmr-android.git
cd japaneseasmr-android

# Kompilasi Debug APK
./gradlew assembleDebug
```

Berkas APK hasil kompilasi akan tersimpan di:
```
app/build/outputs/apk/debug/JapaneseASMR-v1.2.0-debug.apk
```

---

## Pernyataan Penyangkalan (Disclaimer)

Perangkat lunak ini dikembangkan secara ketat untuk tujuan pribadi, edukasi, dan pengarsipan cadangan. Pengembang tidak menyediakan, mendistribusikan, atau mempromosikan media berhak cipta. Pengguna bertanggung jawab penuh untuk memastikan kepatuhan terhadap undang-undang hak cipta dan ketentuan layanan yang berlaku di wilayah hukum masing-masing.

---

## Lisensi

Proyek ini dilisensikan di bawah [GNU General Public License v3.0 (GPL-3.0)](LICENSE).
