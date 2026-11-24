package com.saymaven.downloader.japaneseasmr.ui.screens.history

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.ui.components.PaginationBar
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionItem
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionsSheet
import kotlinx.coroutines.launch

private enum class HistoryStatusFilter {
    ALL,
    PRESENT,
    MISSING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onPlayTrack: (HistoryEntity) -> Unit,
    onRedownload: (HistoryEntity) -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val historyUiList by viewModel.historyUiList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val hasMissingFiles by viewModel.hasMissingFiles.collectAsState()
    val pageSize by viewModel.pageSize.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var itemToDelete by remember { mutableStateOf<HistoryUiItem?>(null) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var statusFilter by rememberSaveable { mutableStateOf(HistoryStatusFilter.ALL) }
    var currentPage by rememberSaveable { mutableStateOf(1) }

    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.onSearchQueryChanged("")
    }

    LaunchedEffect(searchQuery, statusFilter, sortOrder, pageSize) {
        currentPage = 1
    }

    val filteredHistory = remember(historyUiList, statusFilter) {
        when (statusFilter) {
            HistoryStatusFilter.ALL -> historyUiList
            HistoryStatusFilter.PRESENT -> historyUiList.filter { it.isPresent }
            HistoryStatusFilter.MISSING -> historyUiList.filter { !it.isPresent }
        }
    }

    val safePageSize = pageSize.coerceAtLeast(10)
    val totalPages = ((filteredHistory.size + safePageSize - 1) / safePageSize).coerceAtLeast(1)
    val safePage = currentPage.coerceIn(1, totalPages)
    val pagedHistory = remember(filteredHistory, safePage, safePageSize) {
        val start = ((safePage - 1) * safePageSize).coerceAtMost(filteredHistory.size)
        val end = (start + safePageSize).coerceAtMost(filteredHistory.size)
        filteredHistory.subList(start, end)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp)
    ) {
        HistoryHeader(
            count = filteredHistory.size,
            hasMissingFiles = hasMissingFiles,
            hasAnyItems = historyUiList.isNotEmpty(),
            isSearchActive = isSearchExpanded || searchQuery.isNotBlank(),
            onOpenDrawer = onOpenDrawer,
            onOpenSort = { showSortSheet = true },
            onToggleSearch = {
                if (isSearchExpanded) {
                    isSearchExpanded = false
                    viewModel.onSearchQueryChanged("")
                } else {
                    isSearchExpanded = true
                }
            },
            onCleanMissing = {
                viewModel.cleanMissingFiles()
                Toast.makeText(context, context.getString(R.string.history_toast_missing_cleaned), Toast.LENGTH_SHORT).show()
            },
            onClearAll = {
                viewModel.clearAllHistory()
                Toast.makeText(context, context.getString(R.string.history_toast_all_cleared), Toast.LENGTH_SHORT).show()
            }
        )

        AnimatedVisibility(visible = isSearchExpanded) {
            HistorySearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onClose = { isSearchExpanded = false }
            )
        }

        HistoryStatusFilterRow(
            totalCount = historyUiList.size,
            presentCount = historyUiList.count { it.isPresent },
            missingCount = historyUiList.count { !it.isPresent },
            selected = statusFilter,
            onSelect = { statusFilter = it }
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) {
                        stringResource(R.string.history_empty_title)
                    } else {
                        stringResource(R.string.history_no_matching_works)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            val onPlayAction = remember(onPlayTrack) { { entity: HistoryEntity -> onPlayTrack(entity) } }
            val onRedownloadAction = remember(onRedownload) { { entity: HistoryEntity -> onRedownload(entity) } }
            val onDeleteAction = remember { { uiItem: HistoryUiItem -> itemToDelete = uiItem } }

            key(sortOrder, statusFilter) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = pagedHistory,
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

                    item(key = "history_pagination") {
                        PaginationBar(
                            currentPage = safePage,
                            totalPages = totalPages,
                            totalCount = filteredHistory.size,
                            onPageSelected = { targetPage ->
                                if (targetPage != safePage) {
                                    currentPage = targetPage
                                    coroutineScope.launch { listState.scrollToItem(0) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        HistorySortSheet(
            sortOrder = sortOrder,
            onSortSelected = { viewModel.setSortOrder(it) },
            onDismiss = { showSortSheet = false }
        )
    }

    itemToDelete?.let { item ->
        HistoryDeleteDialog(
            item = item,
            onDismiss = { itemToDelete = null },
            onConfirm = { alsoDeleteFile ->
                viewModel.deleteHistory(item.entity, deleteFile = alsoDeleteFile)
                itemToDelete = null
                Toast.makeText(context, context.getString(R.string.history_toast_item_deleted), Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun HistorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.history_search_hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onClose() }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
private fun HistorySortSheet(
    sortOrder: HistorySortOrder,
    onSortSelected: (HistorySortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val isDateField = sortOrder == HistorySortOrder.DATE_DESC || sortOrder == HistorySortOrder.DATE_ASC
    val isAsc = sortOrder == HistorySortOrder.DATE_ASC || sortOrder == HistorySortOrder.TITLE_ASC
    SortOptionsSheet(
        title = stringResource(R.string.browse_sort_title),
        fieldOptions = listOf(
            SortOptionItem("DATE", R.string.history_sort_date_desc),
            SortOptionItem("TITLE", R.string.history_sort_title_asc)
        ),
        selectedField = if (isDateField) "DATE" else "TITLE",
        onFieldSelected = { field ->
            val next = when {
                field == "DATE" && isAsc -> HistorySortOrder.DATE_ASC
                field == "DATE" -> HistorySortOrder.DATE_DESC
                isAsc -> HistorySortOrder.TITLE_ASC
                else -> HistorySortOrder.TITLE_DESC
            }
            onSortSelected(next)
        },
        directionOptions = listOf(
            SortOptionItem(false, R.string.history_sort_date_desc),
            SortOptionItem(true, R.string.history_sort_date_asc)
        ),
        selectedDirection = isAsc,
        onDirectionSelected = { asc ->
            val next = when {
                isDateField && asc -> HistorySortOrder.DATE_ASC
                isDateField -> HistorySortOrder.DATE_DESC
                asc -> HistorySortOrder.TITLE_ASC
                else -> HistorySortOrder.TITLE_DESC
            }
            onSortSelected(next)
        },
        onDismissRequest = onDismiss
    )
}

@Composable
private fun HistoryHeader(
    count: Int,
    hasMissingFiles: Boolean,
    hasAnyItems: Boolean,
    isSearchActive: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSort: () -> Unit,
    onToggleSearch: () -> Unit,
    onCleanMissing: () -> Unit,
    onClearAll: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDrawer, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.history_header_title_format, count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.history_menu_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (hasMissingFiles) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_menu_clean_missing)) },
                        leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                        onClick = { showMenu = false; onCleanMissing() }
                    )
                }
                if (hasAnyItems) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_menu_clear_all), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { showMenu = false; onClearAll() }
                    )
                }
            }
        }
        IconButton(onClick = onOpenSort, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.browse_sort_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleSearch, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (isSearchActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun HistoryStatusFilterRow(
    totalCount: Int,
    presentCount: Int,
    missingCount: Int,
    selected: HistoryStatusFilter,
    onSelect: (HistoryStatusFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selected == HistoryStatusFilter.ALL,
            onClick = { onSelect(HistoryStatusFilter.ALL) },
            label = { Text("Semua ($totalCount)", style = MaterialTheme.typography.labelMedium) },
            shape = RoundedCornerShape(10.dp)
        )
        FilterChip(
            selected = selected == HistoryStatusFilter.PRESENT,
            onClick = { onSelect(HistoryStatusFilter.PRESENT) },
            label = { Text("Tersedia ($presentCount)", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp)) },
            shape = RoundedCornerShape(10.dp)
        )
        FilterChip(
            selected = selected == HistoryStatusFilter.MISSING,
            onClick = { onSelect(HistoryStatusFilter.MISSING) },
            label = { Text("File Hilang ($missingCount)", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = { Icon(Icons.Default.WarningAmber, contentDescription = null, modifier = Modifier.size(15.dp)) },
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun HistoryDeleteDialog(
    item: HistoryUiItem,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var alsoDeleteLocalFile by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                        Checkbox(checked = alsoDeleteLocalFile, onCheckedChange = { alsoDeleteLocalFile = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.history_dialog_also_delete_file), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoDeleteLocalFile) }) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private val HistoryCardShape = RoundedCornerShape(14.dp)
private val HistoryThumbShape = RoundedCornerShape(10.dp)

@Composable
fun HistoryItemCard(
    item: HistoryUiItem,
    onPlay: (HistoryEntity) -> Unit,
    onRedownload: (HistoryEntity) -> Unit,
    onDelete: (HistoryUiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = {
            if (item.isPresent) {
                onPlay(item.entity)
            } else {
                Toast.makeText(context, context.getString(R.string.history_toast_file_not_found), Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = HistoryCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            width = 0.75.dp,
            color = if (item.isPresent) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            }
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            HistoryThumb(coverUrl = item.coverUrl, rjid = item.rjid, item = item.entity)

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (item.isPresent) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
                Text(
                    text = "${item.circle} • ${item.downloadDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.cv.isNotBlank() && item.cv != "-") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = item.cv,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isPresent) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Text(
                            text = if (item.isPresent) item.fileSize else stringResource(R.string.history_status_missing),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isPresent) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HistoryItemActions(
                isPresent = item.isPresent,
                onPrimaryAction = { if (item.isPresent) onPlay(item.entity) else onRedownload(item.entity) },
                onDelete = { onDelete(item) }
            )
        }
    }
}

@Composable
private fun HistoryItemActions(
    isPresent: Boolean,
    onPrimaryAction: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onPrimaryAction, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (isPresent) Icons.Default.PlayArrow else Icons.Default.Download,
                contentDescription = stringResource(
                    if (isPresent) R.string.history_action_play else R.string.history_action_redownload
                ),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.history_action_delete_file),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun HistoryThumb(coverUrl: String?, rjid: String, item: HistoryEntity) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(HistoryThumbShape)
    ) {
        if (!coverUrl.isNullOrBlank()) {
            val imageReq = remember(coverUrl) {
                ImageRequest.Builder(context)
                    .data(coverUrl)
                    .memoryCacheKey(coverUrl)
                    .allowHardware(true)
                    .size(180, 180)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = imageReq,
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(bottomEnd = 6.dp),
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = if (!item.linkedRjId.isNullOrBlank()) item.linkedRjId else rjid,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
