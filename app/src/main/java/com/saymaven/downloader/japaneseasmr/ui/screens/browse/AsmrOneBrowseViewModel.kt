package com.saymaven.downloader.japaneseasmr.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.data.model.AsmrOneWork
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneApiService
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BrowseFilterType {
    ALL,
    POPULAR,
    SUBTITLE
}

class AsmrOneBrowseViewModel : ViewModel() {

    private val _works = MutableStateFlow<List<AsmrOneWork>>(emptyList())
    val works = _works.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow(BrowseFilterType.ALL)
    val filterType = _filterType.asStateFlow()

    private var currentPage = 1
    private val pageSize = 20
    private var searchDebounceJob: Job? = null

    init {
        loadInitial()
    }

    fun setFilterType(type: BrowseFilterType) {
        if (_filterType.value == type && _searchQuery.value.isEmpty()) return
        _filterType.value = type
        _searchQuery.value = ""
        loadInitial()
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(500)
            loadInitial()
        }
    }

    fun loadInitial() {
        currentPage = 1
        _hasMore.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            _isLoading.value = true
            val query = _searchQuery.value.trim()
            val result = if (query.isNotEmpty()) {
                val subtitleFlag = if (_filterType.value == BrowseFilterType.SUBTITLE) 1 else 0
                AsmrOneApiService.searchWorks(
                    keyword = query,
                    page = currentPage,
                    pageSize = pageSize,
                    subtitle = subtitleFlag
                )
            } else {
                when (_filterType.value) {
                    BrowseFilterType.POPULAR -> AsmrOneApiService.getPopularWorks(page = currentPage, pageSize = pageSize)
                    BrowseFilterType.SUBTITLE -> AsmrOneApiService.getWorks(page = currentPage, pageSize = pageSize, subtitle = 1)
                    BrowseFilterType.ALL -> AsmrOneApiService.getWorks(page = currentPage, pageSize = pageSize, subtitle = 0)
                }
            }

            result.onSuccess { list ->
                _works.value = list
                _hasMore.value = list.size >= pageSize
                _errorMessage.value = null
            }.onFailure { error ->
                _errorMessage.value = error.localizedMessage ?: "Failed to load works"
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoading.value || _isLoadingMore.value || !_hasMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            val nextPage = currentPage + 1
            val query = _searchQuery.value.trim()
            val result = if (query.isNotEmpty()) {
                val subtitleFlag = if (_filterType.value == BrowseFilterType.SUBTITLE) 1 else 0
                AsmrOneApiService.searchWorks(
                    keyword = query,
                    page = nextPage,
                    pageSize = pageSize,
                    subtitle = subtitleFlag
                )
            } else {
                when (_filterType.value) {
                    BrowseFilterType.POPULAR -> AsmrOneApiService.getPopularWorks(page = nextPage, pageSize = pageSize)
                    BrowseFilterType.SUBTITLE -> AsmrOneApiService.getWorks(page = nextPage, pageSize = pageSize, subtitle = 1)
                    BrowseFilterType.ALL -> AsmrOneApiService.getWorks(page = nextPage, pageSize = pageSize, subtitle = 0)
                }
            }

            result.onSuccess { newList ->
                if (newList.isEmpty()) {
                    _hasMore.value = false
                } else {
                    currentPage = nextPage
                    _works.value = _works.value + newList
                    _hasMore.value = newList.size >= pageSize
                }
            }.onFailure {
                // Keep existing items, stop more loading
            }
            _isLoadingMore.value = false
        }
    }

    fun enqueueDownload(work: AsmrOneWork): Boolean {
        val item = DownloadQueueItem(
            rjid = work.rjid,
            title = work.title,
            cv = work.cv,
            circle = work.circle,
            genre = "-",
            ageRating = work.ageRating,
            coverUrl = work.coverUrl
        )
        DownloadService.enqueue(listOf(item))
        return true
    }
}
