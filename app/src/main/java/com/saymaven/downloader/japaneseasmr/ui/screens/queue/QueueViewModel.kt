package com.saymaven.downloader.japaneseasmr.ui.screens.queue

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.model.AsmrWork
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import com.saymaven.downloader.japaneseasmr.data.remote.UniversalCodeResolver
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class QueueViewModel : ViewModel() {

    val queueState = DownloadService.queueState
    val isDownloading = DownloadService.isDownloading

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private val _previewWork = MutableStateFlow<AsmrWork?>(null)
    val previewWork = _previewWork.asStateFlow()

    private val _isLoadingPreview = MutableStateFlow(false)
    val isLoadingPreview = _isLoadingPreview.asStateFlow()

    private val _showConsole = MutableStateFlow(true)
    val showConsole = _showConsole.asStateFlow()

    private var previewJob: Job? = null
    private var lastProcessedClipboardText: String? = null
    private var isFirstResume = true
    private var isPrefsLoaded = false

    fun initPreferences(context: Context) {
        if (isPrefsLoaded) return
        isPrefsLoaded = true
        viewModelScope.launch {
            val prefs = PreferencesManager(context)
            prefs.showConsoleFlow.collect {
                _showConsole.value = it
            }
        }
    }

    fun toggleShowConsole(context: Context) {
        val next = !_showConsole.value
        _showConsole.value = next
        viewModelScope.launch {
            PreferencesManager(context).setShowConsole(next)
        }
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
        fetchPreviewDebounced(text)
    }

    private fun fetchPreviewDebounced(text: String) {
        previewJob?.cancel()

        // Cek cepat secara sinkron
        val syncIds = extractRjIds(text)
        if (syncIds.isNotEmpty()) {
            val firstId = syncIds.first()
            previewJob = viewModelScope.launch {
                delay(350)
                _isLoadingPreview.value = true
                try {
                    val meta = DLsiteScraper.fetchMetadata(firstId)
                    _previewWork.value = meta
                } catch (e: Exception) {
                    _previewWork.value = null
                } finally {
                    _isLoadingPreview.value = false
                }
            }
            return
        }

        // Jika berupa tautan web yang membutuhkan resolusi HTML (misal japaneseasmr.com/150854/)
        if (text.contains("japaneseasmr.com", ignoreCase = true) || text.contains("http://") || text.contains("https://")) {
            previewJob = viewModelScope.launch {
                delay(400)
                _isLoadingPreview.value = true
                try {
                    val resolved = UniversalCodeResolver.resolveAllInputs(text)
                    val firstId = resolved.firstOrNull()
                    if (firstId != null) {
                        val meta = DLsiteScraper.fetchMetadata(firstId)
                        _previewWork.value = meta
                    } else {
                        _previewWork.value = null
                    }
                } catch (e: Exception) {
                    _previewWork.value = null
                } finally {
                    _isLoadingPreview.value = false
                }
            }
        } else {
            _previewWork.value = null
            _isLoadingPreview.value = false
        }
    }

    fun pasteFromClipboard(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotBlank()) {
                    viewModelScope.launch {
                        val resolved = UniversalCodeResolver.resolveAllInputs(text)
                        if (resolved.isNotEmpty()) {
                            onInputChanged(resolved.joinToString(", "))
                        } else {
                            onInputChanged(text.trim())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun checkAutoClipboard(context: Context) {
        viewModelScope.launch {
            val prefs = PreferencesManager(context)
            val isAutoClipboard = prefs.autoClipboardFlow.first()
            if (!isAutoClipboard) return@launch

            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: ""

                    if (isFirstResume) {
                        isFirstResume = false
                        lastProcessedClipboardText = text
                        return@launch
                    }

                    if (text.isNotBlank() && text != lastProcessedClipboardText) {
                        val resolved = UniversalCodeResolver.resolveAllInputs(text)
                        if (resolved.isNotEmpty()) {
                            lastProcessedClipboardText = text
                            onInputChanged(resolved.joinToString(", "))
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    fun addToQueue(): Boolean {
        val currentText = _inputText.value
        val syncIds = extractRjIds(currentText)
        if (syncIds.isNotEmpty()) {
            val items = syncIds.map { id ->
                DownloadQueueItem(
                    rjid = id,
                    title = if (_previewWork.value?.rjid == id) _previewWork.value!!.title else "Memuat info...",
                    cv = if (_previewWork.value?.rjid == id) _previewWork.value!!.cv else "-",
                    circle = if (_previewWork.value?.rjid == id) _previewWork.value!!.circle else "-",
                    genre = if (_previewWork.value?.rjid == id) _previewWork.value!!.genre else "-",
                    ageRating = if (_previewWork.value?.rjid == id) _previewWork.value!!.ageRating else "-",
                    coverUrl = if (_previewWork.value?.rjid == id && _previewWork.value!!.coverUrl.isNotBlank()) {
                        _previewWork.value!!.coverUrl
                    } else {
                        DLsiteScraper.resolveBestCoverUrl(id)
                    }
                )
            }
            DownloadService.enqueue(items)
            _inputText.value = ""
            _previewWork.value = null
            return true
        }

        // Fallback untuk URL yang membutuhkan resolusi HTML
        if (currentText.contains("japaneseasmr.com", ignoreCase = true) || currentText.contains("http", ignoreCase = true)) {
            viewModelScope.launch {
                val resolved = UniversalCodeResolver.resolveAllInputs(currentText)
                if (resolved.isNotEmpty()) {
                    val items = resolved.map { id ->
                        DownloadQueueItem(
                            rjid = id,
                            title = if (_previewWork.value?.rjid == id) _previewWork.value!!.title else "Memuat info...",
                            cv = if (_previewWork.value?.rjid == id) _previewWork.value!!.cv else "-",
                            circle = if (_previewWork.value?.rjid == id) _previewWork.value!!.circle else "-",
                            genre = if (_previewWork.value?.rjid == id) _previewWork.value!!.genre else "-",
                            ageRating = if (_previewWork.value?.rjid == id) _previewWork.value!!.ageRating else "-",
                            coverUrl = if (_previewWork.value?.rjid == id && _previewWork.value!!.coverUrl.isNotBlank()) {
                                _previewWork.value!!.coverUrl
                            } else {
                                DLsiteScraper.resolveBestCoverUrl(id)
                            }
                        )
                    }
                    DownloadService.enqueue(items)
                    _inputText.value = ""
                    _previewWork.value = null
                }
            }
            return true
        }

        return false
    }

    fun startDownload(context: Context) {
        DownloadService.startDownload(context)
    }

    fun clearQueue() {
        DownloadService.clearQueue()
    }

    fun removeItem(rjid: String) {
        DownloadService.removeItem(rjid)
    }

    private fun extractRjIds(input: String): List<String> {
        val tokens = input.split(Regex("[\\s,;]+"))
        val result = mutableListOf<String>()

        for (token in tokens) {
            val t = token.trim()
            if (t.isEmpty()) continue

            // 1. Tautan asmr.one (misal asmr.one/work/RJ01220411 atau /work/01220411)
            if (t.contains("asmr.one", ignoreCase = true)) {
                val m = Pattern.compile("asmr\\.one/(?:work|api/work)/([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE).matcher(t)
                if (m.find()) {
                    var code = m.group(1)!!.uppercase()
                    if (!code.startsWith("RJ")) code = "RJ$code"
                    if (!result.contains(code)) result.add(code)
                    continue
                }
            }

            // 2. Tautan DLsite
            if (t.contains("dlsite.com", ignoreCase = true)) {
                val m = Pattern.compile("product_id/([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE).matcher(t)
                if (m.find()) {
                    var code = m.group(1)!!.uppercase()
                    if (!code.startsWith("RJ") && code.matches(Regex("\\d+"))) code = "RJ$code"
                    if (!result.contains(code)) result.add(code)
                    continue
                }
            }

            // 3. Kode RJ / VJ / BJ atau angka numerik 5-8 digit
            val p = Pattern.compile("((?:RJ|VJ|BJ)\\d+|\\b\\d{5,8}\\b)", Pattern.CASE_INSENSITIVE)
            val m = p.matcher(t)
            if (m.find()) {
                var id = m.group(1)!!.uppercase()
                if (id.matches(Regex("^\\d+$"))) {
                    id = "RJ$id"
                }
                if (!result.contains(id)) {
                    result.add(id)
                }
            } else if (t.matches(Regex("^(?:RJ|VJ|BJ)\\d+$", RegexOption.IGNORE_CASE))) {
                val id = t.uppercase()
                if (!result.contains(id)) {
                    result.add(id)
                }
            }
        }
        return result
    }
}
