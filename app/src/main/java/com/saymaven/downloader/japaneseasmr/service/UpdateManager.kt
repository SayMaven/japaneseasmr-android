package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.saymaven.downloader.japaneseasmr.BuildConfig
import com.saymaven.downloader.japaneseasmr.data.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object UpdateManager {

    private const val GITHUB_REPO = "SayMaven/japaneseasmr-android"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val RELEASES_LIST_URL = "https://api.github.com/repos/$GITHUB_REPO/releases"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(targetChannelDebug: Boolean = BuildConfig.DEBUG): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            // Coba fetch /releases/latest, jika 404 ambil rilis pertama dari /releases
            var jsonStr = fetchUrl(LATEST_RELEASE_URL)
            var releaseObj: JSONObject? = null

            if (jsonStr != null) {
                releaseObj = JSONObject(jsonStr)
            } else {
                val listJson = fetchUrl(RELEASES_LIST_URL)
                if (listJson != null) {
                    val arr = JSONArray(listJson)
                    if (arr.length() > 0) {
                        releaseObj = arr.getJSONObject(0)
                    }
                }
            }

            if (releaseObj == null) {
                return@withContext Result.failure(Exception("Tidak dapat menemukan data rilis dari GitHub"))
            }

            val tagName = releaseObj.optString("tag_name", "")
            val remoteVersion = extractVersionString(tagName)
            val currentVersion = BuildConfig.VERSION_NAME

            val isNewer = isVersionNewer(remoteVersion, currentVersion)
            if (!isNewer) {
                // Versi saat ini sudah terbaru
                return@withContext Result.success(null)
            }

            val releaseTitle = releaseObj.optString("name", "Pembaruan v$remoteVersion")
            val releaseNotes = releaseObj.optString("body", "").trim()
            val publishedAt = releaseObj.optString("published_at", "")
            val assetsArr = releaseObj.optJSONArray("assets") ?: JSONArray()

            data class AssetData(val name: String, val downloadUrl: String, val size: Long)
            val assets = mutableListOf<AssetData>()
            for (i in 0 until assetsArr.length()) {
                val item = assetsArr.getJSONObject(i)
                val name = item.optString("name", "")
                val downloadUrl = item.optString("browser_download_url", "")
                val size = item.optLong("size", 0L)
                if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    assets.add(AssetData(name, downloadUrl, size))
                }
            }

            if (assets.isEmpty()) {
                return@withContext Result.failure(Exception("Rilis v$remoteVersion ditemukan tetapi belum ada file APK yang dilampirkan."))
            }

            // Seleksi cerdas sesuai channel (Debug vs Release)
            val selectedAsset = if (targetChannelDebug) {
                assets.firstOrNull { it.name.contains("debug", ignoreCase = true) }
                    ?: assets.firstOrNull()!!
            } else {
                assets.firstOrNull { it.name.contains("release", ignoreCase = true) }
                    ?: assets.firstOrNull()!!
            }

            val isDebugAsset = selectedAsset.name.contains("debug", ignoreCase = true)

            val updateInfo = UpdateInfo(
                versionName = remoteVersion,
                tagName = tagName,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = selectedAsset.downloadUrl,
                fileName = selectedAsset.name,
                fileSize = selectedAsset.size,
                isDebugAsset = isDebugAsset,
                publishedAt = publishedAt
            )

            Result.success(updateInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchUrl(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JapaneseAsmrApp/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    fun extractVersionString(tag: String): String {
        // Hapus awalan "v", "v.", atau whitespace
        var clean = tag.trim()
        if (clean.startsWith("v.", ignoreCase = true)) {
            clean = clean.substring(2)
        } else if (clean.startsWith("v", ignoreCase = true)) {
            clean = clean.substring(1)
        }
        return clean.trim()
    }

    fun isVersionNewer(remoteVer: String, currentVer: String): Boolean {
        val remoteParts = parseVersionNumbers(remoteVer)
        val currentParts = parseVersionNumbers(currentVer)

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersionNumbers(ver: String): List<Int> {
        // Ambil hanya angka yang dipisahkan titik atau dash
        val base = ver.split("-")[0] // Abaikan suffix pre-release seperti "-beta"
        return base.split(".").mapNotNull { part ->
            part.filter { it.isDigit() }.toIntOrNull()
        }
    }

    suspend fun downloadUpdate(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Bersihkan file lama dalam direktori updates
            updatesDir.listFiles()?.forEach { oldFile ->
                try { oldFile.delete() } catch (_: Exception) {}
            }

            val destFile = File(updatesDir, fileName)
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "JapaneseAsmrApp/${BuildConfig.VERSION_NAME}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Gagal mengunduh file APK (HTTP ${response.code})"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("File unduhan kosong"))
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(destFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int

                outputStream.use { out ->
                    inputStream.use { `in` ->
                        while (`in`.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                        out.flush()
                    }
                }

                Result.success(destFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File): Result<Unit> {
        try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("Berkas APK tidak ditemukan di ${apkFile.absolutePath}"))
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}

