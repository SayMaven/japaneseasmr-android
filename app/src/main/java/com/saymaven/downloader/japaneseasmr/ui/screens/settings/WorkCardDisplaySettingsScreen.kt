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
        topBar = {
            TopAppBar(
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
                        headlineContent = { Text("Rating & Reviews", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display average score and review count", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showRating, onCheckedChange = { viewModel.setCardShowRating(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Price", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display original JPY price", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showPrice, onCheckedChange = { viewModel.setCardShowPrice(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Duration / File Size", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display duration and size badge", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showDuration, onCheckedChange = { viewModel.setCardShowDuration(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Sales / DL Count", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display total sales count", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showSales, onCheckedChange = { viewModel.setCardShowSales(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Release Date", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display original release date", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showReleaseDate, onCheckedChange = { viewModel.setCardShowReleaseDate(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Circle / Maker Name", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display circle or production group name", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showCircle, onCheckedChange = { viewModel.setCardShowCircle(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Subtitle Badge", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display SUB indicator badge", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showSubtitle, onCheckedChange = { viewModel.setCardShowSubtitleTag(it) })
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ListItem(
                        headlineContent = { Text("Age Rating (ALL / R-18)", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Display age classification badge", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(checked = showAgeRating, onCheckedChange = { viewModel.setCardShowAgeRating(it) })
                        }
                    )
                }
            }
        }
    }
}
