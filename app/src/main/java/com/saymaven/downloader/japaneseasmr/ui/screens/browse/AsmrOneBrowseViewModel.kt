package com.saymaven.downloader.japaneseasmr.ui.screens.browse

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.model.AsmrOneWork
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneApiService
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneFileNode
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneTrack
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.service.TranslationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BrowseTabType {
    ALL,
    POPULAR,
    RECOMMENDED
}

enum class AsmrSortField(val apiValue: String, val labelRes: Int) {
    RELEASE_DATE("create_date", R.string.browse_sort_release_date),
    ADDED_DATE("insert_date", R.string.browse_sort_added_date),
    CATALOG_DATE("release", R.string.browse_sort_catalog_date),
    REVIEWS("review_count", R.string.browse_sort_reviews),
    DL_COUNT("dl_count", R.string.browse_sort_sales),
    RANDOM("random", R.string.browse_sort_random),
    ALL_AGES("nsfw", R.string.browse_sort_all_ages),
    ID("id", R.string.browse_sort_id)
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _currentTab = MutableStateFlow(BrowseTabType.ALL)
    val currentTab = _currentTab.asStateFlow()

    private val _sortField = MutableStateFlow(AsmrSortField.RELEASE_DATE)
    val sortField = _sortField.asStateFlow()

    private val _sortDirection = MutableStateFlow(AsmrSortDirection.DESC)
    val sortDirection = _sortDirection.asStateFlow()

    // Pagination
    private val _currentPage = MutableStateFlow(1)
    val currentPage = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(50)
    val totalPages = _totalPages.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount = _totalCount.asStateFlow()

    private val pageSize = 20

    // Work detail & file tree for KikoFlu-style detail view
    private val _selectedWork = MutableStateFlow<AsmrOneWork?>(null)
    val selectedWork = _selectedWork.asStateFlow()

    private val _workTracks = MutableStateFlow<List<AsmrOneTrack>>(emptyList())
    val workTracks = _workTracks.asStateFlow()

    private val _fileTree = MutableStateFlow<List<AsmrOneFileNode>>(emptyList())
    val fileTree = _fileTree.asStateFlow()

    private val _isLoadingTracks = MutableStateFlow(false)
    val isLoadingTracks = _isLoadingTracks.asStateFlow()

    private val _tracksError = MutableStateFlow<String?>(null)
    val tracksError = _tracksError.asStateFlow()

    // Dynamic Title Translation
    private val _isTranslationActive = MutableStateFlow(false)
    val isTranslationActive = _isTranslationActive.asStateFlow()

    private val _translatedTitles = MutableStateFlow<Map<String, String>>(emptyMap())
    val translatedTitles = _translatedTitles.asStateFlow()

    private var searchDebounceJob: Job? = null
    private var translationJob: Job? = null

    init {
        loadPage(1)
    }

    fun setTab(tab: BrowseTabType) {
        if (_currentTab.value == tab && _searchQuery.value.isEmpty()) return
        _currentTab.value = tab
        _searchQuery.value = ""
        when (tab) {
            BrowseTabType.ALL -> {
                _sortField.value = AsmrSortField.RELEASE_DATE
                _sortDirection.value = AsmrSortDirection.DESC
            }
            BrowseTabType.POPULAR -> {
                _sortField.value = AsmrSortField.DL_COUNT
                _sortDirection.value = AsmrSortDirection.DESC
            }
            BrowseTabType.RECOMMENDED -> {
                _sortField.value = AsmrSortField.CATALOG_DATE
                _sortDirection.value = AsmrSortDirection.DESC
            }
        }
        _currentPage.value = 1
        loadPage(1)
    }

    fun setSort(field: AsmrSortField, direction: AsmrSortDirection) {
        if (_sortField.value == field && _sortDirection.value == direction) return
        _sortField.value = field
        _sortDirection.value = direction
        _currentPage.value = 1
        loadPage(1)
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(500)
            _currentPage.value = 1
            loadPage(1)
        }
    }

    fun goToPage(page: Int) {
        if (page < 1 || page == _currentPage.value) return
        _currentPage.value = page
        loadPage(page)
    }

    fun loadPage(page: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val query = _searchQuery.value.trim()
            val orderParam = _sortField.value.apiValue
            val sortParam = _sortDirection.value.apiValue

            val result = if (query.isNotEmpty()) {
                AsmrOneApiService.searchWorksPage(
                    keyword = query,
                    page = page,
                    pageSize = pageSize,
                    subtitle = 0
                )
            } else {
                AsmrOneApiService.getWorksPage(
                    page = page,
                    pageSize = pageSize,
                    order = orderParam,
                    sort = sortParam,
                    subtitle = 0
                )
            }

            result.onSuccess { pageData ->
                val filteredList = if (_sortField.value == AsmrSortField.ALL_AGES) {
                    pageData.works.filter { it.ageRating == "ALL" }
                } else {
                    pageData.works
                }
                _works.value = filteredList
                _errorMessage.value = null
                _totalCount.value = pageData.totalCount
                _totalPages.value = pageData.totalPages.coerceAtLeast(1)
            }.onFailure { error ->
                _errorMessage.value = error.localizedMessage ?: "Failed to load works"
            }
            _isLoading.value = false
        }
    }

    fun selectWork(work: AsmrOneWork?) {
        _selectedWork.value = work
        _workTracks.value = emptyList()
        _fileTree.value = emptyList()
        _tracksError.value = null
        if (work != null) {
            loadWorkDetails(work.id)
        }
    }

    fun loadWorkDetails(workId: Int) {
        viewModelScope.launch {
            _isLoadingTracks.value = true
            _tracksError.value = null

            // Load file tree and flat audio tracks concurrently
            val treeResult = AsmrOneApiService.getWorkFileTree(workId)
            val tracksResult = AsmrOneApiService.getWorkTracks(workId)

            treeResult.onSuccess { tree ->
                _fileTree.value = tree
            }
            tracksResult.onSuccess { tracks ->
                _workTracks.value = tracks
            }.onFailure { error ->
                _tracksError.value = error.localizedMessage ?: "Failed to load tracks"
            }

            _isLoadingTracks.value = false
        }
    }

    fun toggleTranslation(context: Context) {
        val nextState = !_isTranslationActive.value
        _isTranslationActive.value = nextState
        if (nextState) {
            translateCurrentWorks(context)
        }
    }

    fun translateCurrentWorks(context: Context) {
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val targetLang = TranslationService.getActiveTargetLanguage(context)
            if (targetLang == "ja") return@launch

            val currentMap = _translatedTitles.value.toMutableMap()
            val worksToTranslate = _works.value.filter { it.rjid !in currentMap }

            for (w in worksToTranslate) {
                try {
                    val translated = TranslationService.translateText(w.title, targetLang)
                    if (translated.isNotBlank()) {
                        currentMap[w.rjid] = translated
                        _translatedTitles.value = HashMap(currentMap)
                    }
                } catch (ignored: Exception) {
                }
            }
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
