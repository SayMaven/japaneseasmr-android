package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.BuildConfig
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import com.saymaven.downloader.japaneseasmr.data.model.UpdateInfo
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.service.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current

    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val colorPalette by viewModel.colorPalette.collectAsState()
    val downloadDir by viewModel.downloadDir.collectAsState()
    val parallelConn by viewModel.parallelConnections.collectAsState()
    val autoClipboard by viewModel.autoClipboard.collectAsState()
    val useDetailedFilename by viewModel.useDetailedFilename.collectAsState()
    val createCueSheet by viewModel.createCueSheet.collectAsState()

    // Audio Player States
    val exclusiveUsbDac by viewModel.exclusiveUsbDac.collectAsState()
    val audioFocus by viewModel.audioFocus.collectAsState()
    val pauseOnUnplug by viewModel.pauseOnUnplug.collectAsState()
    val skipSilence by viewModel.skipSilence.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val autoResume by viewModel.autoResume.collectAsState()
    val defaultSpeed by viewModel.defaultSpeed.collectAsState()
    val translationLanguage by viewModel.translationLanguage.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val updateUiState by viewModel.updateUiState.collectAsState()
    val targetDebugChannel by viewModel.targetDebugChannel.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showThreadDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var draftSpeed by remember(defaultSpeed) { mutableFloatStateOf(defaultSpeed) }
    var showTranslationLangDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
            }
            val resolved = com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper.resolvePhysicalPathFromUri(context, uri.toString()) ?: uri.toString()
            viewModel.setDownloadDir(resolved)
            Toast.makeText(context, context.getString(R.string.settings_folder_toast_changed), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_header_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // ================= 0. UMUM & BAHASA =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_general), icon = Icons.Default.Language)

        Card(
            onClick = { showLanguageDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_lang_title), fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    val langLabel = when (appLanguage) {
                        "en" -> "English"
                        "in" -> "Bahasa Indonesia"
                        "ja" -> "日本語"
                        "zh" -> "简体中文"
                        else -> stringResource(R.string.settings_lang_system)
                    }
                    Text(langLabel, style = MaterialTheme.typography.bodySmall)
                },
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

        // ================= 1. PENGATURAN PEMUTAR AUDIO =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_player), icon = Icons.AutoMirrored.Filled.VolumeUp)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dac_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_dac_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = exclusiveUsbDac,
                            onCheckedChange = { viewModel.setExclusiveUsbDac(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_audio_focus_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_audio_focus_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Hearing, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = audioFocus,
                            onCheckedChange = { viewModel.setAudioFocus(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_pause_unplug_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_pause_unplug_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = pauseOnUnplug,
                            onCheckedChange = { viewModel.setPauseOnUnplug(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_skip_silence_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_skip_silence_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = skipSilence,
                            onCheckedChange = { viewModel.setSkipSilence(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_keep_screen_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_keep_screen_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.StayCurrentPortrait, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { viewModel.setKeepScreenOn(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_auto_resume_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_auto_resume_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = autoResume,
                            onCheckedChange = { viewModel.setAutoResume(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_default_speed_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        val label = when (defaultSpeed) {
                            1.0f -> stringResource(R.string.settings_speed_normal)
                            0.25f -> stringResource(R.string.settings_speed_min)
                            2.0f -> stringResource(R.string.settings_speed_max)
                            else -> String.format(java.util.Locale.US, "%.2fx", defaultSpeed)
                        }
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = { Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        draftSpeed = defaultSpeed
                        showSpeedDialog = true
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_target_lang_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        val langLabel = when (translationLanguage) {
                            "en" -> stringResource(R.string.settings_trans_lang_en)
                            else -> stringResource(R.string.settings_trans_lang_in)
                        }
                        Text(langLabel, style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = { Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showTranslationLangDialog = true }
                )
            }
        }

        // ================= 2. PENGATURAN UNDUHAN =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_download), icon = Icons.Default.Download)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_download_dir_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        val physicalPath = com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper.resolvePhysicalPathFromUri(context, downloadDir)
                            ?: DownloadService.getDefaultDownloadDirectory().absolutePath
                        val cleanDisplayPath = com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper.formatPathForDisplay(physicalPath)
                        Text(
                            cleanDisplayPath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        FilledTonalButton(onClick = { dirPickerLauncher.launch(null) }) {
                            Text(stringResource(R.string.action_change))
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_threads_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_threads_desc, parallelConn), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showThreadDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_clipboard_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_clipboard_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = autoClipboard,
                            onCheckedChange = { viewModel.setAutoClipboard(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_detailed_filename_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_detailed_filename_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = useDetailedFilename,
                            onCheckedChange = { viewModel.setUseDetailedFilename(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_cue_sheet_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_cue_sheet_detailed_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = createCueSheet,
                            onCheckedChange = { viewModel.setCreateCueSheet(it) }
                        )
                    }
                )
            }
        }

        // ================= 3. TAMPILAN & TEMA (DENGAN 36 COLOR PALETTE PREVIEWS) =================
        SettingsCategoryHeader(title = stringResource(R.string.settings_cat_appearance), icon = Icons.Default.Palette)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_theme_mode_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            when (themeMode) {
                                ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark)
                                ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light)
                                ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_dynamic_color_title), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_dynamic_color_desc), style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { viewModel.setDynamicColor(it) }
                            )
                        }
                    )
                }

                if (!dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_palette_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = colorPalette.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal Palettes Preview Carousel with Exact 4-Palettes = 1 Dot indicator
                        ColorPaletteCarousel(
                            selectedPalette = colorPalette,
                            onSelectPalette = { viewModel.setColorPalette(it) }
                        )
                    }
                }
            }
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
                    modifier = Modifier.clickable { showChangelogDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_user_guide_title), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stringResource(R.string.settings_user_guide_desc), style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showGuideDialog = true }
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

    // Dialogs
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
                onDismissRequest = { /* Jangan tutup sembarangan saat mengunduh */ },
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
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_theme_title)) },
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                when (mode) {
                                    ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark)
                                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light)
                                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.settings_speed_dialog_title))
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Hero Speed Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.2fx", draftSpeed),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val statusText = when {
                                draftSpeed == 1.0f -> stringResource(R.string.settings_speed_normal)
                                draftSpeed < 1.0f -> stringResource(R.string.settings_speed_min)
                                else -> stringResource(R.string.settings_speed_max)
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Smooth Slider murni tanpa titik-titik (steps = 0)
                    Slider(
                        value = draftSpeed,
                        onValueChange = { draftSpeed = (kotlin.math.round(it * 20f) / 20f).coerceIn(0.25f, 2.0f) },
                        valueRange = 0.25f..2.0f,
                        steps = 0,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.settings_speed_min), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.settings_speed_normal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.settings_speed_max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = stringResource(R.string.player_timer_quick_choice),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2 Baris Tombol Preset Proporsional (100% Pas & Terlihat)
                    val presetsRow1 = listOf(0.5f, 0.75f, 1.0f)
                    val presetsRow2 = listOf(1.25f, 1.5f, 2.0f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetsRow1.forEach { preset ->
                            val isSelected = (draftSpeed == preset)
                            Surface(
                                onClick = { draftSpeed = preset },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${preset}x",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetsRow2.forEach { preset ->
                            val isSelected = (draftSpeed == preset)
                            Surface(
                                onClick = { draftSpeed = preset },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${preset}x",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setDefaultSpeed(draftSpeed)
                    showSpeedDialog = false
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showTranslationLangDialog) {
        val langOptions = listOf(
            "id" to stringResource(R.string.settings_trans_lang_in),
            "en" to stringResource(R.string.settings_trans_lang_en)
        )
        AlertDialog(
            onDismissRequest = { showTranslationLangDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.settings_dialog_trans_lang_title))
                }
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_target_lang_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    langOptions.forEach { (code, label) ->
                        val isSelected = (translationLanguage == code)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTranslationLanguage(code)
                                    showTranslationLangDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setTranslationLanguage(code)
                                    showTranslationLangDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTranslationLangDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showThreadDialog) {
        val options = listOf(
            4 to stringResource(R.string.settings_threads_opt_4),
            8 to stringResource(R.string.settings_threads_opt_8),
            16 to stringResource(R.string.settings_threads_opt_16),
            24 to stringResource(R.string.settings_threads_opt_24),
            32 to stringResource(R.string.settings_threads_opt_32)
        )
        AlertDialog(
            onDismissRequest = { showThreadDialog = false },
            title = { Text(stringResource(R.string.settings_threads_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_threads_desc, parallelConn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    options.forEach { (opt, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setParallelConnections(opt)
                                    showThreadDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = parallelConn == opt, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (opt == 16) FontWeight.Bold else FontWeight.Normal,
                                color = if (opt == 16) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThreadDialog = false }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }

    if (showLanguageDialog) {
        val languages = listOf(
            "system" to stringResource(R.string.settings_lang_system),
            "en" to stringResource(R.string.settings_lang_en),
            "in" to stringResource(R.string.settings_lang_in),
            "ja" to stringResource(R.string.settings_lang_ja),
            "zh" to stringResource(R.string.settings_lang_zh)
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_lang_title)) },
            text = {
                Column {
                    languages.forEach { (code, label) ->
                        val isSelected = (appLanguage == code)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLanguage(code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = { Text(stringResource(R.string.settings_changelog_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_changelog_v140_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_changelog_v140_item1), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v140_item2), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v140_item3), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v140_item4), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v140_item5), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v140_item6), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v140_item7), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_changelog_v132_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_changelog_v132_item1), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v132_item2), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v132_item3), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v132_item4), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v132_item5), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_changelog_v131_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_changelog_v131_item1), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v131_item2), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v131_item3), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v131_item4), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_changelog_v130_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_changelog_v130_item1), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v130_item2), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v130_item3), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v130_item4), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v130_item5), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v130_item6), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v130_item7), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_changelog_v121_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_changelog_v121_item1), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v121_item2), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v121_item3), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v121_item4), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_changelog_v120_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_changelog_v120_item1), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v120_item2), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v120_item3), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.settings_changelog_v120_item4), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = { Text(stringResource(R.string.settings_user_guide_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_guide_step1_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_guide_step1_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.settings_guide_step2_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_guide_step2_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.settings_guide_step3_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_guide_step3_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.settings_guide_step4_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_guide_step4_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.settings_guide_step5_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_guide_step5_desc), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuideDialog = false }) { Text(stringResource(R.string.action_close)) }
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

    // 1 Page Indicator = Setiap 4 Palet Warna (36 Palet = 9 Titik Indikator)
    val totalPages = remember(palettes.size) { (palettes.size + 3) / 4 }

    // Hitung dot index secara dinamis: bergeser setiap 4 warna dilewati
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
                        // 3-Segment Preview Circle (Top Half: Primary, Bottom Left: Secondary, Bottom Right: Tertiary)
                        Canvas(modifier = Modifier.size(50.dp)) {
                            // Top Half (Primary)
                            drawArc(
                                color = palette.primary,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = true
                            )
                            // Bottom Left Quadrant (Secondary)
                            drawArc(
                                color = palette.secondary,
                                startAngle = 90f,
                                sweepAngle = 90f,
                                useCenter = true
                            )
                            // Bottom Right Quadrant (Tertiary)
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

        // Indikator Titik: 1 Titik per 4 Warna (Total 9 Titik untuk 36 Palet)
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
