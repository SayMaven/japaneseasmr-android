package com.saymaven.downloader.japaneseasmr.ui.screens.player.delegates

import android.content.Context
import android.content.SharedPreferences
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.PlaylistItemEntity
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import com.saymaven.downloader.japaneseasmr.service.TranslationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerTranslationDelegate(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val prefs: PreferencesManager,
    private val sp: SharedPreferences,
    private val historyDao: HistoryDao,
    private val getCurrentRjId: () -> String?,
    private val getCurrentTitle: () -> String,
    private val getCurrentChapters: () -> List<ChapterItem>,
    private val getIsOnlineStreaming: () -> Boolean,
    private val onClearPlaylistEntityTranslations: () -> Unit
) {
    private val _isTranslationActive = MutableStateFlow(false)
    val isTranslationActive = _isTranslationActive.asStateFlow()

    private val _isPlaylistTranslationActive = MutableStateFlow(false)
    val isPlaylistTranslationActive = _isPlaylistTranslationActive.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating = _isTranslating.asStateFlow()

    private val _translatedTitle = MutableStateFlow<String?>(null)
    val translatedTitle = _translatedTitle.asStateFlow()

    private val _translatedChapters = MutableStateFlow<List<ChapterItem>>(emptyList())
    val translatedChapters = _translatedChapters.asStateFlow()

    private val _playlistTranslatedTitles = MutableStateFlow<Map<String, String>>(emptyMap())
    val playlistTranslatedTitles = _playlistTranslatedTitles.asStateFlow()

    private var translationJob: Job? = null
    private var pageTranslateJob: Job? = null
    private var lastTranslatedKey: String? = null

    fun observePreferences() {
        scope.launch {
            prefs.autoTranslatePlayerFlow.distinctUntilChanged().collect { autoActive ->
                _isTranslationActive.value = autoActive
                if (autoActive) {
                    val rj = getCurrentRjId()
                    if (!rj.isNullOrBlank()) {
                        loadOrFetchTranslations(rj)
                    }
                } else {
                    _translatedTitle.value = null
                    _translatedChapters.value = emptyList()
                }
            }
        }
        scope.launch {
            prefs.autoTranslateListsFlow.distinctUntilChanged().collect { autoActive ->
                _isPlaylistTranslationActive.value = autoActive
                if (!autoActive) {
                    _playlistTranslatedTitles.value = emptyMap()
                }
            }
        }
        scope.launch {
            var isFirstLang = true
            prefs.translationLanguageFlow.distinctUntilChanged().collect { _ ->
                if (isFirstLang) {
                    isFirstLang = false
                    return@collect
                }
                if (_isPlaylistTranslationActive.value) {
                    _playlistTranslatedTitles.value = emptyMap()
                }
                val rj = getCurrentRjId()
                if (_isTranslationActive.value && !rj.isNullOrBlank()) {
                    loadOrFetchTranslations(rj, forceOnline = true)
                }
            }
        }
    }

    fun preloadFromHistory(items: List<HistoryEntity>) {
        val preloaded = mutableMapOf<String, String>()
        for (item in items) {
            if (!item.translatedTitle.isNullOrBlank()) {
                preloaded[item.rjid] = item.translatedTitle
            }
        }
        if (preloaded.isNotEmpty()) {
            val merged = _playlistTranslatedTitles.value.toMutableMap()
            merged.putAll(preloaded)
            _playlistTranslatedTitles.value = merged
        }
    }

    fun resetPlayerTranslations() {
        _translatedTitle.value = null
        _translatedChapters.value = emptyList()
    }

    fun clearInMemoryTranslations() {
        _playlistTranslatedTitles.value = emptyMap()
        _translatedTitle.value = null
        _translatedChapters.value = emptyList()
        lastTranslatedKey = null
        onClearPlaylistEntityTranslations()
    }

    fun toggleTranslation() {
        val next = !_isTranslationActive.value
        _isTranslationActive.value = next
        scope.launch {
            if (next) {
                val rj = getCurrentRjId()
                if (!rj.isNullOrBlank()) {
                    loadOrFetchTranslations(rj, forceOnline = getIsOnlineStreaming())
                }
            } else {
                _translatedTitle.value = null
                _translatedChapters.value = emptyList()
            }
        }
    }

    fun togglePlaylistTranslation() {
        val next = !_isPlaylistTranslationActive.value
        _isPlaylistTranslationActive.value = next
        if (!next) {
            pageTranslateJob?.cancel()
            _playlistTranslatedTitles.value = emptyMap()
        }
    }

    fun translateCurrentPageTitles(items: List<HistoryEntity>, forceRefresh: Boolean = false) {
        if (items.isEmpty()) return
        pageTranslateJob?.cancel()
        pageTranslateJob = scope.launch(Dispatchers.IO) {
            try {
                val targetLang = TranslationService.getActiveTargetLanguage(appContext)
                val provider = TranslationService.getActiveProvider(appContext)

                val lastLang = sp.getString("last_playlist_trans_lang", null)
                val langChanged = lastLang != targetLang
                if (langChanged) {
                    sp.edit().putString("last_playlist_trans_lang", targetLang).apply()
                    if (lastLang != null) {
                        historyDao.clearAllTranslations()
                        clearInMemoryTranslations()
                    }
                }

                val effectiveForce = forceRefresh || langChanged
                val currentMap = _playlistTranslatedTitles.value.toMutableMap()
                val needOnline = mutableListOf<HistoryEntity>()
                for (item in items) {
                    if (!effectiveForce && !TranslationService.isPoisonedTranslation(item.title, item.translatedTitle, targetLang)) {
                        currentMap[item.rjid] = item.translatedTitle!!
                    } else if (effectiveForce || currentMap[item.rjid].isNullOrBlank()) {
                        needOnline.add(item)
                    }
                }
                if (currentMap.isNotEmpty()) {
                    _playlistTranslatedTitles.value = currentMap.toMap()
                }
                if (needOnline.isEmpty()) return@launch

                val titles = needOnline.map { it.title }
                val batchResult = TranslationService.translateBatch(titles, targetLang, provider)
                val updatedMap = _playlistTranslatedTitles.value.toMutableMap()

                needOnline.forEach { item ->
                    val tr = batchResult[item.title]
                    if (!tr.isNullOrBlank() && !TranslationService.isPoisonedTranslation(item.title, tr, targetLang)) {
                        updatedMap[item.rjid] = tr
                        if (item.translatedTitle != tr) {
                            historyDao.updateTranslations(item.rjid, tr, item.translatedChaptersJson)
                        }
                    }
                }
                _playlistTranslatedTitles.value = updatedMap.toMap()
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun translateCustomPlaylistTitles(items: List<PlaylistItemEntity>, forceRefresh: Boolean = false) {
        if (!_isPlaylistTranslationActive.value || items.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val targetLang = TranslationService.getActiveTargetLanguage(appContext)
            val provider = TranslationService.getActiveProvider(appContext)
            val currentMap = _playlistTranslatedTitles.value.toMutableMap()

            val needTranslation = if (forceRefresh) items else items.filter { currentMap[it.rjid].isNullOrBlank() }
            if (needTranslation.isEmpty()) return@launch

            val titles = needTranslation.map { it.title }
            val batchResult = TranslationService.translateBatch(titles, targetLang, provider)
            items.forEach { item ->
                val tr = batchResult[item.title]
                if (!tr.isNullOrBlank() && !TranslationService.isPoisonedTranslation(item.title, tr, targetLang)) {
                    currentMap[item.rjid] = tr
                }
            }
            _playlistTranslatedTitles.value = currentMap
        }
    }

    fun loadOrFetchTranslations(targetRjid: String? = getCurrentRjId(), forceOnline: Boolean = false) {
        if (targetRjid.isNullOrBlank()) {
            _translatedTitle.value = null
            _translatedChapters.value = emptyList()
            return
        }

        translationJob?.cancel()
        translationJob = scope.launch {
            try {
                val targetLang = TranslationService.getActiveTargetLanguage(appContext)
                val currentChaps = getCurrentChapters()
                val currentKey = "$targetRjid:$targetLang:${currentChaps.size}"

                if (!forceOnline &&
                    lastTranslatedKey == currentKey &&
                    !_translatedTitle.value.isNullOrBlank() &&
                    (currentChaps.isEmpty() || _translatedChapters.value.size == currentChaps.size)
                ) {
                    return@launch
                }

                val history = if (getIsOnlineStreaming()) null else withContext(Dispatchers.IO) { historyDao.getHistoryById(targetRjid) }
                val rawTitle = (history?.title ?: getCurrentTitle())
                    .replace("\\[$targetRjid\\]".toRegex(RegexOption.IGNORE_CASE), "")
                    .trim()

                val hasValidTitle = history != null && !TranslationService.isPoisonedTranslation(rawTitle, history.translatedTitle, targetLang)
                val parsedChaps = if (history != null && !history.translatedChaptersJson.isNullOrBlank()) {
                    ChapterItem.fromJsonArray(history.translatedChaptersJson)
                } else emptyList()
                val hasValidChapters = currentChaps.isEmpty() || (
                    parsedChaps.size == currentChaps.size &&
                    !parsedChaps.indices.all { TranslationService.isPoisonedTranslation(currentChaps[it].title, parsedChaps[it].title, targetLang) }
                )

                if (!forceOnline && hasValidTitle && hasValidChapters) {
                    _translatedTitle.value = history!!.translatedTitle
                    if (parsedChaps.isNotEmpty()) {
                        _translatedChapters.value = parsedChaps
                    }
                    lastTranslatedKey = currentKey
                    return@launch
                }
                if (hasValidTitle) {
                    _translatedTitle.value = history!!.translatedTitle
                }

                _isTranslating.value = true
                val provider = TranslationService.getActiveProvider(appContext)

                kotlinx.coroutines.withTimeoutOrNull(45_000L) {
                    coroutineScope {
                        val titleDeferred = if (!forceOnline && hasValidTitle) null else async(Dispatchers.IO) {
                            TranslationService.translateText(rawTitle, targetLang, provider)
                        }
                        val chapsDeferred = if (currentChaps.isNotEmpty() && (forceOnline || !hasValidChapters)) {
                            async(Dispatchers.IO) {
                                translateChaptersBatch(currentChaps, targetLang, provider)
                            }
                        } else null

                        val translatedTitleRes = titleDeferred?.await() ?: history?.translatedTitle ?: rawTitle
                        val validTitle = !TranslationService.isPoisonedTranslation(rawTitle, translatedTitleRes, targetLang)
                        if (validTitle) {
                            _translatedTitle.value = translatedTitleRes
                        }

                        val translatedChapsList = chapsDeferred?.await() ?: parsedChaps
                        val validChapters = translatedChapsList.isNotEmpty() &&
                            translatedChapsList.indices.any { i ->
                                i < currentChaps.size && !TranslationService.isPoisonedTranslation(currentChaps[i].title, translatedChapsList[i].title, targetLang)
                            }
                        if (validChapters) {
                            _translatedChapters.value = translatedChapsList
                        }

                        if (validTitle || validChapters) {
                            lastTranslatedKey = currentKey
                            val titleToSave = if (validTitle) translatedTitleRes else history?.translatedTitle
                            val chapsJson = if (validChapters) {
                                ChapterItem.toJsonArray(translatedChapsList)
                            } else history?.translatedChaptersJson

                            withContext(Dispatchers.IO) {
                                historyDao.updateTranslations(targetRjid, titleToSave, chapsJson)
                            }
                        } else {
                            _isTranslationActive.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isTranslating.value = false
            }
        }
    }

    private suspend fun translateChaptersBatch(
        chapters: List<ChapterItem>,
        targetLang: String,
        provider: String = TranslationService.getActiveProvider(appContext)
    ): List<ChapterItem> = withContext(Dispatchers.IO) {
        if (chapters.isEmpty()) return@withContext emptyList()
        val titles = chapters.map { it.title }
        val translatedMap = TranslationService.translateBatch(titles, targetLang, provider)
        chapters.map { orig ->
            orig.copy(title = translatedMap[orig.title] ?: orig.title)
        }
    }

    fun checkTranslateChaptersIfNeeded(
        targetRjid: String,
        chapters: List<ChapterItem>,
        forceOnline: Boolean = false
    ) {
        if (!_isTranslationActive.value || chapters.isEmpty()) return
        scope.launch {
            _isTranslating.value = true
            try {
                val targetLang = TranslationService.getActiveTargetLanguage(appContext)
                val history = if (getIsOnlineStreaming() || forceOnline) null else withContext(Dispatchers.IO) { historyDao.getHistoryById(targetRjid) }
                if (!forceOnline && !history?.translatedChaptersJson.isNullOrBlank()) {
                    val parsed = ChapterItem.fromJsonArray(history!!.translatedChaptersJson)
                    if (parsed.size == chapters.size) {
                        val isAllPoisoned = parsed.indices.all {
                            TranslationService.isPoisonedTranslation(chapters[it].title, parsed[it].title, targetLang)
                        }
                        if (!isAllPoisoned) {
                            _translatedChapters.value = parsed
                            lastTranslatedKey = "$targetRjid:$targetLang:${chapters.size}"
                            return@launch
                        }
                    }
                }
                val translatedChapsList = translateChaptersBatch(chapters, targetLang)
                _translatedChapters.value = translatedChapsList
                lastTranslatedKey = "$targetRjid:$targetLang:${chapters.size}"
                withContext(Dispatchers.IO) {
                    historyDao.updateTranslations(
                        targetRjid,
                        _translatedTitle.value ?: history?.translatedTitle,
                        ChapterItem.toJsonArray(translatedChapsList)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isTranslating.value = false
            }
        }
    }
}
