package com.saymaven.downloader.japaneseasmr.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class PreferencesManager(private val context: Context) {

    private val fastSp = context.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_COLOR_PALETTE = stringPreferencesKey("color_palette")
        val KEY_DOWNLOAD_DIR = stringPreferencesKey("download_dir")
        val KEY_PARALLEL_CONNECTIONS = intPreferencesKey("parallel_connections")
        val KEY_AUTO_CLIPBOARD = booleanPreferencesKey("auto_clipboard")
        val KEY_USE_DETAILED_FILENAME = booleanPreferencesKey("use_detailed_filename")
        val KEY_SHOW_CONSOLE = booleanPreferencesKey("show_console")

        // Audio Player Settings
        val KEY_EXCLUSIVE_USB_DAC = booleanPreferencesKey("exclusive_usb_dac")
        val KEY_EXCLUSIVE_AUDIO_FOCUS = booleanPreferencesKey("exclusive_audio_focus")
        val KEY_PAUSE_ON_UNPLUG = booleanPreferencesKey("pause_on_unplug")
        val KEY_SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val KEY_DEFAULT_SPEED = floatPreferencesKey("default_speed")

        // Playback State Persistence
        val KEY_LAST_PLAYED_RJID = stringPreferencesKey("last_played_rjid")
        val KEY_LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        val KEY_SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
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

    val colorPaletteFlow: Flow<ColorPalette> = context.dataStore.data.map { preferences ->
        val palStr = preferences[KEY_COLOR_PALETTE] ?: ColorPalette.DEFAULT.name
        try {
            ColorPalette.valueOf(palStr)
        } catch (e: Exception) {
            ColorPalette.DEFAULT
        }
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

    val showConsoleFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SHOW_CONSOLE] ?: true
    }

    // Audio Player Settings Flows
    val exclusiveUsbDacFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_EXCLUSIVE_USB_DAC] ?: true
    }

    val exclusiveAudioFocusFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_EXCLUSIVE_AUDIO_FOCUS] ?: true
    }

    val audioFocusFlow: Flow<Boolean> get() = exclusiveAudioFocusFlow

    val pauseOnUnplugFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PAUSE_ON_UNPLUG] ?: true
    }

    val skipSilenceFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SKIP_SILENCE] ?: false
    }

    val keepScreenOnFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_KEEP_SCREEN_ON] ?: false
    }

    val autoResumeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_RESUME] ?: true
    }

    val defaultSpeedFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEFAULT_SPEED] ?: 1.0f
    }

    val lastPlayedRjidFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_PLAYED_RJID]
    }

    val lastPositionMsFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_POSITION_MS] ?: 0L
    }

    val repeatModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_REPEAT_MODE] ?: 0
    }

    val shuffleModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SHUFFLE_MODE] ?: false
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

    suspend fun setColorPalette(palette: ColorPalette) {
        context.dataStore.edit { preferences ->
            preferences[KEY_COLOR_PALETTE] = palette.name
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

    suspend fun setShowConsole(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHOW_CONSOLE] = enabled
        }
    }

    suspend fun setExclusiveUsbDac(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EXCLUSIVE_USB_DAC] = enabled
        }
    }

    suspend fun setExclusiveAudioFocus(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EXCLUSIVE_AUDIO_FOCUS] = enabled
        }
    }

    suspend fun setAudioFocus(enabled: Boolean) = setExclusiveAudioFocus(enabled)

    suspend fun setPauseOnUnplug(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PAUSE_ON_UNPLUG] = enabled
        }
    }

    suspend fun setSkipSilence(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SKIP_SILENCE] = enabled
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setAutoResume(enabled: Boolean) {
        fastSp.edit().putBoolean("setting_auto_resume", enabled).apply()
        if (!enabled) {
            fastSp.edit()
                .remove("cached_rjid")
                .remove("cached_title")
                .remove("cached_artist")
                .remove("cached_cover")
                .remove("cached_pos")
                .remove("cached_duration")
                .remove("cached_specs")
                .apply()
        }
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_RESUME] = enabled
            if (!enabled) {
                preferences.remove(KEY_LAST_PLAYED_RJID)
                preferences.remove(KEY_LAST_POSITION_MS)
            }
        }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_SPEED] = speed
        }
    }

    suspend fun savePlaybackState(rjid: String?, positionMs: Long, repeatMode: Int, shuffleMode: Boolean) {
        val autoResume = fastSp.getBoolean("setting_auto_resume", true)
        if (autoResume) {
            context.dataStore.edit { preferences ->
                if (rjid != null) preferences[KEY_LAST_PLAYED_RJID] = rjid
                preferences[KEY_LAST_POSITION_MS] = positionMs
                preferences[KEY_REPEAT_MODE] = repeatMode
                preferences[KEY_SHUFFLE_MODE] = shuffleMode
            }
        }
    }
}
