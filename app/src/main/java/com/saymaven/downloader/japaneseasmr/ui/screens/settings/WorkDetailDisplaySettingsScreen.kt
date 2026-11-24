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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDetailDisplaySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val showRating by viewModel.detailShowRating.collectAsState()
    val showPrice by viewModel.detailShowPrice.collectAsState()
    val showDuration by viewModel.detailShowDuration.collectAsState()
    val showSales by viewModel.detailShowSales.collectAsState()
    val showReleaseDate by viewModel.detailShowReleaseDate.collectAsState()
    val showTranslateBtn by viewModel.detailShowTranslateBtn.collectAsState()
    val showSubtitle by viewModel.detailShowSubtitle.collectAsState()
    val showAgeRating by viewModel.detailShowAgeRating.collectAsState()
    val showRecommendations by viewModel.detailShowRecommendations.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_work_detail_display),
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
                        viewModel.setDetailShowRating(false)
                        viewModel.setDetailShowPrice(false)
                        viewModel.setDetailShowDuration(true)
                        viewModel.setDetailShowSales(false)
                        viewModel.setDetailShowReleaseDate(true)
                        viewModel.setDetailShowTranslateBtn(true)
                        viewModel.setDetailShowSubtitle(true)
                        viewModel.setDetailShowAgeRating(true)
                        viewModel.setDetailShowRecommendations(true)
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_work_detail_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_rating), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_rating_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showRating, onCheckedChange = { viewModel.setDetailShowRating(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_price), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_price_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showPrice, onCheckedChange = { viewModel.setDetailShowPrice(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_duration), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_duration_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showDuration, onCheckedChange = { viewModel.setDetailShowDuration(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_sales), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_sales_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showSales, onCheckedChange = { viewModel.setDetailShowSales(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_release_date), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_release_date_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showReleaseDate, onCheckedChange = { viewModel.setDetailShowReleaseDate(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_translate_btn), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_translate_btn_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showTranslateBtn, onCheckedChange = { viewModel.setDetailShowTranslateBtn(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_subtitle), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_subtitle_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showSubtitle, onCheckedChange = { viewModel.setDetailShowSubtitle(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_age_rating), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_age_rating_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showAgeRating, onCheckedChange = { viewModel.setDetailShowAgeRating(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_recommendations), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_recommendations_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showRecommendations, onCheckedChange = { viewModel.setDetailShowRecommendations(it) })
                        }
                    )
                }
            }
        }
    }
}
