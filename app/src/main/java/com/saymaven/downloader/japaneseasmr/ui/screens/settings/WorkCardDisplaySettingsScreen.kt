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
fun WorkCardDisplaySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val showRating by viewModel.cardShowRating.collectAsState()
    val showPrice by viewModel.cardShowPrice.collectAsState()
    val showDuration by viewModel.cardShowDuration.collectAsState()
    val showSales by viewModel.cardShowSales.collectAsState()
    val showReleaseDate by viewModel.cardShowReleaseDate.collectAsState()
    val showCircle by viewModel.cardShowCircle.collectAsState()
    val showSubtitle by viewModel.cardShowSubtitleTag.collectAsState()
    val showAgeRating by viewModel.cardShowAgeRating.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_work_card_display),
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
                        viewModel.setCardShowRating(false)
                        viewModel.setCardShowPrice(false)
                        viewModel.setCardShowDuration(true)
                        viewModel.setCardShowSales(false)
                        viewModel.setCardShowReleaseDate(true)
                        viewModel.setCardShowCircle(true)
                        viewModel.setCardShowSubtitleTag(true)
                        viewModel.setCardShowAgeRating(true)
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
                    text = stringResource(R.string.settings_work_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_rating), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_rating_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showRating, onCheckedChange = { viewModel.setCardShowRating(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_price), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_price_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showPrice, onCheckedChange = { viewModel.setCardShowPrice(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_duration), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_duration_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showDuration, onCheckedChange = { viewModel.setCardShowDuration(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_sales), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_sales_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showSales, onCheckedChange = { viewModel.setCardShowSales(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_release_date), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_release_date_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showReleaseDate, onCheckedChange = { viewModel.setCardShowReleaseDate(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_circle), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_circle_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showCircle, onCheckedChange = { viewModel.setCardShowCircle(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_subtitle), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_subtitle_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showSubtitle, onCheckedChange = { viewModel.setCardShowSubtitleTag(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_display_age_rating), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.settings_display_age_rating_desc), style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showAgeRating, onCheckedChange = { viewModel.setCardShowAgeRating(it) })
                        }
                    )
                }
            }
        }
    }
}
