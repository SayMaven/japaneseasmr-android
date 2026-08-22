package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    val themeMode = prefs.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    val dynamicColor = prefs.dynamicColorFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDynamicColor(enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            app.imageLoader.diskCache?.clear()
            app.imageLoader.memoryCache?.clear()
            val tempDir = File(app.cacheDir, "temp_downloads")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
        }
    }
}
