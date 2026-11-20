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
import androidx.compose.ui.res.stringResource
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
    val showTranslation by viewModel.floatingLyricsShowTranslation.collectAsState()
    val isLocked by viewModel.floatingLyricsLocked.collectAsState()

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
                        viewModel.setFloatingLyricsEnabled(false)
                        viewModel.setFloatingLyricsFontSize(14f)
                        viewModel.setFloatingLyricsOpacity(0.90f)
                        viewModel.setFloatingLyricsCornerRadius(16f)
                        viewModel.setFloatingLyricsShowTranslation(true)
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

            Surface(
                shape = RoundedCornerShape(cornerRadius.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = opacity),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.settings_floating_lyrics_sample_orig),
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    if (showTranslation) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_floating_lyrics_sample_trans),
                            fontSize = (fontSize - 1.5f).coerceAtLeast(10f).sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )
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

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_floating_lyrics_show_trans), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_floating_lyrics_show_trans_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showTranslation, onCheckedChange = { viewModel.setFloatingLyricsShowTranslation(it) })
                        }
                    )
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
                        text = stringResource(R.string.settings_floating_lyrics_font_size, fontSize.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = fontSize,
                        onValueChange = { viewModel.setFloatingLyricsFontSize(it) },
                        valueRange = 10f..24f
                    )

                    Text(
                        text = stringResource(R.string.settings_floating_lyrics_opacity, String.format(Locale.US, "%d%%", (opacity * 100).toInt())),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = opacity,
                        onValueChange = { viewModel.setFloatingLyricsOpacity(it) },
                        valueRange = 0.25f..1.0f
                    )

                    Text(
                        text = stringResource(R.string.settings_floating_lyrics_corner, cornerRadius.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = cornerRadius,
                        onValueChange = { viewModel.setFloatingLyricsCornerRadius(it) },
                        valueRange = 0f..24f
                    )
                }
            }
        }
    }
}
