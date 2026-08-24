package com.saymaven.downloader.japaneseasmr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient

class JapaneseAsmrApp : Application(), ImageLoaderFactory {

    companion object {
        const val CHANNEL_DOWNLOAD_ID = "japaneseasmr_download_channel"
        const val CHANNEL_DOWNLOAD_NAME = "JapaneseASMR Downloads"
        const val CHANNEL_PLAYBACK_ID = "japaneseasmr_playback_channel"
        const val CHANNEL_PLAYBACK_NAME = "JapaneseASMR Playback"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOAD_ID,
                CHANNEL_DOWNLOAD_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi kemajuan pengunduhan audio ASMR"
                setShowBadge(false)
            }

            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK_ID,
                CHANNEL_PLAYBACK_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi pemutar musik latar belakang ASMR"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(downloadChannel)
            notificationManager?.createNotificationChannel(playbackChannel)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.10)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Referer", "https://japaneseasmr.com")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
