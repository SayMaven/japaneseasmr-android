package com.saymaven.downloader.japaneseasmr.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.model.AsmrOneWork
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneApiService
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneTrack
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BrowseTabType {
    POPULAR,
    RECOMMENDED
}

enum class AsmrSortField(val apiValue: String, val labelRes: Int) {
    RELEASE_DATE("create_date", R.string.browse_sort_release_date),
    DL_COUNT("dl_count", R.string.browse_sort_dl_count),
    RATING("rating", R.string.browse_sort_rating),
    REVIEWS("review_count", R.string.browse_sort_reviews)
}

enum class AsmrSortDirection(val apiValue: String, val labelRes: Int) {
    DESC("desc", R.string.browse_sort_desc),
    ASC("asc", R.string.browse_sort_asc)
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

    private val _currentTab = MutableStateFlow(BrowseTabType.POPULAR)
    val currentTab = _currentTab.asStateFlow()

    private val _sortField = MutableStateFlow(AsmrSortField.DL_COUNT)
    val sortField = _sortField.asStateFlow()

    private val _sortDirection = MutableStateFlow(AsmrSortDirection.DESC)
    val sortDirection = _sortDirection.asStateFlow()

    // Work detail & tracks for streaming / detail sheet
    private val _selectedWork = MutableStateFlow<AsmrOneWork?>(null)
    val selectedWork = _selectedWork.asStateFlow()

    private val _workTracks = MutableStateFlow<List<AsmrOneTrack>>(emptyList())
    val workTracks = _workTracks.asStateFlow()

    private val _isLoadingTracks = MutableStateFlow(false)
    val isLoadingTracks = _isLoadingTracks.asStateFlow()

    private val _tracksError = MutableStateFlow<String?>(null)
    val tracksError = _tracksError.asStateFlow()

    private var currentPage = 1
    private val pageSize = 20
    private var searchDebounceJob: Job? = null

    init {
        loadInitial()
    }

    fun setTab(tab: BrowseTabType) {
        if (_currentTab.value == tab && _searchQuery.value.isEmpty()) return
        _currentTab.value = tab
        _searchQuery.value = ""
        // Set default sort according to tab
        if (tab == BrowseTabType.POPULAR) {
            _sortField.value = AsmrSortField.DL_COUNT
        } else {
            _sortField.value = AsmrSortField.RATING
        }
        loadInitial()
    }

    fun setSort(field: AsmrSortField, direction: AsmrSortDirection) {
        if (_sortField.value == field && _sortDirection.value == direction) return
        _sortField.value = field
        _sortDirection.value = direction
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

    fun selectWork(work: AsmrOneWork?) {
        _selectedWork.value = work
        _workTracks.value = emptyList()
        _tracksError.value = null
        if (work != null) {
            loadTracks(work.id)
        }
    }

    fun loadTracks(workId: Int) {
        viewModelScope.launch {
            _isLoadingTracks.value = true
            _tracksError.value = null
            val result = AsmrOneApiService.getWorkTracks(workId)
            result.onSuccess { list ->
                _workTracks.value = list
            }.onFailure { error ->
                _tracksError.value = error.localizedMessage ?: "Failed to load tracks"
            }
            _isLoadingTracks.value = false
        }
    }

    fun loadInitial() {
        currentPage = 1
        _hasMore.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            _isLoading.value = true
            val query = _searchQuery.value.trim()
            val orderParam = _sortField.value.apiValue
            val sortParam = _sortDirection.value.apiValue

            val result = if (query.isNotEmpty()) {
                AsmrOneApiService.searchWorks(
                    keyword = query,
                    page = currentPage,
                    pageSize = pageSize,
                    subtitle = 0
                )
            } else {
                AsmrOneApiService.getWorks(
                    page = currentPage,
                    pageSize = pageSize,
                    order = orderParam,
                    sort = sortParam,
                    subtitle = 0
                )
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
            val orderParam = _sortField.value.apiValue
            val sortParam = _sortDirection.value.apiValue

            val result = if (query.isNotEmpty()) {
                AsmrOneApiService.searchWorks(
                    keyword = query,
                    page = nextPage,
                    pageSize = pageSize,
                    subtitle = 0
                )
            } else {
                AsmrOneApiService.getWorks(
                    page = nextPage,
                    pageSize = pageSize,
                    order = orderParam,
                    sort = sortParam,
                    subtitle = 0
                )
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
            genre = work.tags.joinToString(", "),
            ageRating = work.ageRating,
            coverUrl = work.coverUrl
        )
        DownloadService.enqueue(listOf(item))
        return true
    }
}
