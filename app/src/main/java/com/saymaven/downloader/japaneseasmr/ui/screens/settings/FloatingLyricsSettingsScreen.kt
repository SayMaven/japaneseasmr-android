package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saymaven.downloader.japaneseasmr.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingLyricsSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val enabled by viewModel.floatingLyricsEnabled.collectAsState()
    val fontSize by viewModel.floatingLyricsFontSize.collectAsState()
    val opacity by viewModel.floatingLyricsOpacity.collectAsState()
    val cornerRadius by viewModel.floatingLyricsCornerRadius.collectAsState()
    val displayMode by viewModel.floatingLyricsDisplayMode.collectAsState()
    val isLocked by viewModel.floatingLyricsLocked.collectAsState()

    var draftFontSize by remember(fontSize) { mutableFloatStateOf(fontSize) }
    var draftOpacity by remember(opacity) { mutableFloatStateOf(opacity) }
    var draftCornerRadius by remember(cornerRadius) { mutableFloatStateOf(cornerRadius) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_floating_lyrics),
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
                        draftFontSize = 14f
                        draftOpacity = 0.90f
                        draftCornerRadius = 16f
                        viewModel.setFloatingLyricsEnabled(false)
                        viewModel.setFloatingLyricsFontSize(14f)
                        viewModel.setFloatingLyricsOpacity(0.90f)
                        viewModel.setFloatingLyricsCornerRadius(16f)
                        viewModel.setFloatingLyricsDisplayMode("BOTH")
                        viewModel.setFloatingLyricsLocked(false)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Preview Card (KikoFlu style)
            Text(
                text = stringResource(R.string.settings_floating_lyrics_preview),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            val previewTextColor = if (draftOpacity < 0.35f) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
            val previewTextShadow = if (draftOpacity < 0.35f) {
                Shadow(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 6f
                )
            } else {
                null
            }
            val previewSurfaceColor = if (draftOpacity <= 0.01f) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = draftOpacity)
            }

            Surface(
                shape = RoundedCornerShape(draftCornerRadius.dp),
                color = previewSurfaceColor,
                shadowElevation = if (draftOpacity >= 0.85f) 4.dp else 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (displayMode) {
                        "TRANSLATION_ONLY" -> {
                            Text(
                                text = stringResource(R.string.settings_floating_lyrics_sample_trans),
                                style = TextStyle(
                                    fontSize = draftFontSize.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = previewTextColor,
                                    shadow = previewTextShadow
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        "ORIGINAL_ONLY" -> {
                            Text(
                                text = stringResource(R.string.settings_floating_lyrics_sample_orig),
                                style = TextStyle(
                                    fontSize = draftFontSize.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = previewTextColor,
                                    shadow = previewTextShadow
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.settings_floating_lyrics_sample_orig),
                                style = TextStyle(
                                    fontSize = draftFontSize.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = previewTextColor,
                                    shadow = previewTextShadow
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_floating_lyrics_sample_trans),
                                style = TextStyle(
                                    fontSize = (draftFontSize - 1.5f).coerceAtLeast(10f).sp,
                                    color = previewTextColor.copy(alpha = 0.85f),
                                    shadow = previewTextShadow
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_floating_lyrics_enable), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_floating_lyrics_enable_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = enabled, onCheckedChange = { viewModel.setFloatingLyricsEnabled(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_floating_lyrics_display_mode),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_floating_lyrics_display_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modeOptions = listOf(
                                "BOTH" to stringResource(R.string.settings_floating_lyrics_mode_both),
                                "TRANSLATION_ONLY" to stringResource(R.string.settings_floating_lyrics_mode_translation),
                                "ORIGINAL_ONLY" to stringResource(R.string.settings_floating_lyrics_mode_original)
                            )
                            modeOptions.forEach { (modeKey, modeLabel) ->
                                FilterChip(
                                    selected = displayMode == modeKey,
                                    onClick = { viewModel.setFloatingLyricsDisplayMode(modeKey) },
                                    label = { Text(modeLabel, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_floating_lyrics_lock), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_floating_lyrics_lock_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = isLocked, onCheckedChange = { viewModel.setFloatingLyricsLocked(it) })
                        }
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_floating_lyrics_font_size, draftFontSize.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = draftFontSize,
                        onValueChange = { draftFontSize = it },
                        onValueChangeFinished = { viewModel.setFloatingLyricsFontSize(draftFontSize) },
                        valueRange = 10f..24f
                    )

                    Text(
                        text = stringResource(R.string.settings_floating_lyrics_opacity, String.format(Locale.US, "%d%%", (draftOpacity * 100).toInt())),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = draftOpacity,
                        onValueChange = { draftOpacity = it },
                        onValueChangeFinished = { viewModel.setFloatingLyricsOpacity(draftOpacity) },
                        valueRange = 0.0f..1.0f
                    )

                    Text(
                        text = stringResource(R.string.settings_floating_lyrics_corner, draftCornerRadius.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = draftCornerRadius,
                        onValueChange = { draftCornerRadius = it },
                        onValueChangeFinished = { viewModel.setFloatingLyricsCornerRadius(draftCornerRadius) },
                        valueRange = 0f..24f
                    )
                }
            }
        }
    }
}
