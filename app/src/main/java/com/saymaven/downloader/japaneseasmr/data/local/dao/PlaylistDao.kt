package com.saymaven.downloader.japaneseasmr.data.local.dao

import androidx.room.*
import com.saymaven.downloader.japaneseasmr.data.local.entity.PlaylistItemEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.UserPlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM user_playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<UserPlaylistEntity>>

    @Query("SELECT * FROM user_playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): UserPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: UserPlaylistEntity): Long

    @Query("UPDATE user_playlists SET name = :newName WHERE id = :id")
    suspend fun updatePlaylistName(id: Long, newName: String)

    @Delete
    suspend fun deletePlaylist(playlist: UserPlaylistEntity)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt DESC")
    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT DISTINCT rjid FROM playlist_items")
    fun getAllSavedRjids(): Flow<List<String>>

    @Query("SELECT playlistId FROM playlist_items WHERE rjid = :rjid")
    suspend fun getPlaylistIdsForItem(rjid: String): List<Long>

    @Query("SELECT playlistId FROM playlist_items WHERE rjid = :rjid")
    fun getPlaylistIdsForItemFlow(rjid: String): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND rjid = :rjid")
    suspend fun removeItem(playlistId: Long, rjid: String)

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    fun getItemCountFlow(playlistId: Long): Flow<Int>

    @Query("SELECT * FROM playlist_items")
    suspend fun getAllPlaylistItemsDirect(): List<PlaylistItemEntity>
}
