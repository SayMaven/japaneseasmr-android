package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageAndTranslationSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val appLanguage by viewModel.appLanguage.collectAsState()
    val translationLanguage by viewModel.translationLanguage.collectAsState()
    val autoTranslateLyrics by viewModel.autoTranslateLyrics.collectAsState()
    val autoTranslateDetail by viewModel.autoTranslateDetail.collectAsState()
    val autoTranslateLists by viewModel.autoTranslateLists.collectAsState()

    val uiLanguages = listOf(
        "system" to stringResource(R.string.settings_lang_system),
        "en" to stringResource(R.string.settings_lang_en),
        "in" to stringResource(R.string.settings_lang_in),
        "ja" to stringResource(R.string.settings_lang_ja),
        "zh" to stringResource(R.string.settings_lang_zh)
    )

    val transLanguages = listOf(
        "auto" to stringResource(R.string.settings_trans_lang_auto),
        "id" to stringResource(R.string.settings_trans_lang_in),
        "en" to stringResource(R.string.settings_trans_lang_en),
        "ja" to stringResource(R.string.settings_trans_lang_ja),
        "zh-CN" to stringResource(R.string.settings_trans_lang_zh)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_lang_and_trans_title),
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
            // 1. App UI Language
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_lang_title),
                icon = Icons.Default.Language
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    uiLanguages.forEachIndexed { index, (code, label) ->
                        val isSelected = (appLanguage == code)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAppLanguage(code) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setAppLanguage(code) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (index < uiLanguages.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        }
                    }
                }
            }

            // 2. Translation Target Language
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_target_lang_title),
                icon = Icons.Default.Translate
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    transLanguages.forEachIndexed { index, (code, label) ->
                        val isSelected = (translationLanguage == code)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setTranslationLanguage(code) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setTranslationLanguage(code) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (index < transLanguages.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        }
                    }
                }
            }

            // 3. Auto-Translate Settings (Point 3)
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_cat_auto_translate),
                icon = Icons.Default.Subtitles
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.settings_auto_translate_lyrics),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.settings_auto_translate_lyrics_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = autoTranslateLyrics,
                                onCheckedChange = { viewModel.setAutoTranslateLyrics(it) }
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.settings_auto_translate_detail),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.settings_auto_translate_detail_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = autoTranslateDetail,
                                onCheckedChange = { viewModel.setAutoTranslateDetail(it) }
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.settings_auto_translate_lists),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.settings_auto_translate_lists_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = autoTranslateLists,
                                onCheckedChange = { viewModel.setAutoTranslateLists(it) }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
