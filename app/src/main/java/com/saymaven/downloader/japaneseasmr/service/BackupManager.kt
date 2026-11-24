package com.saymaven.downloader.japaneseasmr.service

import android.content.Context
import android.net.Uri
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.PlaylistItemEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.UserPlaylistEntity
import com.saymaven.downloader.japaneseasmr.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupRestoreResult(
    val playlistsRestored: Int,
    val itemsRestored: Int,
    val historyRestored: Int
)

/**
 * Exports and restores user playlists, playlist items, and download history as JSON,
 * plus maintains an optional automatic mirror file in the download directory.
 */
object BackupManager {
    private const val BACKUP_VERSION = 1
    private const val AUTO_BACKUP_FILENAME = "japaneseasmr_backup_auto.json"

    suspend fun exportBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = AsmrDatabase.getDatabase(context.applicationContext)
        val playlistDao = db.playlistDao()
        val historyDao = db.historyDao()

        val playlists = playlistDao.getAllPlaylists().first()
        val allItems = playlistDao.getAllPlaylistItemsDirect()
        val itemsByPlaylist = allItems.groupBy { it.playlistId }
        val histories = historyDao.getAllHistoryDirect()

        val playlistsJson = JSONArray()
        for (playlist in playlists) {
            val itemsJson = JSONArray()
            for (item in itemsByPlaylist[playlist.id].orEmpty()) {
                itemsJson.put(
                    JSONObject().apply {
                        put("rjid", item.rjid)
                        put("title", item.title)
                        put("coverUrl", item.coverUrl)
                        put("circle", item.circle)
                        put("cv", item.cv)
                        put("addedAt", item.addedAt)
                    }
                )
            }
            playlistsJson.put(
                JSONObject().apply {
                    put("name", playlist.name)
                    put("createdAt", playlist.createdAt)
                    put("items", itemsJson)
                }
            )
        }

        val historyJson = JSONArray()
        for (h in histories) {
            historyJson.put(
                JSONObject().apply {
                    put("rjid", h.rjid)
                    put("title", h.title)
                    put("cv", h.cv)
                    put("circle", h.circle)
                    put("genre", h.genre)
                    put("ageRating", h.ageRating)
                    put("coverUrl", h.coverUrl)
                    put("localFilePath", h.localFilePath)
                    put("downloadDate", h.downloadDate)
                    put("fileSize", h.fileSize)
                    put("chaptersJson", h.chaptersJson ?: JSONObject.NULL)
                    put("translatedTitle", h.translatedTitle ?: JSONObject.NULL)
                    put("translatedChaptersJson", h.translatedChaptersJson ?: JSONObject.NULL)
                    put("sourceType", h.sourceType)
                    put("linkedRjId", h.linkedRjId ?: JSONObject.NULL)
                }
            )
        }

        JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("playlists", playlistsJson)
            put("history", historyJson)
        }.toString(2)
    }

    suspend fun exportToUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = exportBackupJson(context)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Cannot open output stream for backup URI")
        }
    }

    suspend fun importFromUri(context: Context, uri: Uri): Result<BackupRestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Cannot read backup file")
            importBackupJson(context, json)
        }
    }

    suspend fun importBackupJson(context: Context, jsonString: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        val db = AsmrDatabase.getDatabase(context.applicationContext)
        val playlistDao = db.playlistDao()
        val historyDao = db.historyDao()

        val root = JSONObject(jsonString)
        val existingPlaylists = playlistDao.getAllPlaylists().first().associateBy { it.name }.toMutableMap()
        val existingItems = playlistDao.getAllPlaylistItemsDirect()
            .groupBy { it.playlistId }
            .mapValues { (_, list) -> list.map { it.rjid }.toMutableSet() }
            .toMutableMap()

        var playlistsRestored = 0
        var itemsRestored = 0
        var historyRestored = 0

        val playlistsArr = root.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until playlistsArr.length()) {
            val pObj = playlistsArr.getJSONObject(i)
            val name = pObj.optString("name").trim()
            if (name.isBlank()) continue

            val existing = existingPlaylists[name]
            val targetPlaylistId = if (existing != null) {
                existing.id
            } else {
                val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())
                val newId = playlistDao.insertPlaylist(UserPlaylistEntity(name = name, createdAt = createdAt))
                existingPlaylists[name] = UserPlaylistEntity(id = newId, name = name, createdAt = createdAt)
                playlistsRestored++
                newId
            }

            val rjidSet = existingItems.getOrPut(targetPlaylistId) { mutableSetOf() }
            val itemsArr = pObj.optJSONArray("items") ?: JSONArray()
            for (j in 0 until itemsArr.length()) {
                val itemObj = itemsArr.getJSONObject(j)
                val rjid = itemObj.optString("rjid").trim()
                if (rjid.isBlank() || rjid in rjidSet) continue
                playlistDao.insertItem(
                    PlaylistItemEntity(
                        playlistId = targetPlaylistId,
                        rjid = rjid,
                        title = itemObj.optString("title", rjid),
                        coverUrl = itemObj.optString("coverUrl", ""),
                        circle = itemObj.optString("circle", "-"),
                        cv = itemObj.optString("cv", "-"),
                        addedAt = itemObj.optLong("addedAt", System.currentTimeMillis())
                    )
                )
                rjidSet.add(rjid)
                itemsRestored++
            }
        }

        val historyArr = root.optJSONArray("history") ?: JSONArray()
        for (i in 0 until historyArr.length()) {
            val hObj = historyArr.getJSONObject(i)
            val rjid = hObj.optString("rjid").trim()
            if (rjid.isBlank()) continue
            if (historyDao.getHistoryById(rjid) == null) {
                historyDao.insertHistory(
                    HistoryEntity(
                        rjid = rjid,
                        title = hObj.optString("title", rjid),
                        cv = hObj.optString("cv", "-"),
                        circle = hObj.optString("circle", "-"),
                        genre = hObj.optString("genre", "-"),
                        ageRating = hObj.optString("ageRating", "-"),
                        coverUrl = hObj.optString("coverUrl", ""),
                        localFilePath = hObj.optString("localFilePath", ""),
                        downloadDate = hObj.optString("downloadDate", ""),
                        fileSize = hObj.optString("fileSize", "-"),
                        chaptersJson = hObj.optString("chaptersJson").takeIf { it.isNotBlank() && it != "null" },
                        translatedTitle = hObj.optString("translatedTitle").takeIf { it.isNotBlank() && it != "null" },
                        translatedChaptersJson = hObj.optString("translatedChaptersJson").takeIf { it.isNotBlank() && it != "null" },
                        sourceType = hObj.optString("sourceType", "DL_SITE"),
                        linkedRjId = hObj.optString("linkedRjId").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
                historyRestored++
            }
        }

        BackupRestoreResult(playlistsRestored, itemsRestored, historyRestored)
    }

    suspend fun autoBackupToExternalIfPossible(context: Context) = withContext(Dispatchers.IO) {
        try {
            val dir = DownloadService.getDefaultDownloadDirectory()
            if (!dir.exists()) dir.mkdirs()
            if (!dir.canWrite()) return@withContext
            val outFile = File(dir, AUTO_BACKUP_FILENAME)
            outFile.writeText(exportBackupJson(context), Charsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.w("BackupManager", "Auto-backup skipped: ${e.message}")
        }
    }
}
