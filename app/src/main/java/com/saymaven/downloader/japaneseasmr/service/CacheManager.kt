package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import coil.Coil
import com.saymaven.downloader.japaneseasmr.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Manages application cache files inside [Context.getCacheDir] (including Coil image cache)
 * without touching Room databases (history/playlists) or downloaded ASMR audio files.
 */
object CacheManager {

    const val DEFAULT_CACHE_LIMIT_MB = 1024 // 1 GB
    const val MIN_CACHE_LIMIT_MB = 200
    const val MAX_CACHE_LIMIT_MB = 10240 // 10 GB

    fun calculateDirectorySizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            total += if (child.isDirectory) calculateDirectorySizeBytes(child) else child.length()
        }
        return total
    }

    suspend fun calculateCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        calculateDirectorySizeBytes(context.applicationContext.cacheDir)
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearAppCache(context: Context): Long = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val before = calculateDirectorySizeBytes(appCtx.cacheDir)
        try {
            val imageLoader = Coil.imageLoader(appCtx)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (e: Exception) {
            AppLogger.w("CacheManager", "Coil cache clear skipped: ${e.message}")
        }
        deleteChildrenRecursively(appCtx.cacheDir)
        val after = calculateDirectorySizeBytes(appCtx.cacheDir)
        (before - after).coerceAtLeast(0L)
    }

    /**
     * When [autoCleanEnabled] is true and [dir] exceeds [limitMb], deletes the oldest files
     * (by lastModified) until total size drops to 80% of [limitMb].
     */
    fun enforceAutoCleanOnDir(dir: File?, limitMb: Int, autoCleanEnabled: Boolean): Boolean {
        if (!autoCleanEnabled || dir == null || !dir.exists()) return false
        val limitBytes = limitMb.coerceIn(MIN_CACHE_LIMIT_MB, MAX_CACHE_LIMIT_MB).toLong() * 1024L * 1024L
        var currentSize = calculateDirectorySizeBytes(dir)
        if (currentSize <= limitBytes) return false

        val targetBytes = (limitBytes * 80L) / 100L
        val files = collectFilesSortedOldestFirst(dir)
        var deletedAny = false
        for (file in files) {
            if (currentSize <= targetBytes) break
            val len = file.length()
            if (file.delete()) {
                currentSize -= len
                deletedAny = true
            }
        }
        return deletedAny
    }

    suspend fun enforceAutoCleanIfNeeded(
        context: Context,
        limitMb: Int,
        autoCleanEnabled: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        enforceAutoCleanOnDir(context.applicationContext.cacheDir, limitMb, autoCleanEnabled)
    }

    fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.2f MB", mb)
        }
    }

    fun formatLimitLabel(limitMb: Int): String {
        return if (limitMb >= 1024) {
            val gb = limitMb / 1024.0
            if (limitMb % 1024 == 0) "${limitMb / 1024} GB" else String.format(Locale.US, "%.1f GB", gb)
        } else {
            "$limitMb MB"
        }
    }

    private fun deleteChildrenRecursively(dir: File?) {
        if (dir == null || !dir.exists()) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                deleteChildrenRecursively(child)
            }
            child.delete()
        }
    }

    private fun collectFilesSortedOldestFirst(dir: File): List<File> {
        val result = mutableListOf<File>()
        fun walk(current: File) {
            val children = current.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) walk(child) else result.add(child)
            }
        }
        walk(dir)
        return result.sortedBy { it.lastModified() }
    }
}
