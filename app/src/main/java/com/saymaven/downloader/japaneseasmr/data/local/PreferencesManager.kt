package com.saymaven.downloader.japaneseasmr.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import com.saymaven.downloader.japaneseasmr.util.SecurePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class PreferencesManager(private val context: Context) {

    private val fastSp = context.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_COLOR_PALETTE = stringPreferencesKey("color_palette")
        val KEY_DOWNLOAD_DIR = stringPreferencesKey("download_dir")
        val KEY_IMPORTED_DIRS = stringSetPreferencesKey("imported_audio_directories")
        val KEY_PARALLEL_CONNECTIONS = intPreferencesKey("parallel_connections")
        val KEY_AUTO_CLIPBOARD = booleanPreferencesKey("auto_clipboard")
        val KEY_USE_DETAILED_FILENAME = booleanPreferencesKey("use_detailed_filename")
        val KEY_SHOW_CONSOLE = booleanPreferencesKey("show_console")
        val KEY_CREATE_CUE_SHEET = booleanPreferencesKey("create_cue_sheet")
        val KEY_MERGE_DOWNLOADED_AUDIO = booleanPreferencesKey("merge_downloaded_audio")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_ui_language")
        val KEY_TRANSLATION_LANGUAGE = stringPreferencesKey("translation_language")
        val KEY_IS_TRANSLATION_ACTIVE = booleanPreferencesKey("is_translation_active")
        val KEY_TRANSLATION_ENGINE_VERSION = intPreferencesKey("translation_engine_version")
        val KEY_TRANSLATION_PROVIDER = stringPreferencesKey("translation_provider")
        val KEY_AUTO_SAVE_TRANSLATED_LYRICS = booleanPreferencesKey("auto_save_translated_lyrics")
        val KEY_AUTO_TRANSLATE_LYRICS = booleanPreferencesKey("auto_translate_lyrics")
        val KEY_AUTO_TRANSLATE_DETAIL = booleanPreferencesKey("auto_translate_detail")
        val KEY_AUTO_TRANSLATE_LISTS = booleanPreferencesKey("auto_translate_lists")
        val KEY_AUTO_TRANSLATE_PLAYER = booleanPreferencesKey("auto_translate_player")

        // Audio Player Settings
        val KEY_EXCLUSIVE_USB_DAC = booleanPreferencesKey("exclusive_usb_dac")
        val KEY_EXCLUSIVE_USB_VOLUME = floatPreferencesKey("exclusive_usb_volume")
        val KEY_EXCLUSIVE_AUDIO_FOCUS = booleanPreferencesKey("exclusive_audio_focus")
        val KEY_PAUSE_ON_UNPLUG = booleanPreferencesKey("pause_on_unplug")
        val KEY_SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val KEY_DEFAULT_SPEED = floatPreferencesKey("default_speed")

        const val PREF_KEY_USB_VOLUME = "cached_exclusive_usb_volume"
        const val DEFAULT_USB_VOLUME = 0.25f

        // Playback State Persistence
        val KEY_LAST_PLAYED_RJID = stringPreferencesKey("last_played_rjid")
        val KEY_LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        val KEY_SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val KEY_SHOW_REMAINING_TIME = booleanPreferencesKey("show_remaining_time")

        // View Mode & Layout Persistence
        val KEY_HOME_VIEW_MODE = stringPreferencesKey("home_view_mode")
        val KEY_BROWSE_VIEW_MODE = stringPreferencesKey("browse_view_mode")
        val KEY_HOME_SORT_FIELD = stringPreferencesKey("home_sort_field")
        val KEY_HOME_SORT_ASC = booleanPreferencesKey("home_sort_asc")
        val KEY_BROWSE_SORT_FIELD = stringPreferencesKey("browse_sort_field")
        val KEY_BROWSE_SORT_ASC = booleanPreferencesKey("browse_sort_asc")
        val KEY_PAGE_SIZE = intPreferencesKey("browse_page_size")

        // UI Settings Toggles (Work Card & Detail Display)
        val KEY_WORK_CARD_SIZE = stringPreferencesKey("work_card_size")
        val KEY_WORK_CARD_FONT_SCALE = stringPreferencesKey("work_card_font_scale")
        val KEY_CARD_SHOW_RATING = booleanPreferencesKey("card_show_rating")
        val KEY_CARD_SHOW_PRICE = booleanPreferencesKey("card_show_price")
        val KEY_CARD_SHOW_DURATION = booleanPreferencesKey("card_show_duration")
        val KEY_CARD_SHOW_SALES = booleanPreferencesKey("card_show_sales")
        val KEY_CARD_SHOW_RELEASE_DATE = booleanPreferencesKey("card_show_release_date")
        val KEY_CARD_SHOW_CIRCLE = booleanPreferencesKey("card_show_circle")
        val KEY_CARD_SHOW_SUBTITLE_TAG = booleanPreferencesKey("card_show_subtitle_tag")
        val KEY_CARD_SHOW_AGE_RATING = booleanPreferencesKey("card_show_age_rating")

        val KEY_DETAIL_SHOW_RATING = booleanPreferencesKey("detail_show_rating")
        val KEY_DETAIL_SHOW_PRICE = booleanPreferencesKey("detail_show_price")
        val KEY_DETAIL_SHOW_DURATION = booleanPreferencesKey("detail_show_duration")
        val KEY_DETAIL_SHOW_SALES = booleanPreferencesKey("detail_show_sales")
        val KEY_DETAIL_SHOW_EXTERNAL_LINKS = booleanPreferencesKey("detail_show_external_links")
        val KEY_DETAIL_SHOW_RELEASE_DATE = booleanPreferencesKey("detail_show_release_date")
        val KEY_DETAIL_SHOW_TRANSLATE_BUTTON = booleanPreferencesKey("detail_show_translate_button")
        val KEY_DETAIL_SHOW_SUBTITLE_TAG = booleanPreferencesKey("detail_show_subtitle_tag")
        val KEY_DETAIL_SHOW_AGE_RATING = booleanPreferencesKey("detail_show_age_rating")
        val KEY_DETAIL_SHOW_RECOMMENDATIONS = booleanPreferencesKey("detail_show_recommendations")

        val KEY_PLAYER_BUTTON_ORDER = stringPreferencesKey("player_button_order")
        const val DEFAULT_PLAYER_BUTTON_ORDER = "repeat,sleep_timer,chapters,translate,shuffle,speed,volume,bookmark"

        // Privacy Mode (Anti-Malu)
        val KEY_PRIVACY_ENABLED = booleanPreferencesKey("privacy_enabled")
        val KEY_PRIVACY_BLUR_NOTIF = booleanPreferencesKey("privacy_blur_notif")
        val KEY_PRIVACY_BLUR_INAPP = booleanPreferencesKey("privacy_blur_inapp")
        val KEY_PRIVACY_MASK_TITLE = booleanPreferencesKey("privacy_mask_title")
        val KEY_PRIVACY_CUSTOM_MASK = stringPreferencesKey("privacy_custom_mask")

        // Audio Lanjutan
        val KEY_AUDIO_GAIN_DB = floatPreferencesKey("audio_gain_db")
        val KEY_AUDIO_FORMAT_PRIORITY = stringPreferencesKey("audio_format_priority")
        val KEY_AUDIO_PRELOAD_SECONDS = intPreferencesKey("audio_preload_seconds")

        // Blocked Items (Blacklist)
        val KEY_BLOCKED_TAGS = stringSetPreferencesKey("blocked_tags")
        val KEY_BLOCKED_CVS = stringSetPreferencesKey("blocked_cvs")
        val KEY_BLOCKED_CIRCLES = stringSetPreferencesKey("blocked_circles")

        // LLM Configuration
        val KEY_LLM_API_URL = stringPreferencesKey("llm_api_url")
        val KEY_LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val KEY_LLM_MODEL = stringPreferencesKey("llm_model")
        val KEY_LLM_PROMPT = stringPreferencesKey("llm_prompt")
        val KEY_LLM_CONCURRENCY = intPreferencesKey("llm_concurrency")
        const val DEFAULT_LLM_PROMPT = "You are a professional translator. Translate the following text into id. Output ONLY the translated text without any explanations, notes, or markdown code blocks."

        // Floating Lyrics (In-App above MiniPlayer)
        val KEY_FLOATING_LYRICS_ENABLED = booleanPreferencesKey("floating_lyrics_enabled")
        val KEY_FLOATING_LYRICS_FONT_SIZE = floatPreferencesKey("floating_lyrics_font_size")
        val KEY_FLOATING_LYRICS_OPACITY = floatPreferencesKey("floating_lyrics_opacity")
        val KEY_FLOATING_LYRICS_CORNER_RADIUS = floatPreferencesKey("floating_lyrics_corner_radius")
        val KEY_FLOATING_LYRICS_SHOW_TRANSLATION = booleanPreferencesKey("floating_lyrics_show_translation")
        val KEY_FLOATING_LYRICS_DISPLAY_MODE = stringPreferencesKey("floating_lyrics_display_mode")
        val KEY_FLOATING_LYRICS_LOCKED = booleanPreferencesKey("floating_lyrics_locked")

        fun getFastCachedUsbVolume(context: Context): Float {
            return try {
                val sp = context.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
                sp.getFloat(PREF_KEY_USB_VOLUME, DEFAULT_USB_VOLUME)
            } catch (e: Exception) {
                DEFAULT_USB_VOLUME
            }
        }

        fun setFastCachedUsbVolume(context: Context, volume: Float) {
            try {
                val sp = context.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
                sp.edit().putFloat(PREF_KEY_USB_VOLUME, volume.coerceIn(0.0f, 1.0f)).apply()
            } catch (ignored: Exception) {}
        }

        fun getFastCachedLanguage(context: Context): String {
            return try {
                val sp = context.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
                sp.getString("cached_app_language", "system") ?: "system"
            } catch (e: Exception) {
                "system"
            }
        }

        fun getFastCachedTranslationLanguage(context: Context): String {
            return try {
                val sp = context.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
                sp.getString("cached_trans_language", "auto") ?: "auto"
            } catch (e: Exception) {
                "auto"
            }
        }

        fun getFastCachedTranslationProvider(context: Context): String {
            return try {
                val sp = context.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
                sp.getString("cached_trans_provider", "google") ?: "google"
            } catch (e: Exception) {
                "google"
            }
        }

        data class FastLlmConfig(
            val url: String,
            val key: String,
            val model: String,
            val prompt: String,
            val concurrency: Int
        )

        fun getFastCachedLlmConfig(context: Context): FastLlmConfig {
            return try {
                val sp = context.getSharedPreferences("player_fast_cache", Context.MODE_PRIVATE)
                val legacyKey = sp.getString("cached_llm_key", "") ?: ""
                if (legacyKey.isNotBlank() && SecurePreferences.migratePlaintextKeyIfNeeded(context, legacyKey)) {
                    sp.edit().remove("cached_llm_key").apply()
                }
                FastLlmConfig(
                    url = sp.getString("cached_llm_url", "") ?: "",
                    key = SecurePreferences.getLlmApiKey(context),
                    model = sp.getString("cached_llm_model", "claude-haiku-5-5") ?: "claude-haiku-5-5",
                    prompt = sp.getString("cached_llm_prompt", DEFAULT_LLM_PROMPT) ?: DEFAULT_LLM_PROMPT,
                    concurrency = sp.getInt("cached_llm_concurrency", 2).coerceIn(1, 5)
                )
            } catch (_: Exception) {
                FastLlmConfig("", "", "claude-haiku-5-5", DEFAULT_LLM_PROMPT, 2)
            }
        }
    }

    fun getInitialThemeMode(): ThemeMode {
        val str = fastSp.getString("cached_theme_mode", null)
        if (str != null) {
            return try { ThemeMode.valueOf(str) } catch (e: Exception) { ThemeMode.SYSTEM }
        }
        return ThemeMode.SYSTEM
    }

    fun getInitialDynamicColor(): Boolean {
        return fastSp.getBoolean("cached_dynamic_color", false)
    }

    fun getInitialColorPalette(): ColorPalette {
        val str = fastSp.getString("cached_color_palette", null)
        if (str != null) {
            return try { ColorPalette.valueOf(str) } catch (e: Exception) { ColorPalette.DEFAULT }
        }
        return ColorPalette.DEFAULT
    }

    fun getInitialAppLanguage(): String {
        return getFastCachedLanguage(context)
    }

    val appLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val lang = preferences[KEY_APP_LANGUAGE] ?: "system"
        if (fastSp.getString("cached_app_language", null) != lang) {
            fastSp.edit().putString("cached_app_language", lang).apply()
        }
        lang
    }

    suspend fun setAppLanguage(lang: String) {
        fastSp.edit().putString("cached_app_language", lang).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_LANGUAGE] = lang
        }
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
        preferences[KEY_DYNAMIC_COLOR] ?: false
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
    }.distinctUntilChanged()

    val importedDirsFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_IMPORTED_DIRS] ?: emptySet()
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

    val createCueSheetFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_CREATE_CUE_SHEET] ?: false
    }

    val mergeDownloadedAudioFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_MERGE_DOWNLOADED_AUDIO] ?: true
    }

    fun getInitialMergeDownloadedAudio(): Boolean = fastSp.getBoolean("cached_merge_downloaded_audio", true)

    val translationLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val lang = preferences[KEY_TRANSLATION_LANGUAGE] ?: "auto"
        if (fastSp.getString("cached_trans_language", null) != lang) {
            fastSp.edit().putString("cached_trans_language", lang).apply()
        }
        lang
    }.distinctUntilChanged()

    val isTranslationActiveFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_TRANSLATION_ACTIVE] ?: false
    }.distinctUntilChanged()

    // Audio Player Settings Flows
    val exclusiveUsbDacFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_EXCLUSIVE_USB_DAC] ?: false
    }

    val exclusiveUsbVolumeFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_EXCLUSIVE_USB_VOLUME] ?: DEFAULT_USB_VOLUME
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
    }.distinctUntilChanged()

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

    val showRemainingTimeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SHOW_REMAINING_TIME] ?: false
    }

    // Persistent Layout View Modes (Grid vs List)
    val homeViewModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_HOME_VIEW_MODE] ?: "grid"
    }

    val browseViewModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_BROWSE_VIEW_MODE] ?: "grid"
    }

    fun getInitialHomeViewMode(): String = fastSp.getString("cached_home_view_mode", "grid") ?: "grid"
    fun getInitialBrowseViewMode(): String = fastSp.getString("cached_browse_view_mode", "grid") ?: "grid"

    suspend fun setHomeViewMode(mode: String) {
        fastSp.edit().putString("cached_home_view_mode", mode).apply()
        context.dataStore.edit { it[KEY_HOME_VIEW_MODE] = mode }
    }

    suspend fun setBrowseViewMode(mode: String) {
        fastSp.edit().putString("cached_browse_view_mode", mode).apply()
        context.dataStore.edit { it[KEY_BROWSE_VIEW_MODE] = mode }
    }

    // Persistent Sort Preferences
    val homeSortFieldFlow: Flow<String> = context.dataStore.data.map { it[KEY_HOME_SORT_FIELD] ?: "DATE" }
    val homeSortAscFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_HOME_SORT_ASC] ?: false }

    val browseSortFieldFlow: Flow<String> = context.dataStore.data.map { it[KEY_BROWSE_SORT_FIELD] ?: "release" }
    val browseSortAscFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_BROWSE_SORT_ASC] ?: false }

    fun getInitialBrowseSortField(): String = fastSp.getString("cached_browse_sort_field", "release") ?: "release"
    fun getInitialBrowseSortAsc(): Boolean = fastSp.getBoolean("cached_browse_sort_asc", false)

    suspend fun saveHomeSort(field: String, isAsc: Boolean) {
        fastSp.edit().putString("cached_home_sort_field", field).putBoolean("cached_home_sort_asc", isAsc).apply()
        context.dataStore.edit {
            it[KEY_HOME_SORT_FIELD] = field
            it[KEY_HOME_SORT_ASC] = isAsc
        }
    }

    suspend fun saveBrowseSort(field: String, isAsc: Boolean) {
        fastSp.edit().putString("cached_browse_sort_field", field).putBoolean("cached_browse_sort_asc", isAsc).apply()
        context.dataStore.edit {
            it[KEY_BROWSE_SORT_FIELD] = field
            it[KEY_BROWSE_SORT_ASC] = isAsc
        }
    }

    // Page Size Preference
    val pageSizeFlow: Flow<Int> = context.dataStore.data.map { it[KEY_PAGE_SIZE] ?: 40 }
    fun getInitialPageSize(): Int = fastSp.getInt("cached_page_size", 40)

    suspend fun setPageSize(size: Int) {
        fastSp.edit().putInt("cached_page_size", size).apply()
        context.dataStore.edit { it[KEY_PAGE_SIZE] = size }
    }

    // Privacy Mode (Anti-Malu) Flows & Setters
    val privacyEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PRIVACY_ENABLED] ?: false }.distinctUntilChanged()
    val privacyBlurNotifFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PRIVACY_BLUR_NOTIF] ?: true }.distinctUntilChanged()
    val privacyBlurInAppFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PRIVACY_BLUR_INAPP] ?: false }.distinctUntilChanged()
    val privacyMaskTitleFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PRIVACY_MASK_TITLE] ?: true }.distinctUntilChanged()
    val privacyCustomMaskFlow: Flow<String> = context.dataStore.data.map { it[KEY_PRIVACY_CUSTOM_MASK] ?: "Audio Relaksasi" }.distinctUntilChanged()

    fun getInitialPrivacyEnabled(): Boolean = fastSp.getBoolean("cached_privacy_enabled", false)
    fun getInitialPrivacyMaskTitle(): Boolean = fastSp.getBoolean("cached_privacy_mask_title", true)
    fun getInitialPrivacyCustomMask(): String = fastSp.getString("cached_privacy_custom_mask", "Audio Relaksasi") ?: "Audio Relaksasi"
    fun getInitialPrivacyBlurNotif(): Boolean = fastSp.getBoolean("cached_privacy_blur_notif", true)
    fun getInitialPrivacyBlurInApp(): Boolean = fastSp.getBoolean("cached_privacy_blur_inapp", false)

    suspend fun setPrivacyEnabled(enabled: Boolean) {
        fastSp.edit().putBoolean("cached_privacy_enabled", enabled).apply()
        context.dataStore.edit { it[KEY_PRIVACY_ENABLED] = enabled }
    }

    suspend fun setPrivacyBlurNotif(blur: Boolean) {
        fastSp.edit().putBoolean("cached_privacy_blur_notif", blur).apply()
        context.dataStore.edit { it[KEY_PRIVACY_BLUR_NOTIF] = blur }
    }

    suspend fun setPrivacyBlurInApp(blur: Boolean) {
        fastSp.edit().putBoolean("cached_privacy_blur_inapp", blur).apply()
        context.dataStore.edit { it[KEY_PRIVACY_BLUR_INAPP] = blur }
    }

    suspend fun setPrivacyMaskTitle(mask: Boolean) {
        fastSp.edit().putBoolean("cached_privacy_mask_title", mask).apply()
        context.dataStore.edit { it[KEY_PRIVACY_MASK_TITLE] = mask }
    }

    suspend fun setPrivacyCustomMask(text: String) {
        fastSp.edit().putString("cached_privacy_custom_mask", text).apply()
        context.dataStore.edit { it[KEY_PRIVACY_CUSTOM_MASK] = text }
    }

    // Audio Gain Slider (-12dB to +12dB)
    val audioGainDbFlow: Flow<Float> = context.dataStore.data.map { it[KEY_AUDIO_GAIN_DB] ?: 0.0f }
    fun getInitialAudioGainDb(): Float = fastSp.getFloat("cached_audio_gain_db", 0.0f)

    suspend fun setAudioGainDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(-12.0f, 12.0f)
        fastSp.edit().putFloat("cached_audio_gain_db", clamped).apply()
        context.dataStore.edit { it[KEY_AUDIO_GAIN_DB] = clamped }
    }

    // Audio Format Priority
    val audioFormatPriorityFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_AUDIO_FORMAT_PRIORITY] ?: "mp3,flac,wav,opus,m4a,aac"
    }

    fun getInitialAudioFormatPriority(): List<String> {
        val s = fastSp.getString("cached_audio_format_priority", "mp3,flac,wav,opus,m4a,aac") ?: "mp3,flac,wav,opus,m4a,aac"
        return s.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }

    suspend fun setAudioFormatPriority(priority: String) {
        fastSp.edit().putString("cached_audio_format_priority", priority).apply()
        context.dataStore.edit { it[KEY_AUDIO_FORMAT_PRIORITY] = priority }
    }

    // Audio Preload
    val audioPreloadSecondsFlow: Flow<Int> = context.dataStore.data.map { it[KEY_AUDIO_PRELOAD_SECONDS] ?: 15 }

    suspend fun setAudioPreloadSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_AUDIO_PRELOAD_SECONDS] = seconds }
    }

    // Blocked Items (Blacklist)
    val blockedTagsFlow: Flow<Set<String>> = context.dataStore.data.map { it[KEY_BLOCKED_TAGS] ?: emptySet() }
    val blockedCvsFlow: Flow<Set<String>> = context.dataStore.data.map { it[KEY_BLOCKED_CVS] ?: emptySet() }
    val blockedCirclesFlow: Flow<Set<String>> = context.dataStore.data.map { it[KEY_BLOCKED_CIRCLES] ?: emptySet() }

    suspend fun addBlockedTag(tag: String) {
        if (tag.isBlank()) return
        context.dataStore.edit { prefs ->
            val set = prefs[KEY_BLOCKED_TAGS]?.toMutableSet() ?: mutableSetOf()
            set.add(tag.trim().lowercase())
            prefs[KEY_BLOCKED_TAGS] = set
        }
    }

    suspend fun removeBlockedTag(tag: String) {
        context.dataStore.edit { prefs ->
            val set = prefs[KEY_BLOCKED_TAGS]?.toMutableSet() ?: mutableSetOf()
            set.remove(tag.trim().lowercase())
            prefs[KEY_BLOCKED_TAGS] = set
        }
    }

    suspend fun addBlockedCv(cv: String) {
        if (cv.isBlank()) return
        context.dataStore.edit { prefs ->
            val set = prefs[KEY_BLOCKED_CVS]?.toMutableSet() ?: mutableSetOf()
            set.add(cv.trim())
            prefs[KEY_BLOCKED_CVS] = set
        }
    }

    suspend fun removeBlockedCv(cv: String) {
        context.dataStore.edit { prefs ->
            val set = prefs[KEY_BLOCKED_CVS]?.toMutableSet() ?: mutableSetOf()
            set.remove(cv.trim())
            prefs[KEY_BLOCKED_CVS] = set
        }
    }

    suspend fun addBlockedCircle(circle: String) {
        if (circle.isBlank()) return
        context.dataStore.edit { prefs ->
            val set = prefs[KEY_BLOCKED_CIRCLES]?.toMutableSet() ?: mutableSetOf()
            set.add(circle.trim())
            prefs[KEY_BLOCKED_CIRCLES] = set
        }
    }

    suspend fun removeBlockedCircle(circle: String) {
        context.dataStore.edit { prefs ->
            val set = prefs[KEY_BLOCKED_CIRCLES]?.toMutableSet() ?: mutableSetOf()
            set.remove(circle.trim())
            prefs[KEY_BLOCKED_CIRCLES] = set
        }
    }

    // UI Settings: Work Card Display Preferences
    val cardShowRatingFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CARD_SHOW_RATING] ?: false }
    val cardShowPriceFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CARD_SHOW_PRICE] ?: false }
    val cardShowDurationFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CARD_SHOW_DURATION] ?: true }
    val cardShowSalesFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CARD_SHOW_SALES] ?: false }
    val cardShowReleaseDateFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CARD_SHOW_RELEASE_DATE] ?: true }
    val cardShowCircleFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CARD_SHOW_CIRCLE] ?: true }
    val cardShowSubtitleTagFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CARD_SHOW_SUBTITLE_TAG] ?: true }
    val cardShowAgeRatingFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CARD_SHOW_AGE_RATING] ?: true }

    suspend fun setCardShowRating(show: Boolean) = context.dataStore.edit { it[KEY_CARD_SHOW_RATING] = show }
    suspend fun setCardShowPrice(show: Boolean) = context.dataStore.edit { it[KEY_CARD_SHOW_PRICE] = show }
    suspend fun setCardShowDuration(show: Boolean) = context.dataStore.edit { it[KEY_CARD_SHOW_DURATION] = show }
    suspend fun setCardShowSales(show: Boolean) = context.dataStore.edit { it[KEY_CARD_SHOW_SALES] = show }
    suspend fun setCardShowReleaseDate(show: Boolean) = context.dataStore.edit { it[KEY_CARD_SHOW_RELEASE_DATE] = show }
    suspend fun setCardShowCircle(show: Boolean) = context.dataStore.edit { it[KEY_CARD_SHOW_CIRCLE] = show }
    suspend fun setCardShowSubtitleTag(show: Boolean) = context.dataStore.edit { it[KEY_CARD_SHOW_SUBTITLE_TAG] = show }
    suspend fun setCardShowAgeRating(show: Boolean) = context.dataStore.edit { it[KEY_CARD_SHOW_AGE_RATING] = show }

    // UI Settings: Work Detail Display Preferences
    val detailShowRatingFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_RATING] ?: false }
    val detailShowPriceFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_PRICE] ?: false }
    val detailShowDurationFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_DURATION] ?: true }
    val detailShowSalesFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_SALES] ?: false }
    val detailShowExternalLinksFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_EXTERNAL_LINKS] ?: true }
    val detailShowReleaseDateFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_RELEASE_DATE] ?: true }
    val detailShowTranslateButtonFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_TRANSLATE_BUTTON] ?: true }
    val detailShowSubtitleTagFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_SUBTITLE_TAG] ?: true }
    val detailShowAgeRatingFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_AGE_RATING] ?: true }
    val detailShowRecommendationsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETAIL_SHOW_RECOMMENDATIONS] ?: true }

    suspend fun setDetailShowRating(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_RATING] = show }
    suspend fun setDetailShowPrice(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_PRICE] = show }
    suspend fun setDetailShowDuration(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_DURATION] = show }
    suspend fun setDetailShowSales(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_SALES] = show }
    suspend fun setDetailShowExternalLinks(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_EXTERNAL_LINKS] = show }
    suspend fun setDetailShowReleaseDate(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_RELEASE_DATE] = show }
    suspend fun setDetailShowTranslateButton(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_TRANSLATE_BUTTON] = show }
    suspend fun setDetailShowSubtitleTag(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_SUBTITLE_TAG] = show }
    suspend fun setDetailShowAgeRating(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_AGE_RATING] = show }
    suspend fun setDetailShowRecommendations(show: Boolean) = context.dataStore.edit { it[KEY_DETAIL_SHOW_RECOMMENDATIONS] = show }

    // UI Settings: Player Buttons Order
    val playerButtonOrderFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_PLAYER_BUTTON_ORDER]
        if (raw.isNullOrBlank() || raw.contains("seek_backward") || !raw.contains("chapters")) {
            DEFAULT_PLAYER_BUTTON_ORDER
        } else {
            raw
        }
    }

    suspend fun setPlayerButtonOrder(order: String) = context.dataStore.edit { it[KEY_PLAYER_BUTTON_ORDER] = order }

    // Translation Provider & Lyrics Persistence
    val translationProviderFlow: Flow<String> = context.dataStore.data.map { it[KEY_TRANSLATION_PROVIDER] ?: "google" }
    suspend fun setTranslationProvider(provider: String) {
        try {
            fastSp.edit().putString("cached_trans_provider", provider).apply()
        } catch (_: Exception) {}
        context.dataStore.edit { it[KEY_TRANSLATION_PROVIDER] = provider }
    }

    val autoSaveTranslatedLyricsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SAVE_TRANSLATED_LYRICS] ?: true }
    suspend fun setAutoSaveTranslatedLyrics(autoSave: Boolean) = context.dataStore.edit { it[KEY_AUTO_SAVE_TRANSLATED_LYRICS] = autoSave }

    // LLM Settings
    val llmApiUrlFlow: Flow<String> = context.dataStore.data.map { it[KEY_LLM_API_URL] ?: "" }
    val llmApiKeyFlow: Flow<String> = SecurePreferences.llmApiKeyFlow(context).onStart {
        migrateLegacyLlmKeyIfNeeded()
    }
    val llmModelFlow: Flow<String> = context.dataStore.data.map { it[KEY_LLM_MODEL] ?: "claude-haiku-5-5" }
    val llmPromptFlow: Flow<String> = context.dataStore.data.map {
        val p = it[KEY_LLM_PROMPT]
        if (p.isNullOrBlank()) DEFAULT_LLM_PROMPT else p
    }
    val llmConcurrencyFlow: Flow<Int> = context.dataStore.data.map { (it[KEY_LLM_CONCURRENCY] ?: 2).coerceIn(1, 5) }

    private suspend fun migrateLegacyLlmKeyIfNeeded() {
        try {
            val prefs = context.dataStore.data.first()
            val legacyDataStoreKey = prefs[KEY_LLM_API_KEY]
            if (!legacyDataStoreKey.isNullOrBlank() &&
                SecurePreferences.migratePlaintextKeyIfNeeded(context, legacyDataStoreKey)
            ) {
                context.dataStore.edit { it.remove(KEY_LLM_API_KEY) }
            }
            val legacyFastKey = fastSp.getString("cached_llm_key", null)
            if (!legacyFastKey.isNullOrBlank() &&
                SecurePreferences.migratePlaintextKeyIfNeeded(context, legacyFastKey)
            ) {
                fastSp.edit().remove("cached_llm_key").apply()
            }
        } catch (_: Exception) {}
    }

    suspend fun setLlmConfig(url: String, key: String, model: String, prompt: String, concurrency: Int = 2) {
        SecurePreferences.setLlmApiKey(context, key)
        try {
            fastSp.edit()
                .putString("cached_llm_url", url)
                .remove("cached_llm_key")
                .putString("cached_llm_model", model)
                .putString("cached_llm_prompt", prompt)
                .putInt("cached_llm_concurrency", concurrency.coerceIn(1, 5))
                .apply()
        } catch (_: Exception) {}
        context.dataStore.edit {
            it[KEY_LLM_API_URL] = url
            it.remove(KEY_LLM_API_KEY)
            it[KEY_LLM_MODEL] = model
            it[KEY_LLM_PROMPT] = prompt
            it[KEY_LLM_CONCURRENCY] = concurrency.coerceIn(1, 5)
        }
    }

    suspend fun setLlmConcurrency(concurrency: Int) {
        try {
            fastSp.edit().putInt("cached_llm_concurrency", concurrency.coerceIn(1, 5)).apply()
        } catch (_: Exception) {}
        context.dataStore.edit {
            it[KEY_LLM_CONCURRENCY] = concurrency.coerceIn(1, 5)
        }
    }

    // Floating Lyrics Settings
    val floatingLyricsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_FLOATING_LYRICS_ENABLED] ?: false }
    val floatingLyricsFontSizeFlow: Flow<Float> = context.dataStore.data.map { it[KEY_FLOATING_LYRICS_FONT_SIZE] ?: 14f }
    val floatingLyricsOpacityFlow: Flow<Float> = context.dataStore.data.map { it[KEY_FLOATING_LYRICS_OPACITY] ?: 0.90f }
    val floatingLyricsCornerRadiusFlow: Flow<Float> = context.dataStore.data.map { it[KEY_FLOATING_LYRICS_CORNER_RADIUS] ?: 16f }
    val floatingLyricsShowTranslationFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_FLOATING_LYRICS_SHOW_TRANSLATION] ?: true }
    val floatingLyricsDisplayModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_FLOATING_LYRICS_DISPLAY_MODE]
            ?: if (prefs[KEY_FLOATING_LYRICS_SHOW_TRANSLATION] == false) "ORIGINAL_ONLY" else "BOTH"
    }.distinctUntilChanged()
    val floatingLyricsLockedFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_FLOATING_LYRICS_LOCKED] ?: false }

    suspend fun setFloatingLyricsEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_FLOATING_LYRICS_ENABLED] = enabled }
    suspend fun setFloatingLyricsFontSize(size: Float) = context.dataStore.edit { it[KEY_FLOATING_LYRICS_FONT_SIZE] = size.coerceIn(10f, 24f) }
    suspend fun setFloatingLyricsOpacity(opacity: Float) = context.dataStore.edit { it[KEY_FLOATING_LYRICS_OPACITY] = opacity.coerceIn(0.0f, 1.0f) }
    suspend fun setFloatingLyricsCornerRadius(radius: Float) = context.dataStore.edit { it[KEY_FLOATING_LYRICS_CORNER_RADIUS] = radius.coerceIn(0f, 24f) }
    suspend fun setFloatingLyricsShowTranslation(show: Boolean) = context.dataStore.edit {
        it[KEY_FLOATING_LYRICS_SHOW_TRANSLATION] = show
        it[KEY_FLOATING_LYRICS_DISPLAY_MODE] = if (show) "BOTH" else "ORIGINAL_ONLY"
    }
    suspend fun setFloatingLyricsDisplayMode(mode: String) {
        val normalized = when (mode) {
            "TRANSLATION_ONLY", "ORIGINAL_ONLY", "BOTH" -> mode
            else -> "BOTH"
        }
        context.dataStore.edit {
            it[KEY_FLOATING_LYRICS_DISPLAY_MODE] = normalized
            it[KEY_FLOATING_LYRICS_SHOW_TRANSLATION] = (normalized != "ORIGINAL_ONLY")
        }
    }
    suspend fun setFloatingLyricsLocked(locked: Boolean) = context.dataStore.edit { it[KEY_FLOATING_LYRICS_LOCKED] = locked }

    suspend fun setThemeMode(mode: ThemeMode) {
        fastSp.edit().putString("cached_theme_mode", mode.name).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        fastSp.edit().putBoolean("cached_dynamic_color", enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setColorPalette(palette: ColorPalette) {
        fastSp.edit()
            .putString("cached_color_palette", palette.name)
            .putBoolean("cached_dynamic_color", false)
            .apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_COLOR_PALETTE] = palette.name
            preferences[KEY_DYNAMIC_COLOR] = false
        }
    }

    suspend fun setDownloadDir(path: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DOWNLOAD_DIR] = path
        }
    }

    suspend fun addImportedDir(dirUriOrPath: String) {
        if (dirUriOrPath.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_IMPORTED_DIRS]?.toMutableSet() ?: mutableSetOf()
            current.add(dirUriOrPath.trim())
            preferences[KEY_IMPORTED_DIRS] = current
        }
    }

    suspend fun removeImportedDir(dirUriOrPath: String) {
        if (dirUriOrPath.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_IMPORTED_DIRS]?.toMutableSet() ?: mutableSetOf()
            current.remove(dirUriOrPath.trim())
            preferences[KEY_IMPORTED_DIRS] = current
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

    suspend fun setCreateCueSheet(create: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CREATE_CUE_SHEET] = create
        }
    }

    suspend fun setMergeDownloadedAudio(merge: Boolean) {
        fastSp.edit().putBoolean("cached_merge_downloaded_audio", merge).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_MERGE_DOWNLOADED_AUDIO] = merge
        }
    }

    suspend fun setTranslationLanguage(lang: String) {
        fastSp.edit().putString("cached_trans_language", lang).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_TRANSLATION_LANGUAGE] = lang
        }
    }

    suspend fun setTranslationActive(active: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_TRANSLATION_ACTIVE] = active
        }
    }

    val translationEngineVersionFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_TRANSLATION_ENGINE_VERSION] ?: 0
    }

    suspend fun setTranslationEngineVersion(version: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TRANSLATION_ENGINE_VERSION] = version
        }
    }

    val autoTranslateLyricsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_TRANSLATE_LYRICS] ?: true
    }.distinctUntilChanged()

    val autoTranslateDetailFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_TRANSLATE_DETAIL] ?: false
    }.distinctUntilChanged()

    val autoTranslateListsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_TRANSLATE_LISTS] ?: false
    }.distinctUntilChanged()

    val autoTranslatePlayerFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_TRANSLATE_PLAYER] ?: false
    }.distinctUntilChanged()

    fun getInitialAutoTranslatePlayer(): Boolean = fastSp.getBoolean("cached_auto_translate_player", false)

    suspend fun setAutoTranslatePlayer(enabled: Boolean) {
        fastSp.edit().putBoolean("cached_auto_translate_player", enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_TRANSLATE_PLAYER] = enabled
        }
    }

    fun getInitialAutoTranslateLyrics(): Boolean = fastSp.getBoolean("cached_auto_translate_lyrics", true)
    fun getInitialAutoTranslateDetail(): Boolean = fastSp.getBoolean("cached_auto_translate_detail", false)
    fun getInitialAutoTranslateLists(): Boolean = fastSp.getBoolean("cached_auto_translate_lists", false)

    suspend fun setAutoTranslateLyrics(enabled: Boolean) {
        fastSp.edit().putBoolean("cached_auto_translate_lyrics", enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_TRANSLATE_LYRICS] = enabled
        }
    }

    suspend fun setAutoTranslateDetail(enabled: Boolean) {
        fastSp.edit().putBoolean("cached_auto_translate_detail", enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_TRANSLATE_DETAIL] = enabled
        }
    }

    suspend fun setAutoTranslateLists(enabled: Boolean) {
        fastSp.edit().putBoolean("cached_auto_translate_lists", enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_TRANSLATE_LISTS] = enabled
        }
    }

    suspend fun setExclusiveUsbDac(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EXCLUSIVE_USB_DAC] = enabled
        }
    }

    suspend fun setExclusiveUsbVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        setFastCachedUsbVolume(context, clamped)
        context.dataStore.edit { preferences ->
            preferences[KEY_EXCLUSIVE_USB_VOLUME] = clamped
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
                .remove("cached_show_remaining")
                .apply()
        }
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_RESUME] = enabled
            if (!enabled) {
                preferences.remove(KEY_LAST_PLAYED_RJID)
                preferences.remove(KEY_LAST_POSITION_MS)
                preferences.remove(KEY_SHOW_REMAINING_TIME)
            }
        }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_SPEED] = speed
        }
    }

    suspend fun setShowRemainingTime(enabled: Boolean) {
        val autoResume = fastSp.getBoolean("setting_auto_resume", true)
        if (autoResume) {
            fastSp.edit().putBoolean("cached_show_remaining", enabled).apply()
            context.dataStore.edit { preferences ->
                preferences[KEY_SHOW_REMAINING_TIME] = enabled
            }
        }
    }

    suspend fun savePlaybackState(rjid: String?, positionMs: Long, repeatMode: Int, shuffleMode: Boolean, showRemainingTime: Boolean = false) {
        val autoResume = fastSp.getBoolean("setting_auto_resume", true)
        if (autoResume) {
            context.dataStore.edit { preferences ->
                if (rjid != null) preferences[KEY_LAST_PLAYED_RJID] = rjid
                preferences[KEY_LAST_POSITION_MS] = positionMs
                preferences[KEY_REPEAT_MODE] = repeatMode
                preferences[KEY_SHUFFLE_MODE] = shuffleMode
                preferences[KEY_SHOW_REMAINING_TIME] = showRemainingTime
            }
        }
    }
}
