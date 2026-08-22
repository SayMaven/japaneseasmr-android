package com.saymaven.downloader.japaneseasmr.ui.screens.queue

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saymaven.downloader.japaneseasmr.data.model.AsmrWork
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.data.remote.DLsiteScraper
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var previewJob: Job? = null

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

    fun addToQueue() {
        val ids = extractRjIds(_inputText.value)
        if (ids.isEmpty()) return

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
    }

    fun startDownload(context: Context) {
        DownloadService.startDownload(context)
    }

    fun clearQueue() {
        DownloadService.clearQueue()
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
