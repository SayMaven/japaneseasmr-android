package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
fun UiSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToPlayerButtons: () -> Unit,
    onNavigateToWorkCard: () -> Unit,
    onNavigateToWorkDetail: () -> Unit
) {
    BackHandler { onBack() }

    val pageSize by viewModel.pageSize.collectAsState()
    var showPageSizeDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_cat_ui),
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.settings_player_buttons),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_player_buttons_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.clickable { onNavigateToPlayerButtons() }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.settings_work_card_display),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_work_card_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.ViewAgenda, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.clickable { onNavigateToWorkCard() }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.settings_work_detail_display),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_work_detail_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.clickable { onNavigateToWorkDetail() }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.settings_page_size),
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_page_size_desc, pageSize),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.clickable { showPageSizeDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    viewModel.setPageSize(40)
                    viewModel.setPlayerButtonOrder("repeat,sleep_timer,chapters,translate,shuffle,speed,volume,bookmark")
                    viewModel.setCardShowRating(false)
                    viewModel.setCardShowPrice(false)
                    viewModel.setCardShowDuration(true)
                    viewModel.setCardShowSales(false)
                    viewModel.setCardShowReleaseDate(true)
                    viewModel.setCardShowCircle(true)
                    viewModel.setCardShowSubtitleTag(true)
                    viewModel.setCardShowAgeRating(true)
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
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_restore_defaults))
            }
        }
    }

    if (showPageSizeDialog) {
        val options = listOf(20, 40, 60, 100)
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            title = { Text(stringResource(R.string.settings_page_size)) },
            text = {
                Column {
                    options.forEach { opt ->
                        val isSelected = (opt == pageSize)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPageSize(opt)
                                    showPageSizeDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setPageSize(opt)
                                    showPageSizeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_page_size_desc, opt),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPageSizeDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}
