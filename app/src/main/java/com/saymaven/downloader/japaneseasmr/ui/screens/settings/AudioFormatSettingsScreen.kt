package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFormatSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val rawPriority by viewModel.audioFormatPriority.collectAsState()
    val preloadSeconds by viewModel.audioPreloadSeconds.collectAsState()
    val audioGainDb by viewModel.audioGainDb.collectAsState()

    var draftAudioGainDb by remember(audioGainDb) { mutableFloatStateOf(audioGainDb) }
    var draftPreloadSeconds by remember(preloadSeconds) { mutableIntStateOf(preloadSeconds) }

    // Playback States
    val defaultSpeed by viewModel.defaultSpeed.collectAsState()
    var draftSpeed by remember(defaultSpeed) { mutableFloatStateOf(defaultSpeed) }

    val exclusiveUsbDac by viewModel.exclusiveUsbDac.collectAsState()
    val audioFocus by viewModel.audioFocus.collectAsState()
    val pauseOnUnplug by viewModel.pauseOnUnplug.collectAsState()
    val skipSilence by viewModel.skipSilence.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val autoResume by viewModel.autoResume.collectAsState()

    val formatList = remember(rawPriority) {
        rawPriority.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    fun getFormatLabel(formatKey: String): String {
        return when (formatKey.lowercase()) {
            "mp3_se" -> "MP3 (SE あり)"
            "mp3" -> "MP3 (Standard 320k)"
            "flac" -> "FLAC (Lossless Hi-Res)"
            "wav" -> "WAV (Uncompressed PCM)"
            "m4a", "aac" -> "M4A / AAC"
            "opus" -> "Opus"
            else -> formatKey.uppercase()
        }
    }

    fun moveFormat(fromIndex: Int, toIndex: Int) {
        if (fromIndex in formatList.indices && toIndex in formatList.indices && fromIndex != toIndex) {
            val item = formatList.removeAt(fromIndex)
            formatList.add(toIndex, item)
            viewModel.setAudioFormatPriority(formatList.joinToString(","))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_audio_format_and_playback),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.setAudioFormatPriority("mp3_se,mp3,flac,wav,opus,m4a,aac")
                        viewModel.setAudioPreloadSeconds(15)
                        viewModel.setAudioGainDb(0f)
                        viewModel.setDefaultSpeed(1.0f)
                    }) {
                        Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.settings_restore_defaults))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Audio Format Priority (Point 6)
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_audio_format_priority),
                icon = Icons.Default.AudioFile
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.settings_audio_format_priority_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    formatList.forEachIndexed { index, formatKey ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = getFormatLabel(formatKey),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { moveFormat(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                            }

                            IconButton(
                                onClick = { moveFormat(index, index + 1) },
                                enabled = index < formatList.lastIndex,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                            }
                        }
                        if (index < formatList.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            }

            // 2. Playback Speed Section
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_speed_title),
                icon = Icons.Default.Speed
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2fx", draftSpeed),
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

                    Spacer(modifier = Modifier.height(14.dp))

                    Slider(
                        value = draftSpeed,
                        onValueChange = { draftSpeed = (kotlin.math.round(it * 20f) / 20f).coerceIn(0.25f, 2.0f) },
                        onValueChangeFinished = { viewModel.setDefaultSpeed(draftSpeed) },
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

                    Spacer(modifier = Modifier.height(14.dp))

                    val presetsRow1 = listOf(0.5f, 0.75f, 1.0f)
                    val presetsRow2 = listOf(1.25f, 1.5f, 2.0f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetsRow1.forEach { preset ->
                            val isSelected = (draftSpeed == preset)
                            Surface(
                                onClick = {
                                    draftSpeed = preset
                                    viewModel.setDefaultSpeed(preset)
                                },
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
                                onClick = {
                                    draftSpeed = preset
                                    viewModel.setDefaultSpeed(preset)
                                },
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
            }

            // 3. Audio Gain & Preload Sliders (Point 2)
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_audio_gain),
                icon = Icons.Default.GraphicEq
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_audio_gain),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_audio_gain_desc, String.format(Locale.US, "%+.1f", draftAudioGainDb)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Slider(
                        value = draftAudioGainDb,
                        onValueChange = { draftAudioGainDb = (it * 2).toInt() / 2f },
                        onValueChangeFinished = { viewModel.setAudioGainDb(draftAudioGainDb) },
                        valueRange = -12f..12f,
                        steps = 47
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_preload_next),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${stringResource(R.string.settings_preload_next_desc)} ($draftPreloadSeconds s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Slider(
                        value = draftPreloadSeconds.toFloat(),
                        onValueChange = { draftPreloadSeconds = it.toInt() },
                        onValueChangeFinished = { viewModel.setAudioPreloadSeconds(draftPreloadSeconds) },
                        valueRange = 5f..30f,
                        steps = 24
                    )
                }
            }

            // 4. Hardware & Playback Options
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_cat_player),
                icon = Icons.AutoMirrored.Filled.VolumeUp
            )

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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
