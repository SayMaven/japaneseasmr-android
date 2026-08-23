package com.saymaven.downloader.japaneseasmr.ui.screens.queue

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.model.AsmrWork
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
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
        val firstId = extractRjIds(text).firstOrNull()
        if (firstId == null) {
            _previewWork.value = null
            _isLoadingPreview.value = false
            return
        }

        previewJob = viewModelScope.launch {
            delay(500)
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
    }

    fun pasteFromClipboard(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                val extracted = extractRjIds(text)
                if (extracted.isNotEmpty()) {
                    onInputChanged(extracted.joinToString(", "))
                } else if (text.isNotBlank()) {
                    onInputChanged(text.trim())
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
                        val extracted = extractRjIds(text)
                        if (extracted.isNotEmpty()) {
                            lastProcessedClipboardText = text
                            onInputChanged(extracted.joinToString(", "))
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    fun addToQueue(): Boolean {
        val ids = extractRjIds(_inputText.value)
        if (ids.isEmpty()) return false

        val items = ids.map { id ->
            DownloadQueueItem(
                rjid = id,
                title = if (_previewWork.value?.rjid == id) _previewWork.value!!.title else "Memuat info...",
                cv = if (_previewWork.value?.rjid == id) _previewWork.value!!.cv else "-",
                circle = if (_previewWork.value?.rjid == id) _previewWork.value!!.circle else "-",
                genre = if (_previewWork.value?.rjid == id) _previewWork.value!!.genre else "-",
                ageRating = if (_previewWork.value?.rjid == id) _previewWork.value!!.ageRating else "-",
                coverUrl = "https://pic.weeabo0.xyz/${id}_img_main.jpg"
            )
        }

        DownloadService.enqueue(items)
        _inputText.value = ""
        _previewWork.value = null
        return true
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
        val pattern = Pattern.compile("(RJ\\d+|\\d{6,8})", Pattern.CASE_INSENSITIVE)

        for (token in tokens) {
            val t = token.trim()
            if (t.isEmpty()) continue
            val matcher = pattern.matcher(t)
            if (matcher.find()) {
                var id = matcher.group(1)!!.uppercase()
                if (!id.startsWith("RJ")) {
                    id = "RJ$id"
                }
                if (!result.contains(id)) {
                    result.add(id)
                }
            } else if (t.startsWith("RJ", ignoreCase = true)) {
                val id = t.uppercase()
                if (!result.contains(id)) {
                    result.add(id)
                }
            }
        }
        return result
    }
}
