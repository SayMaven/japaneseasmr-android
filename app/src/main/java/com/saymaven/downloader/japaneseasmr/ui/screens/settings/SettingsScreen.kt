package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.BuildConfig
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import com.saymaven.downloader.japaneseasmr.data.model.UpdateInfo
import com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.service.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

enum class SettingsSubScreen {
    MAIN,
    LANGUAGE_AND_TRANSLATION,
    APPEARANCE_THEME,
    DOWNLOAD_STORAGE,
    AUDIO_FORMAT,
    FLOATING_LYRICS,
    PRIVACY_MODE,
    BLOCKED_ITEMS,
    LLM_SETTINGS,
    UI_SETTINGS,
    PLAYER_BUTTONS,
    WORK_CARD,
    WORK_DETAIL,
    CHANGELOG,
    USER_GUIDE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    var subScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }

    when (subScreen) {
        SettingsSubScreen.LANGUAGE_AND_TRANSLATION -> {
            LanguageAndTranslationSettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.APPEARANCE_THEME -> {
            AppearanceThemeSettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.DOWNLOAD_STORAGE -> {
            DownloadStorageSettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.CHANGELOG -> {
            ChangelogSettingsScreen(
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.USER_GUIDE -> {
            UserGuideSettingsScreen(
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.UI_SETTINGS -> {
            UiSettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.MAIN },
                onNavigateToPlayerButtons = { subScreen = SettingsSubScreen.PLAYER_BUTTONS },
                onNavigateToWorkCard = { subScreen = SettingsSubScreen.WORK_CARD },
                onNavigateToWorkDetail = { subScreen = SettingsSubScreen.WORK_DETAIL }
            )
            return
        }
        SettingsSubScreen.PLAYER_BUTTONS -> {
            PlayerButtonsSettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.UI_SETTINGS }
            )
            return
        }
        SettingsSubScreen.WORK_CARD -> {
            WorkCardDisplaySettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.UI_SETTINGS }
            )
            return
        }
        SettingsSubScreen.WORK_DETAIL -> {
            WorkDetailDisplaySettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.UI_SETTINGS }
            )
            return
        }
        SettingsSubScreen.FLOATING_LYRICS -> {
            FloatingLyricsSettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.PRIVACY_MODE -> {
            PrivacyModeSettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.BLOCKED_ITEMS -> {
            BlockedItemsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.AUDIO_FORMAT -> {
            AudioFormatSettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.LLM_SETTINGS -> {
            LLMSettingsScreen(
                viewModel = viewModel,
                onBack = { subScreen = SettingsSubScreen.MAIN }
            )
            return
        }
        SettingsSubScreen.MAIN -> { /* proceed with normal main settings list */ }
    }

    val updateUiState by viewModel.updateUiState.collectAsState()
    val targetDebugChannel by viewModel.targetDebugChannel.collectAsState()

    val importedDirs by viewModel.importedDirs.collectAsState()
    val importPreview by viewModel.importPreview.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    var folderToRemove by remember { mutableStateOf<String?>(null) }
    var showManageStorageDialog by remember { mutableStateOf(false) }
    var pendingFolderPickerAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun checkStoragePermissionAndLaunch(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            pendingFolderPickerAction = action
            showManageStorageDialog = true
        } else {
            action()
        }
    }

    val importFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
            }
            viewModel.prepareImport(uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_header_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // ================= 0. UMUM & BAHASA =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_general), icon = Icons.Default.Language)

        Card(
            onClick = { subScreen = SettingsSubScreen.LANGUAGE_AND_TRANSLATION },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_lang_and_trans_title), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_lang_and_trans_desc), style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        Card(
            onClick = { subScreen = SettingsSubScreen.BLOCKED_ITEMS },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_blocked_items), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_blocked_items_desc), style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        Card(
            onClick = { subScreen = SettingsSubScreen.LLM_SETTINGS },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_llm_config), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_llm_config_desc), style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // ================= 1. PENGATURAN PEMUTAR AUDIO =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_player), icon = Icons.AutoMirrored.Filled.VolumeUp)

        Card(
            onClick = { subScreen = SettingsSubScreen.AUDIO_FORMAT },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_audio_format_and_playback), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_audio_format_and_playback_desc), style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        Card(
            onClick = { subScreen = SettingsSubScreen.FLOATING_LYRICS },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_floating_lyrics), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_floating_lyrics_desc), style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        Card(
            onClick = { subScreen = SettingsSubScreen.PRIVACY_MODE },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_cat_privacy), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_privacy_enable_desc), style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // ================= 2. PENGATURAN UNDUHAN =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_download), icon = Icons.Default.Download)

        Card(
            onClick = { subScreen = SettingsSubScreen.DOWNLOAD_STORAGE },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_download_storage_title), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_download_storage_desc), style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // ================= 2.1 FOLDER AUDIO & IMPOR =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_import), icon = Icons.Default.FolderSpecial)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_import_folders_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_import_folders_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        FilledTonalButton(onClick = { checkStoragePermissionAndLaunch { importFolderLauncher.launch(null) } }) {
                            Text(stringResource(R.string.settings_import_add_btn))
                        }
                    }
                )

                if (importedDirs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_import_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    importedDirs.forEach { dirStr ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        val physical = AudioStorageHelper.resolvePhysicalPathFromUri(context, dirStr) ?: dirStr
                        val displayPath = AudioStorageHelper.formatPathForDisplay(physical)
                        val isSdCard = physical.contains("SD Card", ignoreCase = true) || (physical.startsWith("/storage/") && !physical.contains("emulated"))

                        ListItem(
                            headlineContent = {
                                Text(
                                    displayPath,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                if (isSdCard) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = "SD CARD",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                Icon(
                                    if (isSdCard) Icons.Default.SdStorage else Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { folderToRemove = dirStr }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // ================= 3. TAMPILAN & ANTARMUKA =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_appearance), icon = Icons.Default.Palette)

        Card(
            onClick = { subScreen = SettingsSubScreen.APPEARANCE_THEME },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_appearance_theme_title), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_appearance_theme_desc), style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        Card(
            onClick = { subScreen = SettingsSubScreen.UI_SETTINGS },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_cat_ui), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_ui_desc), style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // ================= 4. TENTANG & INFORMASI =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_about), icon = Icons.Default.Info)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dev_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_dev_name), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SayMaven"))
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_arch_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_arch_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_check_update_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        if (updateUiState is UpdateUiState.Checking) {
                            Text(stringResource(R.string.settings_checking_updates), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                stringResource(R.string.settings_check_update_desc, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        if (updateUiState is UpdateUiState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            FilledTonalButton(
                                onClick = { viewModel.checkForUpdate() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.action_refresh))
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        if (updateUiState !is UpdateUiState.Checking) {
                            viewModel.checkForUpdate()
                        }
                    }
                )

                if (BuildConfig.DEBUG) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_update_dev_channel_toggle), fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                if (targetDebugChannel) stringResource(R.string.settings_update_channel_debug)
                                else stringResource(R.string.settings_update_channel_release),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                        trailingContent = {
                            Switch(
                                checked = targetDebugChannel,
                                onCheckedChange = { viewModel.setTargetDebugChannel(it) }
                            )
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_changelog_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_changelog_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable { subScreen = SettingsSubScreen.CHANGELOG }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_user_guide_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_user_guide_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable { subScreen = SettingsSubScreen.USER_GUIDE }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_report_bug_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("GitHub Issues", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SayMaven/japaneseasmr-android/issues"))
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_github_repo_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("SayMaven/japaneseasmr-android", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SayMaven/japaneseasmr-android"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }

    // Confirmation & Action Dialogs
    when (val state = updateUiState) {
        is UpdateUiState.NoUpdateAvailable -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateDialog() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_check_update_title))
                    }
                },
                text = {
                    Text(stringResource(R.string.settings_no_update_toast, state.currentVersion))
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
            )
        }
        is UpdateUiState.UpdateAvailable -> {
            val info = state.info
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateDialog() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_update_available_title))
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.settings_update_version_badge, info.versionName),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Surface(
                                color = if (info.isDebugAsset) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    if (info.isDebugAsset) stringResource(R.string.settings_update_channel_debug) else stringResource(R.string.settings_update_channel_release),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (info.isDebugAsset) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        if (BuildConfig.DEBUG && info.isDebugAsset) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    stringResource(R.string.settings_update_debug_tip),
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (info.fileSize > 0) {
                            val mb = String.format(java.util.Locale.US, "%.1f MB", info.fileSize / (1024.0 * 1024.0))
                            Text(
                                text = "${info.fileName} ($mb)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                stringResource(R.string.settings_changelog_title),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    info.releaseNotes,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState()),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { viewModel.startDownload(info) }) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_update_download_btn))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text(stringResource(R.string.settings_update_later_btn))
                    }
                }
            )
        }
        is UpdateUiState.Downloading -> {
            val downState = state
            AlertDialog(
                onDismissRequest = { /* Do not dismiss while downloading */ },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.settings_update_downloading_title))
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(
                            progress = { downState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val percent = (downState.progress * 100).toInt()
                            Text("$percent%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            if (downState.totalBytes > 0) {
                                val dlMb = String.format(java.util.Locale.US, "%.1f", downState.downloadedBytes / (1024.0 * 1024.0))
                                val totMb = String.format(java.util.Locale.US, "%.1f MB", downState.totalBytes / (1024.0 * 1024.0))
                                Text("$dlMb / $totMb", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
        is UpdateUiState.ReadyToInstall -> {
            val apkFile = state.apkFile
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateDialog() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_update_ready_title))
                    }
                },
                text = {
                    Text(stringResource(R.string.settings_update_ready_desc))
                },
                confirmButton = {
                    Button(onClick = { viewModel.installApk(context, apkFile) }) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_update_install_btn))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        }
        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateDialog() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Error")
                    }
                },
                text = {
                    Text(state.message)
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        }
        else -> Unit
    }

    if (showManageStorageDialog) {
        AlertDialog(
            onDismissRequest = { showManageStorageDialog = false },
            icon = { Icon(Icons.Default.SdCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.settings_manage_storage_dialog_title)) },
            text = { Text(stringResource(R.string.settings_manage_storage_dialog_desc)) },
            confirmButton = {
                Button(onClick = {
                    showManageStorageDialog = false
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                }) {
                    Text(stringResource(R.string.settings_manage_storage_open_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showManageStorageDialog = false
                    pendingFolderPickerAction?.invoke()
                    pendingFolderPickerAction = null
                }) {
                    Text(stringResource(R.string.settings_manage_storage_cancel_btn))
                }
            }
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportPreview() },
            icon = { Icon(Icons.Default.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.settings_import_preview_title)) },
            text = {
                Column {
                    Text(preview.folderName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    val resolved = AudioStorageHelper.resolvePhysicalPathFromUri(context, preview.folderPathOrUri) ?: preview.folderPathOrUri
                    val disp = AudioStorageHelper.formatPathForDisplay(resolved)
                    Text(disp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.settings_import_preview_total, preview.totalAudioTracks), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.settings_import_preview_multi, preview.multiAlbumCount), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.settings_import_preview_singles, preview.singleCount), style = MaterialTheme.typography.bodyMedium)

                            if (preview.unreadableCount > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.settings_import_preview_unreadable, preview.unreadableCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.settings_import_preview_zerocopy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.confirmImport(preview.folderPathOrUri)
                    Toast.makeText(context, context.getString(R.string.settings_import_toast_success), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.settings_import_preview_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissImportPreview() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    folderToRemove?.let { dir ->
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text(stringResource(R.string.settings_import_delete_confirm)) },
            text = {
                val resolved = AudioStorageHelper.resolvePhysicalPathFromUri(context, dir) ?: dir
                val disp = AudioStorageHelper.formatPathForDisplay(resolved)
                Text(disp, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeImportedDir(dir)
                    folderToRemove = null
                    Toast.makeText(context, context.getString(R.string.settings_import_toast_removed), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (isScanning) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.settings_import_preview_scanning))
                }
            }
        )
    }
}

@Composable
fun ColorPaletteCarousel(
    selectedPalette: ColorPalette,
    onSelectPalette: (ColorPalette) -> Unit
) {
    val palettes = remember { ColorPalette.values().toList() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val totalPages = remember(palettes.size) { (palettes.size + 3) / 4 }

    val activePageIndex by remember {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            (firstIdx / 4).coerceIn(0, totalPages - 1)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(palettes, key = { _, it -> it.name }) { idx, palette ->
                val isSelected = (palette == selectedPalette)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = if (isSelected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Transparent),
                    modifier = Modifier
                        .size(width = 82.dp, height = 82.dp)
                        .clickable {
                            onSelectPalette(palette)
                            scope.launch {
                                listState.animateScrollToItem((idx - 1).coerceAtLeast(0))
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(50.dp)) {
                            drawArc(
                                color = palette.primary,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = true
                            )
                            drawArc(
                                color = palette.secondary,
                                startAngle = 90f,
                                sweepAngle = 90f,
                                useCenter = true
                            )
                            drawArc(
                                color = palette.tertiary,
                                startAngle = 0f,
                                sweepAngle = 90f,
                                useCenter = true
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { pageIdx ->
                val isCurrent = (pageIdx == activePageIndex)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isCurrent) 8.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        )
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
