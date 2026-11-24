package com.saymaven.downloader.japaneseasmr.ui.screens.player.delegates

import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao
import com.saymaven.downloader.japaneseasmr.data.local.dao.PlaylistDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.PlaylistItemEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.UserPlaylistEntity
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneApiService
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Delegate handling Room v6 user playlist CRUD and playlist playback resolution
 * so [com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel]
 * remains focused on Media3 playback coordination.
 */
class PlayerPlaylistDelegate(
    private val scope: CoroutineScope,
    private val prefs: PreferencesManager,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val onPlayLocalTrack: (HistoryEntity) -> Unit,
    private val onPlayOnlineWork: (
        rjid: String,
        workTitle: String,
        cv: String,
        coverUrl: String,
        tracks: List<AsmrOneTrack>
    ) -> Unit
) {

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            val entity = UserPlaylistEntity(name = name.trim())
            val id = playlistDao.insertPlaylist(entity)
            withContext(Dispatchers.Main) { onCreated(id) }
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        scope.launch(Dispatchers.IO) {
            playlistDao.updatePlaylistName(id, newName.trim())
        }
    }

    fun deletePlaylist(playlist: UserPlaylistEntity) {
        scope.launch(Dispatchers.IO) {
            playlistDao.deletePlaylist(playlist)
        }
    }

    fun addWorkToPlaylist(
        playlistId: Long,
        rjid: String,
        title: String,
        coverUrl: String? = null,
        circle: String? = null,
        cv: String? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val item = PlaylistItemEntity(
                playlistId = playlistId,
                rjid = rjid,
                title = title,
                coverUrl = coverUrl ?: "",
                circle = circle ?: "-",
                cv = cv ?: "-"
            )
            playlistDao.insertItem(item)
        }
    }

    fun removeWorkFromPlaylist(playlistId: Long, rjid: String) {
        scope.launch(Dispatchers.IO) {
            playlistDao.removeItem(playlistId, rjid)
        }
    }

    suspend fun getPlaylistIdsForWork(rjid: String): List<Long> = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistIdsForItem(rjid)
    }

    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>> {
        return playlistDao.getItemsForPlaylist(playlistId)
    }

    fun playPlaylistItem(item: PlaylistItemEntity) {
        scope.launch(Dispatchers.IO) {
            val local = historyDao.findByRjOrLinkedRj(item.rjid)
            if (local != null) {
                withContext(Dispatchers.Main) {
                    onPlayLocalTrack(local)
                }
            } else {
                val priority = prefs.audioFormatPriorityFlow.first()
                val tracksRes = AsmrOneApiService.getWorkTracks(item.rjid, priority)
                val tracks = tracksRes.getOrNull() ?: emptyList()
                if (tracks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onPlayOnlineWork(
                            item.rjid,
                            item.title,
                            item.cv,
                            item.coverUrl,
                            tracks
                        )
                    }
                }
            }
        }
    }
}
