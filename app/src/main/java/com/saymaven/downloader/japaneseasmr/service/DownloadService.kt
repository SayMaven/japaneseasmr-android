package com.saymaven.downloader.japaneseasmr.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.saymaven.downloader.japaneseasmr.JapaneseAsmrApp
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.data.model.DownloadStatus
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import com.saymaven.downloader.japaneseasmr.data.remote.TrackDiscoveryService
import com.saymaven.downloader.japaneseasmr.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val NOTIFICATION_ID = 1001

        private val _queueState = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
        val queueState = _queueState.asStateFlow()

        private val _isDownloading = MutableStateFlow(false)
        val isDownloading = _isDownloading.asStateFlow()

        private val _logsState = MutableStateFlow<List<String>>(emptyList())
        val logsState = _logsState.asStateFlow()

        fun log(msg: String) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val formatted = "[$time] $msg"
            val current = _logsState.value.toMutableList()
            if (current.size > 200) {
                current.removeAt(0)
            }
            current.add(formatted)
            _logsState.value = current
        }

        fun clearLogs() {
            _logsState.value = emptyList()
        }

        fun enqueue(items: List<DownloadQueueItem>) {
            val current = _queueState.value.toMutableList()
            for (item in items) {
                if (current.none { it.rjid == item.rjid }) {
                    current.add(item)
                    log("[+] Ditambahkan ke antrean: ${item.rjid}")
                }
            }
            _queueState.value = current
        }

        fun clearQueue() {
            val current = _queueState.value.toMutableList()
            if (_isDownloading.value) {
                // Pertahankan item yang sedang didownload, hapus yang sudah selesai atau belum mulai
                current.removeAll { it.status != DownloadStatus.DOWNLOADING && it.status != DownloadStatus.PROCESSING }
            } else {
                current.clear()
            }
            _queueState.value = current
            log("[i] Antrean unduhan dibersihkan.")
        }

        fun removeItem(rjid: String) {
            val current = _queueState.value.toMutableList()
            current.removeAll { it.rjid.equals(rjid, ignoreCase = true) }
            _queueState.value = current
            log("[-] Dihapus dari antrean: $rjid")
        }

        fun startDownload(context: Context) {
            if (_queueState.value.isEmpty()) return
            val intent = Intent(context, DownloadService::class.java)
            context.startForegroundService(intent)
        }

        fun getDefaultDownloadDirectory(): File {
            val publicDownload = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "JapaneseASMR"
            )
            if (!publicDownload.exists()) {
                publicDownload.mkdirs()
            }
            return publicDownload
        }

        fun sanitizeFilename(name: String): String {
            val clean = name.replace(Regex("[\\\\/*?:\"<>|]"), "").trim().trimEnd('.')
            return if (clean.isBlank()) "audio" else clean
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (_queueState.value.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification("Memulai unduhan antrean...", 0, true)
        )

        if (!_isDownloading.value) {
            serviceScope.launch {
                processQueue()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun processQueue() {
        val database = AsmrDatabase.getDatabase(this)
        val historyDao = database.historyDao()
        val prefs = PreferencesManager(this)

        val customDirStr = prefs.downloadDirFlow.first()
        val parallelConn = prefs.parallelConnectionsFlow.first()
        val useDetailedFilename = prefs.useDetailedFilenameFlow.first()

        val downloadDir = if (!customDirStr.isNullOrBlank()) {
            val f = File(customDirStr)
            f.mkdirs()
            if (f.canWrite()) f else getDefaultDownloadDirectory()
        } else {
            getDefaultDownloadDirectory()
        }

        _isDownloading.value = true
        log("===================================================")
        log("[i] Memulai proses pemrosesan antrean unduhan...")

        val tempDir = File(cacheDir, "download_temp").apply { mkdirs() }

        while (true) {
            // Ambil item pending berikutnya secara dinamis dari live queueState
            val currentQueue = _queueState.value
            val nextItem = currentQueue.firstOrNull { it.status == DownloadStatus.PENDING }
            if (nextItem == null) {
                // Tidak ada lagi antrean yang berstatus PENDING
                break
            }

            val itemRjid = nextItem.rjid
            val totalInQueue = _queueState.value.size
            val finishedCount = _queueState.value.count {
                it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
            }
            val currentNum = finishedCount + 1

            log("[*] Memproses [$currentNum/$totalInQueue]: $itemRjid")

            // === 1. CEK FILE SUDAH ADA DI PENYIMPANAN (SKIP OTOMATIS) ===
            val existingFile = AudioStorageHelper.findExistingAudioFile(downloadDir, itemRjid)
            if (existingFile != null && existingFile.length() > 0) {
                val fileSizeStr = AudioDownloader.formatFileSize(existingFile.length())
                val dateStr = AudioStorageHelper.formatDateForDisplay(existingFile.lastModified())

                log("  [i] File audio [$itemRjid] sudah ada di penyimpanan (${existingFile.name}).")
                log("  [i] Melewati proses unduhan...")

                // Pastikan tersimpan di Room database
                val existingHistory = historyDao.getHistoryById(itemRjid)
                if (existingHistory == null) {
                    historyDao.insertHistory(
                        HistoryEntity(
                            rjid = itemRjid,
                            title = nextItem.title.ifBlank { existingFile.nameWithoutExtension },
                            cv = nextItem.cv,
                            circle = nextItem.circle,
                            genre = nextItem.genre,
                            ageRating = nextItem.ageRating,
                            coverUrl = nextItem.coverUrl,
                            localFilePath = existingFile.absolutePath,
                            downloadDate = dateStr,
                            fileSize = fileSizeStr
                        )
                    )
                }

                updateItem(itemRjid) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        statusText = "Sudah ada di penyimpanan (Dilewati)",
                        progress = 1f,
                        speed = "-",
                        eta = "-",
                        downloadedSize = fileSizeStr,
                        totalSize = fileSizeStr
                    )
                }
                continue
            }

            updateItem(itemRjid) {
                it.copy(
                    status = DownloadStatus.DOWNLOADING,
                    statusText = "Mengambil metadata karya..."
                )
            }

            // 2. Scrape Metadata
            val meta = try {
                DLsiteScraper.fetchMetadata(itemRjid)
            } catch (e: Exception) {
                log("[!] Gagal mengambil metadata $itemRjid: ${e.message}")
                updateItem(itemRjid) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        statusText = "Gagal mengambil metadata",
                        error = e.message
                    )
                }
                continue
            }

            val tracks = TrackDiscoveryService.discoverAllTracks(itemRjid)
            log("  [i] Ditemukan ${tracks.size} track (${tracks.joinToString { it.name }})")

            updateItem(itemRjid) {
                it.copy(
                    title = meta.title,
                    cv = meta.cv,
                    circle = meta.circle,
                    genre = meta.genre,
                    ageRating = meta.ageRating,
                    coverUrl = meta.coverUrl,
                    tracks = tracks
                )
            }

            // 3. Download Cover
            log("  [1/3] Mengunduh cover image...")
            val coverFile = File(tempDir, "${itemRjid}_cover.jpg")
            AudioDownloader.downloadImage(meta.coverUrl, coverFile)

            // 4. Download Tracks
            val downloadedTracks = mutableListOf<File>()
            var isFailed = false
            val isHls = tracks.any { it.url.endsWith(".m3u8", ignoreCase = true) }
            val fileExtension = if (isHls) "m4a" else "mp3"

            for (tIdx in tracks.indices) {
                val track = tracks[tIdx]
                val trackExt = if (track.url.endsWith(".m3u8", ignoreCase = true)) "m4a" else "mp3"
                val trackFile = File(tempDir, "${itemRjid}_t${tIdx + 1}.$trackExt")

                updateItem(itemRjid) {
                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        statusText = "Mengunduh ${track.name} [${tIdx + 1}/${tracks.size}]"
                    )
                }

                log("  [2/3] Mengunduh ${track.name} [${tIdx + 1}/${tracks.size}]...")

                val success = AudioDownloader.downloadTrack(
                    url = track.url,
                    destFile = trackFile,
                    tempDir = tempDir,
                    parallelConnections = parallelConn,
                    onProgress = { pct, speed, eta, downStr, totStr ->
                        val overallPct = (tIdx.toFloat() + pct) / tracks.size.toFloat()
                        updateItem(itemRjid) {
                            it.copy(
                                progress = overallPct,
                                speed = speed,
                                eta = eta,
                                downloadedSize = downStr,
                                totalSize = totStr,
                                statusText = "Mengunduh ${track.name} [${tIdx + 1}/${tracks.size}] (${(pct * 100).toInt()}%)"
                            )
                        }
                        val liveTotal = _queueState.value.size
                        updateNotification(
                            "[$currentNum/$liveTotal] ${meta.title} (${(overallPct * 100).toInt()}%)",
                            (overallPct * 100).toInt(),
                            false
                        )
                    },
                    onLog = { logMsg ->
                        log("    $logMsg")
                    }
                )

                if (success && trackFile.exists() && trackFile.length() > 0) {
                    downloadedTracks.add(trackFile)
                } else {
                    isFailed = true
                    log("  [!] Gagal mengunduh track ${track.name}")
                    break
                }
            }

            if (isFailed || downloadedTracks.isEmpty()) {
                updateItem(itemRjid) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        statusText = "Gagal mengunduh audio",
                        error = "Gagal mengunduh track audio"
                    )
                }
                log("[ERROR] Gagal memproses $itemRjid")
                continue
            }

            // 5. Finalizing & Metadata Tagging
            log("  [3/3] Menyematkan metadata & cover art...")
            updateItem(itemRjid) {
                it.copy(
                    status = DownloadStatus.PROCESSING,
                    statusText = "Menyematkan metadata & cover art..."
                )
            }

            val filename = if (useDetailedFilename) {
                "[$itemRjid] ${sanitizeFilename(meta.title)}.$fileExtension"
            } else {
                "$itemRjid.$fileExtension"
            }

            val finalOutputFile = File(downloadDir, filename)
            if (downloadedTracks.size == 1) {
                downloadedTracks[0].copyTo(finalOutputFile, overwrite = true)
            } else {
                finalOutputFile.outputStream().use { out ->
                    for (tFile in downloadedTracks) {
                        tFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            }

            AudioTagger.tagAudioFile(
                audioFile = finalOutputFile,
                coverFile = coverFile,
                title = meta.title,
                artist = meta.cv,
                album = meta.circle,
                genre = meta.genre,
                comment = "Downloaded with JapaneseASMR by SayMaven (https://github.com/SayMaven)"
            )

            val fileSizeStr = AudioDownloader.formatFileSize(finalOutputFile.length())
            val dateStr = AudioStorageHelper.formatDateForDisplay(System.currentTimeMillis())

            historyDao.insertHistory(
                HistoryEntity(
                    rjid = itemRjid,
                    title = meta.title,
                    cv = meta.cv,
                    circle = meta.circle,
                    genre = meta.genre,
                    ageRating = meta.ageRating,
                    coverUrl = meta.coverUrl,
                    localFilePath = finalOutputFile.absolutePath,
                    downloadDate = dateStr,
                    fileSize = fileSizeStr
                )
            )

            updateItem(itemRjid) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    statusText = "Selesai diunduh",
                    progress = 1f,
                    speed = "-",
                    eta = "-",
                    downloadedSize = fileSizeStr
                )
            }

            log("[SUCCESS] Selesai: ${finalOutputFile.absolutePath} ($fileSizeStr)")

            downloadedTracks.forEach { it.delete() }
            coverFile.delete()
        }

        log("===================================================")
        log("[i] Semua antrean telah selesai diproses.")
        _isDownloading.value = false
    }

    private fun updateItem(rjid: String, transform: (DownloadQueueItem) -> DownloadQueueItem) {
        val current = _queueState.value.toMutableList()
        val index = current.indexOfFirst { it.rjid.equals(rjid, ignoreCase = true) }
        if (index != -1) {
            current[index] = transform(current[index])
            _queueState.value = current
        }
    }

    private fun buildNotification(content: String, progress: Int, indeterminate: Boolean): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, JapaneseAsmrApp.CHANNEL_DOWNLOAD_ID)
            .setContentTitle("JapaneseASMR Downloader")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String, progress: Int, indeterminate: Boolean) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content, progress, indeterminate))
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
