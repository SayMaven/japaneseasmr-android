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
    val showExternalLinks by viewModel.detailShowExternalLinks.collectAsState()
    val showReleaseDate by viewModel.detailShowReleaseDate.collectAsState()
    val showTranslateBtn by viewModel.detailShowTranslateBtn.collectAsState()
    val showSubtitle by viewModel.detailShowSubtitle.collectAsState()
    val showAgeRating by viewModel.detailShowAgeRating.collectAsState()
    val showRecommendations by viewModel.detailShowRecommendations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
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
                        viewModel.setDetailShowExternalLinks(true)
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Rating & Reviews", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display review rating section", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showRating, onCheckedChange = { viewModel.setDetailShowRating(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Price", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display JPY pricing details", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showPrice, onCheckedChange = { viewModel.setDetailShowPrice(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Duration", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display audio total duration", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showDuration, onCheckedChange = { viewModel.setDetailShowDuration(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Sales / DL Count", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display total DL count", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showSales, onCheckedChange = { viewModel.setDetailShowSales(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("External Links (DLsite / dlsite.com)", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display button to open original DLsite store page", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showExternalLinks, onCheckedChange = { viewModel.setDetailShowExternalLinks(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Release Date", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display release date information", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showReleaseDate, onCheckedChange = { viewModel.setDetailShowReleaseDate(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Translate Button", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display fast batch translation trigger in tree header", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showTranslateBtn, onCheckedChange = { viewModel.setDetailShowTranslateBtn(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Subtitle Badge", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display subtitle indicator", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showSubtitle, onCheckedChange = { viewModel.setDetailShowSubtitle(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Age Rating (ALL / R-18)", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display age classification badge", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showAgeRating, onCheckedChange = { viewModel.setDetailShowAgeRating(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Recommended Works", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display related recommendations section", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showRecommendations, onCheckedChange = { viewModel.setDetailShowRecommendations(it) })
                        }
                    )
                }
            }
        }
    }
}
