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
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import kotlinx.coroutines.launch

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
                    headlineContent = { Text(stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Build ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )

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
            4 to "4 Jalur (Hemat Kuota)",
            8 to "8 Jalur (Standar)",
            16 to "16 Jalur (Rekomendasi)",
            24 to "24 Jalur (Cepat)",
            32 to "32 Jalur (Maksimum)"
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
                    Text("Versi 1.3.1 (Terbaru)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Multi-Bahasa Antarmuka (UI Localization): Dukungan penuh bahasa Indonesia, English, 日本語 (Jepang), dan 简体中文 (Mandarin).", style = MaterialTheme.typography.bodySmall)
                    Text("• Deteksi Bahasa Sistem Otomatis: Aplikasi menyesuaikan bahasa tampilan mengikuti pengaturan perangkat pengguna secara otomatis.", style = MaterialTheme.typography.bodySmall)
                    Text("• Pemilih Bahasa Manual di Pengaturan: Pengguna dapat bebas memilih bahasa aplikasi tanpa mengubah bahasa sistem ponsel.", style = MaterialTheme.typography.bodySmall)
                    Text("• Kompatibilitas Android 13+ Per-App Language: Mendukung konfigurasi bahasa per-aplikasi bawaan OS Android.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Versi 1.3.0", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Penerjemah ASMR DLsite Cerdas: Terjemahan dwibahasa (Indonesia/Inggris) untuk judul karya & daftar bab/tracklist dengan kamus istilah khusus ASMR DLsite.", style = MaterialTheme.typography.bodySmall)
                    Text("• De-Sensor Fuseji & Normalisasi Teks: Membuka sensor simbol (○, ●, ×) dan menormalkan teks judul agar hasil terjemahan akurat dan alami.", style = MaterialTheme.typography.bodySmall)
                    Text("• Smart Fast Remux Engine: Penggabungan audio multi-segmen instan (< 1 detik) tanpa transcoding demi menjaga kualitas suara 100% loss-free.", style = MaterialTheme.typography.bodySmall)
                    Text("• 4-Tier Hybrid Chapter Engine & CUE Sheet: Penanda bab presisi tinggi, ekspor otomatis berkas .cue, dan lembar daftar bab interaktif (tap-to-seek).", style = MaterialTheme.typography.bodySmall)
                    Text("• Ultra-Smooth 120 FPS Scrolling: Optimasi render daftar putar (playlist drawer) & riwayat download untuk ratusan karya tanpa frame drop / patah-patah.", style = MaterialTheme.typography.bodySmall)
                    Text("• Universal Link & Multi-Site Resolver: Dukungan tautan asmr.one, japaneseasmr.com, DLsite, & auto-prefix RJ.", style = MaterialTheme.typography.bodySmall)
                    Text("• Pembersihan Redundansi & Efisiensi: Optimasi kompilasi regex statis, penghapusan kode redundan, dan O(1) in-memory cache untuk cover & verifikasi berkas.", style = MaterialTheme.typography.bodySmall)
                    Text("• Enhanced Metadata Enrichment: Sinkronisasi CV, Circle, Genre, dan HD Cover dari DLsite & asmr.one.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Versi 1.2.1", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Auto-Sync Penyimpanan Instan (120 FPS): Pemindaian latar belakang bebas lag.", style = MaterialTheme.typography.bodySmall)
                    Text("• Pemindaian Subfolder Rekursif: Deteksi otomatis subfolder penyimpanan.", style = MaterialTheme.typography.bodySmall)
                    Text("• Auto-Fetch Metadata & Cover: Otomatis melengkapi info karya & cover offline.", style = MaterialTheme.typography.bodySmall)
                    Text("• Kategori Subfolder Koleksi: Filter folder otomatis & tombol buat folder baru.", style = MaterialTheme.typography.bodySmall)
                    Text("• Fitur Pindah Folder: Pindahkan berkas antar subfolder secara instan.", style = MaterialTheme.typography.bodySmall)
                    Text("• Perbaikan Scroll Playlist: Pencegahan penutupan tidak sengaja saat scroll cepat.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Versi 1.2.0", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Smart Sleep Timer: Countdown otomatis jeda saat audio di-pause.", style = MaterialTheme.typography.bodySmall)
                    Text("• Floating Mini Player interaktif di tab Unduhan, Riwayat, & Pengaturan.", style = MaterialTheme.typography.bodySmall)
                    Text("• Pop-Up Kecepatan Putar Default baru dengan slider mulus & preset 2x3.", style = MaterialTheme.typography.bodySmall)
                    Text("• Optimasi performa scroll Riwayat hingga 120 FPS tanpa frame drop.", style = MaterialTheme.typography.bodySmall)
                    Text("• Buka daftar putar (playlist) instan 0ms tanpa delay pemindaian.", style = MaterialTheme.typography.bodySmall)
                    Text("• Floating Hardware Volume HUD ultra-ramping dan solid.", style = MaterialTheme.typography.bodySmall)
                    Text("• Retensi tab stabil saat aplikasi diminimize & cold start selalu di Home.", style = MaterialTheme.typography.bodySmall)
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
