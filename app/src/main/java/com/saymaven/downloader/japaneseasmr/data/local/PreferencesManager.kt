package com.saymaven.downloader.japaneseasmr.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_DOWNLOAD_DIR = stringPreferencesKey("download_dir")
        val KEY_PARALLEL_CONNECTIONS = intPreferencesKey("parallel_connections")
        val KEY_AUTO_CLIPBOARD = booleanPreferencesKey("auto_clipboard")
        val KEY_USE_DETAILED_FILENAME = booleanPreferencesKey("use_detailed_filename")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DYNAMIC_COLOR] ?: true
    }

    val downloadDirFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DOWNLOAD_DIR]
    }

    val parallelConnectionsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_PARALLEL_CONNECTIONS] ?: 16
    }

    val autoClipboardFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_CLIPBOARD] ?: true
    }

    val useDetailedFilenameFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_USE_DETAILED_FILENAME] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setDownloadDir(path: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DOWNLOAD_DIR] = path
        }
    }

    suspend fun setParallelConnections(connections: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PARALLEL_CONNECTIONS] = connections
        }
    }

    suspend fun setAutoClipboard(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_CLIPBOARD] = enabled
        }
    }

    suspend fun setUseDetailedFilename(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USE_DETAILED_FILENAME] = enabled
        }
    }
}
