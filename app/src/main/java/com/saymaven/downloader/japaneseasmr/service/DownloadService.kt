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
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.data.model.DownloadStatus
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import com.saymaven.downloader.japaneseasmr.data.remote.TrackDiscoveryService
import com.saymaven.downloader.japaneseasmr.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val NOTIFICATION_ID = 1001

        private val _queueState = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
        val queueState = _queueState.asStateFlow()

        private val _isDownloading = MutableStateFlow(false)
        val isDownloading = _isDownloading.asStateFlow()

        fun enqueue(items: List<DownloadQueueItem>) {
            val current = _queueState.value.toMutableList()
            val existingIds = current.map { it.rjid }.toSet()
            val newOnes = items.filter { !existingIds.contains(it.rjid) }
            current.addAll(newOnes)
            _queueState.value = current
        }

        fun clearQueue() {
            if (!_isDownloading.value) {
                _queueState.value = emptyList()
            }
        }

        fun startDownload(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Mempersiapkan unduhan...", 0, true))

        if (!_isDownloading.value) {
            _isDownloading.value = true
            serviceScope.launch {
                processQueue()
                _isDownloading.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun processQueue() {
        val database = AsmrDatabase.getDatabase(this)
        val historyDao = database.historyDao()

        val downloadDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(filesDir, "Music")
        downloadDir.mkdirs()

        val tempDir = File(cacheDir, "temp_downloads")
        tempDir.mkdirs()

        val items = _queueState.value
        for (i in items.indices) {
            val item = _queueState.value[i]
            if (item.status == DownloadStatus.COMPLETED) continue

            // 1. Fetch Metadata jika belum
            updateItem(i) {
                it.copy(
                    status = DownloadStatus.FETCHING_METADATA,
                    statusText = "Mengambil info DLsite..."
                )
            }
            updateNotification("Mengambil metadata: ${item.rjid}", 0, true)

            val meta = DLsiteScraper.fetchMetadata(item.rjid)
            val tracks = TrackDiscoveryService.discoverAllTracks(item.rjid)

            updateItem(i) {
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

            // 2. Download Cover
            val coverFile = File(tempDir, "${item.rjid}_cover.jpg")
            AudioDownloader.downloadImage(meta.coverUrl, coverFile)

            // 3. Download Tracks
            val downloadedTracks = mutableListOf<File>()
            var isFailed = false

            for (tIdx in tracks.indices) {
                val track = tracks[tIdx]
                val trackFile = File(tempDir, "${item.rjid}_t${tIdx + 1}.mp3")

                updateItem(i) {
                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        statusText = "Mengunduh ${track.name} [${tIdx + 1}/${tracks.size}]"
                    )
                }

                val success = AudioDownloader.downloadFile(
                    url = track.url,
                    destFile = trackFile
                ) { pct, speed, eta, downStr, totStr ->
                    val overallPct = (tIdx.toFloat() + pct) / tracks.size.toFloat()
                    updateItem(i) {
                        it.copy(
                            progress = overallPct,
                            speed = speed,
                            eta = eta,
                            downloadedSize = downStr,
                            totalSize = totStr,
                            statusText = "Mengunduh ${track.name} [${tIdx + 1}/${tracks.size}] (${(pct * 100).toInt()}%)"
                        )
                    }
                    updateNotification(
                        "[${i + 1}/${items.size}] ${meta.title} (${(overallPct * 100).toInt()}%)",
                        (overallPct * 100).toInt(),
                        false
                    )
                }

                if (success && trackFile.exists() && trackFile.length() > 0) {
                    downloadedTracks.add(trackFile)
                } else {
                    isFailed = true
                    break
                }
            }

            if (isFailed || downloadedTracks.isEmpty()) {
                updateItem(i) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        statusText = "Gagal mengunduh audio",
                        error = "Gagal mengunduh track audio"
                    )
                }
                continue
            }

            // 4. Finalizing & ID3 Tagging
            updateItem(i) {
                it.copy(
                    status = DownloadStatus.PROCESSING,
                    statusText = "Menyematkan ID3 tag & cover art..."
                )
            }

            val finalOutputFile = File(downloadDir, "${item.rjid}.mp3")
            // Gabungkan track jika lebih dari 1 atau copy track 1
            if (downloadedTracks.size == 1) {
                downloadedTracks[0].copyTo(finalOutputFile, overwrite = true)
            } else {
                // Byte stream concatenation for MP3 tracks
                finalOutputFile.outputStream().use { out ->
                    for (tFile in downloadedTracks) {
                        tFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            }

            AudioTagger.tagMp3File(
                audioFile = finalOutputFile,
                coverFile = coverFile,
                title = meta.title,
                artist = meta.cv,
                album = meta.circle,
                genre = meta.genre,
                comment = "JapaneseASMR"
            )

            // Simpan ke riwayat database
            val fileSizeStr = AudioDownloader.formatFileSize(finalOutputFile.length())
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            historyDao.insertHistory(
                HistoryEntity(
                    rjid = item.rjid,
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

            updateItem(i) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    statusText = "Selesai diunduh",
                    progress = 1f,
                    speed = "-",
                    eta = "-"
                )
            }

            // Bersihkan temp files
            downloadedTracks.forEach { it.delete() }
            coverFile.delete()
        }
    }

    private fun updateItem(index: Int, transform: (DownloadQueueItem) -> DownloadQueueItem) {
        val current = _queueState.value.toMutableList()
        if (index in current.indices) {
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
