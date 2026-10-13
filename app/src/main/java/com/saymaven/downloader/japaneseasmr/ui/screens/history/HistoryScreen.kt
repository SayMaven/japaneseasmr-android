package com.saymaven.downloader.japaneseasmr.ui.screens.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onPlayTrack: (HistoryEntity) -> Unit,
    onRedownload: (HistoryEntity) -> Unit
) {
    val context = LocalContext.current
    val historyUiList by viewModel.historyUiList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val hasMissingFiles by viewModel.hasMissingFiles.collectAsState()

    var itemToDelete by remember { mutableStateOf<HistoryUiItem?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Clean Header with Overflow Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Riwayat Koleksi (${historyUiList.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.history_menu_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (hasMissingFiles) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_menu_clean_missing)) },
                            leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.cleanMissingFiles()
                                Toast.makeText(context, context.getString(R.string.history_toast_missing_cleaned), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    if (historyUiList.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_menu_clear_all), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                viewModel.clearAllHistory()
                                Toast.makeText(context, context.getString(R.string.history_toast_all_cleared), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            label = { Text(stringResource(R.string.history_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Sorting Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isDateActive = (sortOrder == HistorySortOrder.DATE_DESC || sortOrder == HistorySortOrder.DATE_ASC)
            val isTitleActive = (sortOrder == HistorySortOrder.TITLE_ASC || sortOrder == HistorySortOrder.TITLE_DESC)

            FilterChip(
                selected = isDateActive,
                onClick = { viewModel.toggleSortByDate() },
                label = {
                    Text(
                        text = if (sortOrder == HistorySortOrder.DATE_ASC) stringResource(R.string.history_sort_date_asc) else stringResource(R.string.history_sort_date_desc),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (sortOrder == HistorySortOrder.DATE_ASC) Icons.Default.ArrowUpward else Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(10.dp)
            )

            FilterChip(
                selected = isTitleActive,
                onClick = { viewModel.toggleSortByTitle() },
                label = {
                    Text(
                        text = if (sortOrder == HistorySortOrder.TITLE_DESC) stringResource(R.string.history_sort_title_desc) else stringResource(R.string.history_sort_title_asc),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (sortOrder == HistorySortOrder.TITLE_DESC) Icons.Default.ArrowUpward else Icons.Default.SortByAlpha,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (historyUiList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) stringResource(R.string.history_empty_title) else stringResource(R.string.history_no_matching_works),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            val onPlayAction = remember(onPlayTrack) { { entity: HistoryEntity -> onPlayTrack(entity) } }
            val onRedownloadAction = remember(onRedownload) { { entity: HistoryEntity -> onRedownload(entity) } }
            val onDeleteAction = remember { { uiItem: HistoryUiItem -> itemToDelete = uiItem } }

            key(sortOrder) {
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = historyUiList,
                        key = { it.rjid },
                        contentType = { "history_item" }
                    ) { item ->
                        HistoryItemCard(
                            item = item,
                            onPlay = onPlayAction,
                            onRedownload = onRedownloadAction,
                            onDelete = onDeleteAction
                        )
                    }
                }
            }
        }
    }

    itemToDelete?.let { item ->
        var alsoDeleteLocalFile by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.history_dialog_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.history_dialog_delete_msg_format, item.rjid, item.title))
                    if (item.isPresent) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { alsoDeleteLocalFile = !alsoDeleteLocalFile }
                        ) {
                            Checkbox(
                                checked = alsoDeleteLocalFile,
                                onCheckedChange = { alsoDeleteLocalFile = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.history_dialog_also_delete_file), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHistory(item.entity, deleteFile = alsoDeleteLocalFile)
                        itemToDelete = null
                        Toast.makeText(context, context.getString(R.string.history_toast_item_deleted), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private val HistoryCardShape = RoundedCornerShape(12.dp)
private val HistoryThumbShape = RoundedCornerShape(8.dp)

@Composable
fun HistoryItemCard(
    item: HistoryUiItem,
    onPlay: (HistoryEntity) -> Unit,
    onRedownload: (HistoryEntity) -> Unit,
    onDelete: (HistoryUiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (item.isPresent) {
                    onPlay(item.entity)
                } else {
                    Toast.makeText(context, context.getString(R.string.history_toast_file_not_found), Toast.LENGTH_SHORT).show()
                }
            },
        shape = HistoryCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPresent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val coverUrl = item.coverUrl
            if (!coverUrl.isNullOrBlank()) {
                val imageReq = remember(coverUrl) {
                    ImageRequest.Builder(context)
                        .data(coverUrl)
                        .memoryCacheKey(coverUrl)
                        .size(140, 104)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageReq,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(70.dp, 52.dp)
                        .clip(HistoryThumbShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(70.dp, 52.dp)
                        .clip(HistoryThumbShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "[${item.rjid}] ${item.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (item.isPresent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "CV: ${item.cv} \u2022 ${item.circle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.isPresent) "${item.downloadDate} \u2022 ${item.fileSize}" else stringResource(R.string.history_status_missing),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row {
                if (item.isPresent) {
                    IconButton(onClick = { onPlay(item.entity) }) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.history_action_play),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(onClick = { onRedownload(item.entity) }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.history_action_redownload),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = { onDelete(item) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.history_action_delete_file),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
