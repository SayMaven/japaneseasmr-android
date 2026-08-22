package com.saymaven.downloader.japaneseasmr.data.repository

import com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.model.AsmrWork
import com.saymaven.downloader.japaneseasmr.data.model.TrackInfo
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import com.saymaven.downloader.japaneseasmr.data.remote.TrackDiscoveryService
import kotlinx.coroutines.flow.Flow

class AsmrRepository(private val historyDao: HistoryDao) {

    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    fun searchHistory(query: String): Flow<List<HistoryEntity>> = historyDao.searchHistory(query)

    suspend fun getWorkMetadata(rjid: String): AsmrWork {
        return DLsiteScraper.fetchMetadata(rjid)
    }

    suspend fun discoverTracks(rjid: String): List<TrackInfo> {
        return TrackDiscoveryService.discoverAllTracks(rjid)
    }

    suspend fun saveHistory(history: HistoryEntity) {
        historyDao.insertHistory(history)
    }

    suspend fun deleteHistory(history: HistoryEntity) {
        historyDao.deleteHistory(history)
    }

    suspend fun clearAllHistory() {
        historyDao.clearAll()
    }
}
