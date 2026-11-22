package com.saymaven.downloader.japaneseasmr.ui.screens.browse

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    RANDOM("random", R.string.browse_sort_random),
    ALL_AGES("nsfw", R.string.browse_sort_all_ages),
    ID("id", R.string.browse_sort_id)
}

enum class AsmrSortDirection(val apiValue: String, val labelRes: Int) {
    DESC("desc", R.string.browse_sort_desc),
    ASC("asc", R.string.browse_sort_asc)
}

class AsmrOneBrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

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

    private val initialSortField = when (prefs.getInitialBrowseSortField()) {
        "insert_date" -> AsmrSortField.ADDED_DATE
        "release" -> AsmrSortField.CATALOG_DATE
        "review_count" -> AsmrSortField.REVIEWS
        "random" -> AsmrSortField.RANDOM
        "nsfw" -> AsmrSortField.ALL_AGES
        "id" -> AsmrSortField.ID
        else -> AsmrSortField.RELEASE_DATE
    }
    private val initialSortDir = if (prefs.getInitialBrowseSortAsc()) AsmrSortDirection.ASC else AsmrSortDirection.DESC

    private val _sortField = MutableStateFlow(initialSortField)
    val sortField = _sortField.asStateFlow()

    private val _sortDirection = MutableStateFlow(initialSortDir)
    val sortDirection = _sortDirection.asStateFlow()

    // Persistent View Mode (Grid / List)
    val browseViewMode = prefs.browseViewModeFlow.stateIn(viewModelScope, SharingStarted.Lazily, prefs.getInitialBrowseViewMode())
    fun setViewMode(mode: String) {
        viewModelScope.launch { prefs.setBrowseViewMode(mode) }
    }

    // Persistent Page Size (default 40)
    val browsePageSize = prefs.pageSizeFlow.stateIn(viewModelScope, SharingStarted.Lazily, prefs.getInitialPageSize())
    fun setPageSize(size: Int) {
        viewModelScope.launch {
            prefs.setPageSize(size)
            loadPage(1)
        }
    }

    // Blacklist & Content Filtering Flows
    val blockedTags = prefs.blockedTagsFlow.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())
    val blockedCvs = prefs.blockedCvsFlow.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())
    val blockedCircles = prefs.blockedCirclesFlow.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    // Work Card Display Toggles from UI Settings
    val showCardRating = prefs.cardShowRatingFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val showCardPrice = prefs.cardShowPriceFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val showCardDuration = prefs.cardShowDurationFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showCardSales = prefs.cardShowSalesFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val showCardReleaseDate = prefs.cardShowReleaseDateFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showCardCircle = prefs.cardShowCircleFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showCardSubtitle = prefs.cardShowSubtitleTagFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showCardAgeRating = prefs.cardShowAgeRatingFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)

    // Work Detail Display Toggles from UI Settings
    val showDetailRating = prefs.detailShowRatingFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val showDetailPrice = prefs.detailShowPriceFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val showDetailDuration = prefs.detailShowDurationFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showDetailSales = prefs.detailShowSalesFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val showDetailReleaseDate = prefs.detailShowReleaseDateFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showDetailTranslateBtn = prefs.detailShowTranslateButtonFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showDetailSubtitle = prefs.detailShowSubtitleTagFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val showDetailAgeRating = prefs.detailShowAgeRatingFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val audioFormatPriority = prefs.audioFormatPriorityFlow.stateIn(viewModelScope, SharingStarted.Lazily, "mp3_se,mp3,flac,wav,opus,m4a,aac")
    val autoTranslateDetail = prefs.autoTranslateDetailFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // Pagination
    private val _currentPage = MutableStateFlow(1)
    val currentPage = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages = _totalPages.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount = _totalCount.asStateFlow()

    // Scroll Position Preservation
    var scrollIndex: Int = 0
    var scrollOffset: Int = 0

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
        viewModelScope.launch {
            prefs.autoTranslateListsFlow.collect { autoActive ->
                if (autoActive && !_isTranslationActive.value) {
                    _isTranslationActive.value = true
                    translateCurrentWorks(getApplication())
                }
            }
        }
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
                _sortField.value = AsmrSortField.REVIEWS
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
        viewModelScope.launch {
            prefs.saveBrowseSort(field.apiValue, direction == AsmrSortDirection.ASC)
        }
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
            val isAllAges = _sortField.value == AsmrSortField.ALL_AGES
            val orderParam = if (isAllAges) "nsfw" else _sortField.value.apiValue
            val sortParam = if (isAllAges) "asc" else _sortDirection.value.apiValue
            val effectivePageSize = browsePageSize.value.coerceIn(20, 100)

            val result = if (query.isNotEmpty()) {
                AsmrOneApiService.searchWorksPage(
                    keyword = query,
                    page = page,
                    pageSize = effectivePageSize,
                    subtitle = 0
                )
            } else {
                AsmrOneApiService.getWorksPage(
                    page = page,
                    pageSize = effectivePageSize,
                    order = orderParam,
                    sort = sortParam,
                    subtitle = 0
                )
            }

            result.onSuccess { pageData ->
                val baseFiltered = if (isAllAges) {
                    pageData.works.filter { !it.nsfw }
                } else {
                    pageData.works
                }

                // Blacklist content filtering
                val bTags = blockedTags.value
                val bCvs = blockedCvs.value
                val bCircles = blockedCircles.value

                val cleanWorks = baseFiltered.filter { w ->
                    val tagHit = w.tags.any { t -> bTags.any { bt -> bt.equals(t, ignoreCase = true) } }
                    val cvHit = bCvs.any { bc -> w.cv.contains(bc, ignoreCase = true) }
                    val circleHit = bCircles.any { bc -> w.circle.contains(bc, ignoreCase = true) }
                    !tagHit && !cvHit && !circleHit
                }

                _works.value = cleanWorks
                _errorMessage.value = null
                _totalCount.value = pageData.totalCount
                _totalPages.value = pageData.totalPages.coerceAtLeast(1)
                if (_isTranslationActive.value) {
                    translateCurrentWorks(getApplication())
                }
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

    fun selectWorkByRjid(rjid: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = AsmrOneApiService.getWork(rjid)
            res.onSuccess { work ->
                selectWork(work)
            }.onFailure {
                onSearchQueryChange(rjid)
            }
            _isLoading.value = false
        }
    }

    fun loadWorkDetails(workId: Int) {
        viewModelScope.launch {
            _isLoadingTracks.value = true
            _tracksError.value = null

            // Load file tree and flat audio tracks concurrently
            val treeResult = AsmrOneApiService.getWorkFileTree(workId)
            val tracksResult = AsmrOneApiService.getWorkTracks(workId, audioFormatPriority.value)

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

    fun translateCurrentWorks(context: Context = getApplication()) {
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val targetLang = TranslationService.getActiveTargetLanguage(context)
            if (targetLang == "ja") return@launch

            val currentMap = _translatedTitles.value.toMutableMap()
            val worksToTranslate = _works.value.filter { it.rjid !in currentMap }
            if (worksToTranslate.isEmpty()) return@launch

            val titles = worksToTranslate.map { it.title }
            val batchResults = TranslationService.translateBatch(titles, targetLang)
            for (w in worksToTranslate) {
                val tr = batchResults[w.title]
                if (!tr.isNullOrBlank()) {
                    currentMap[w.rjid] = tr
                }
            }
            _translatedTitles.value = HashMap(currentMap)
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
