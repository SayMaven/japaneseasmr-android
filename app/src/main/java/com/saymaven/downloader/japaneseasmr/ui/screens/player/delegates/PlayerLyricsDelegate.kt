package com.saymaven.downloader.japaneseasmr.ui.screens.player.delegates

import android.content.Context
import androidx.media3.common.Player
import com.saymaven.downloader.japaneseasmr.data.local.AsmrDatabase
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.local.dao.HistoryDao
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.TranslationCacheEntity
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneApiService
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneFileNode
import com.saymaven.downloader.japaneseasmr.service.TranslationService
import com.saymaven.downloader.japaneseasmr.service.lyrics.LyricLine
import com.saymaven.downloader.japaneseasmr.service.lyrics.LyricsParser
import com.saymaven.downloader.japaneseasmr.service.lyrics.SubtitleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PlayerLyricsDelegate(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val prefs: PreferencesManager,
    private val historyDao: HistoryDao,
    private val getPlayer: () -> Player?,
    private val getCurrentRjId: () -> String?,
    private val getCurrentTitle: () -> String,
    private val getCurrentChapters: () -> List<ChapterItem>,
    private val getIsOnlineStreaming: () -> Boolean,
    private val getIsVirtualAlbum: () -> Boolean,
    private val getCurrentStreamTrackTitle: () -> String?,
    private val setCurrentStreamTrackTitle: (String) -> Unit,
    private val getCurrentAlbumHistory: () -> HistoryEntity?
) {
    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics = _currentLyrics.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(-1)
    val activeLyricIndex = _activeLyricIndex.asStateFlow()

    private val _availableSubtitles = MutableStateFlow<List<SubtitleSource>>(emptyList())
    val availableSubtitles = _availableSubtitles.asStateFlow()

    private val _selectedSubtitleSource = MutableStateFlow<SubtitleSource?>(null)
    val selectedSubtitleSource = _selectedSubtitleSource.asStateFlow()

    private val _isLyricsTranslated = MutableStateFlow(false)
    val isLyricsTranslated = _isLyricsTranslated.asStateFlow()

    private val _isTranslatingLyrics = MutableStateFlow(false)
    val isTranslatingLyrics = _isTranslatingLyrics.asStateFlow()

    private var lyricsJob: Job? = null
    private val workTreeCache = mutableMapOf<String, List<AsmrOneFileNode>>()
    private val subtitleTextCache = ConcurrentHashMap<String, String>()

    fun updateActiveLyricForPosition(trackPosMs: Long) {
        val lyrics = _currentLyrics.value
        if (lyrics.isNotEmpty()) {
            val idx = LyricsParser.findActiveIndex(lyrics, trackPosMs.coerceAtLeast(0L))
            if (_activeLyricIndex.value != idx) {
                _activeLyricIndex.value = idx
            }
        }
    }

    fun selectSubtitleSource(source: SubtitleSource) {
        _selectedSubtitleSource.value = source
        _isLyricsTranslated.value = false
        lyricsJob?.cancel()
        lyricsJob = scope.launch(Dispatchers.IO) {
            val content = fetchSubtitleContent(source)
            val parsed = if (!content.isNullOrBlank()) LyricsParser.parse(content, source.format) else emptyList()
            _currentLyrics.value = parsed
            _activeLyricIndex.value = -1
        }
    }

    fun toggleLyricsTranslation() {
        val current = _currentLyrics.value
        if (current.isEmpty()) return

        if (_isLyricsTranslated.value) {
            _isLyricsTranslated.value = false
            return
        }

        translateLyricsDirectly()
    }

    private fun translateLyricsDirectly() {
        val current = _currentLyrics.value
        if (current.isEmpty()) return

        if (current.all { !it.translatedText.isNullOrBlank() }) {
            _isLyricsTranslated.value = true
            return
        }

        scope.launch {
            _isTranslatingLyrics.value = true
            try {
                val targetLang = TranslationService.getActiveTargetLanguage(appContext)
                val llmUrl = prefs.llmApiUrlFlow.first()
                val llmKey = prefs.llmApiKeyFlow.first()
                val llmModel = prefs.llmModelFlow.first()
                val llmPrompt = prefs.llmPromptFlow.first()
                val concurrency = prefs.llmConcurrencyFlow.first()
                val autoSave = prefs.autoSaveTranslatedLyricsFlow.first()

                val texts = current.filter { it.translatedText.isNullOrBlank() }.map { it.text }
                val provider = TranslationService.getActiveProvider(appContext)
                val translatedMap = TranslationService.translateLyricsNaturally(
                    lines = texts,
                    targetLang = targetLang,
                    llmUrl = llmUrl,
                    llmKey = llmKey,
                    llmModel = llmModel,
                    llmPrompt = llmPrompt,
                    concurrency = concurrency,
                    provider = provider
                )

                if (translatedMap.isEmpty()) {
                    _isLyricsTranslated.value = false
                    return@launch
                }

                _currentLyrics.value = current.map { line ->
                    val existing = line.translatedText
                    val newlyTranslated = translatedMap[line.text.trim()] ?: translatedMap[line.text]
                    line.copy(translatedText = if (!existing.isNullOrBlank()) existing else newlyTranslated)
                }
                _isLyricsTranslated.value = _currentLyrics.value.any { !it.translatedText.isNullOrBlank() }

                if (autoSave && translatedMap.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val dao = AsmrDatabase.getDatabase(appContext).translationCacheDao()
                            val entities = translatedMap.map { (src, tr) ->
                                TranslationCacheEntity(
                                    sourceText = src,
                                    targetLang = targetLang,
                                    translatedText = tr
                                )
                            }
                            dao.insertTranslations(entities)
                        } catch (_: Exception) {}
                    }
                }
            } finally {
                _isTranslatingLyrics.value = false
            }
        }
    }

    private suspend fun autoTranslateLyricsIfEnabled() {
        val autoTranslate = prefs.autoTranslateLyricsFlow.first()
        if (autoTranslate && _currentLyrics.value.isNotEmpty() && !_isLyricsTranslated.value) {
            translateLyricsDirectly()
        }
    }

    fun loadLyricsForCurrentTrack() {
        lyricsJob?.cancel()
        lyricsJob = scope.launch(Dispatchers.IO) {
            val player = getPlayer()
            val curIdx = withContext(Dispatchers.Main) { player?.currentMediaItemIndex ?: 0 }
            val trackName = if (getIsOnlineStreaming()) {
                val chapTitle = getCurrentChapters().getOrNull(curIdx)?.title
                if (!chapTitle.isNullOrBlank()) {
                    setCurrentStreamTrackTitle(chapTitle)
                    chapTitle
                } else {
                    getCurrentStreamTrackTitle() ?: getCurrentTitle()
                }
            } else {
                val p = withContext(Dispatchers.Main) { player?.currentMediaItem?.mediaMetadata?.title?.toString() }
                p ?: getCurrentTitle()
            }

            val sources = mutableListOf<SubtitleSource>()
            var resolvedLocalAudioFile: File? = null
            var resolvedHistory: HistoryEntity? = null

            if (getIsOnlineStreaming()) {
                val rj = getCurrentRjId() ?: ""
                if (rj.isNotBlank()) {
                    val tree = workTreeCache[rj] ?: AsmrOneApiService.getWorkFileTree(rj).getOrNull()?.also {
                        workTreeCache[rj] = it
                    } ?: emptyList()
                    collectRemoteSubtitles(tree, sources)
                }
            } else {
                val mediaPath = withContext(Dispatchers.Main) { player?.currentMediaItem?.mediaId }
                val rj = getCurrentRjId()
                resolvedHistory = if (!rj.isNullOrBlank()) historyDao.getHistoryById(rj) else null
                resolvedLocalAudioFile = when {
                    !mediaPath.isNullOrBlank() && File(mediaPath).exists() -> File(mediaPath)
                    !resolvedHistory?.localFilePath.isNullOrBlank() && File(resolvedHistory!!.localFilePath).exists() -> File(resolvedHistory.localFilePath)
                    else -> null
                }

                val searchDir = resolvedLocalAudioFile?.parentFile ?: getCurrentAlbumHistory()?.localFilePath?.let { File(it) }?.let {
                    if (it.isDirectory) it else it.parentFile
                }
                if (searchDir != null && searchDir.exists()) {
                    val allLocal = mutableListOf<SubtitleSource>()
                    collectLocalSubtitles(searchDir, allLocal)
                    if (!getIsVirtualAlbum() && resolvedLocalAudioFile != null && resolvedLocalAudioFile.isFile) {
                        val baseName = resolvedLocalAudioFile.nameWithoutExtension.lowercase()
                        val rjLow = (rj ?: "").lowercase()
                        sources.addAll(
                            allLocal.filter { src ->
                                val fName = File(src.pathOrUrl).nameWithoutExtension.lowercase()
                                fName == baseName || (rjLow.isNotBlank() && fName.contains(rjLow))
                            }
                        )
                    } else {
                        sources.addAll(allLocal)
                    }
                }

                if (sources.isEmpty()) {
                    val lookupRj = (resolvedHistory?.linkedRjId?.takeIf { it.isNotBlank() } ?: rj).orEmpty()
                    if (lookupRj.isNotBlank()) {
                        val tree = workTreeCache[lookupRj] ?: AsmrOneApiService.getWorkFileTree(lookupRj).getOrNull()?.also {
                            workTreeCache[lookupRj] = it
                        } ?: emptyList()
                        val remoteSubs = mutableListOf<SubtitleSource>()
                        collectRemoteSubtitles(tree, remoteSubs)
                        if (remoteSubs.isNotEmpty()) {
                            if (getIsVirtualAlbum()) {
                                sources.addAll(remoteSubs)
                            } else {
                                val chaps = getCurrentChapters().ifEmpty {
                                    ChapterItem.fromJsonArray(resolvedHistory?.chaptersJson)
                                }
                                val mergedLyrics = LyricsParser.buildMergedWorkLyrics(
                                    rjid = lookupRj,
                                    chapters = chaps,
                                    remoteSources = remoteSubs
                                ) { src -> fetchSubtitleContent(src) }
                                if (mergedLyrics.isNotEmpty()) {
                                    val savedVtt = resolvedLocalAudioFile?.takeIf { it.isFile }?.let { audioFile ->
                                        runCatching {
                                            val vttFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.vtt")
                                            vttFile.writeText(LyricsParser.toWebVtt(mergedLyrics), Charsets.UTF_8)
                                            vttFile
                                        }.getOrNull()
                                    }
                                    val subSrc = if (savedVtt != null) {
                                        SubtitleSource(savedVtt.name, savedVtt.absolutePath, "vtt", isRemote = false)
                                    } else {
                                        remoteSubs.first()
                                    }
                                    _availableSubtitles.value = listOf(subSrc)
                                    _selectedSubtitleSource.value = subSrc
                                    _currentLyrics.value = mergedLyrics
                                    _isLyricsTranslated.value = false
                                    _activeLyricIndex.value = -1
                                    autoTranslateLyricsIfEnabled()
                                    return@launch
                                }
                            }
                        }
                    }
                }
            }

            _availableSubtitles.value = sources
            if (sources.isEmpty()) {
                _selectedSubtitleSource.value = null
                _currentLyrics.value = emptyList()
                _activeLyricIndex.value = -1
                return@launch
            }

            val best = LyricsParser.matchBestSubtitle(sources, trackName, curIdx)
            if (best == null) {
                _selectedSubtitleSource.value = null
                _currentLyrics.value = emptyList()
                _activeLyricIndex.value = -1
                return@launch
            }
            if (best.pathOrUrl == _selectedSubtitleSource.value?.pathOrUrl && _currentLyrics.value.isNotEmpty()) {
                return@launch
            }
            _selectedSubtitleSource.value = best
            val content = fetchSubtitleContent(best)
            val parsed = if (!content.isNullOrBlank()) LyricsParser.parse(content, best.format) else emptyList()
            _currentLyrics.value = parsed
            _isLyricsTranslated.value = false
            _activeLyricIndex.value = -1
            autoTranslateLyricsIfEnabled()
        }
    }

    private fun collectRemoteSubtitles(nodes: List<AsmrOneFileNode>, out: MutableList<SubtitleSource>) {
        val exts = setOf("vtt", "lrc", "srt")
        for (node in nodes) {
            if (node.type == "folder") {
                collectRemoteSubtitles(node.children, out)
            } else {
                val ext = node.title.substringAfterLast('.', "").lowercase()
                if (ext in exts && node.streamUrl.isNotBlank()) {
                    out.add(SubtitleSource(title = node.title, pathOrUrl = node.streamUrl, format = ext, isRemote = true))
                }
            }
        }
    }

    private fun collectLocalSubtitles(dir: File, out: MutableList<SubtitleSource>) {
        val exts = setOf("vtt", "lrc", "srt")
        dir.walkTopDown().maxDepth(3).forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in exts) {
                    out.add(SubtitleSource(title = file.name, pathOrUrl = file.absolutePath, format = ext, isRemote = false))
                }
            }
        }
    }

    private suspend fun fetchSubtitleContent(source: SubtitleSource): String? {
        subtitleTextCache[source.pathOrUrl]?.let { return it }
        return try {
            val content = if (!source.isRemote) {
                File(source.pathOrUrl).readText(Charsets.UTF_8)
            } else {
                AsmrOneApiService.fetchRawText(source.pathOrUrl)
            }
            if (!content.isNullOrBlank()) {
                subtitleTextCache[source.pathOrUrl] = content
            }
            content
        } catch (_: Exception) {
            null
        }
    }
}
