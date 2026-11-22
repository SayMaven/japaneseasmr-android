package com.saymaven.downloader.japaneseasmr.ui.screens.browse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.model.AsmrOneWork
import com.saymaven.downloader.japaneseasmr.service.TagLocalizer
import com.saymaven.downloader.japaneseasmr.ui.components.PaginationBar
import com.saymaven.downloader.japaneseasmr.ui.components.PlaylistSelectionSheet
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionItem
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionsSheet
import com.saymaven.downloader.japaneseasmr.ui.components.TagFilterSheet
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsmrOneBrowseScreen(
    viewModel: AsmrOneBrowseViewModel,
    playerViewModel: PlayerViewModel,
    onOpenDrawer: () -> Unit,
    onOpenPlayer: () -> Unit = {},
    onSelectWorkForDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isOnline by remember { com.saymaven.downloader.japaneseasmr.util.NetworkMonitor.getInstance(context).isOnline }.collectAsState()

    val works by viewModel.works.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()

    val isTranslationActive by viewModel.isTranslationActive.collectAsState()
    val translatedTitles by viewModel.translatedTitles.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()

    // Persistent View Mode
    val browseViewMode by viewModel.browseViewMode.collectAsState()
    val isListView = browseViewMode == "list"

    // Downloaded & Playlist status indicators
    val localPlaylist by playerViewModel.playlist.collectAsState()
    val downloadedRjids = remember(localPlaylist) { localPlaylist.map { it.rjid }.toSet() }
    val allSavedRjids by playerViewModel.allSavedRjids.collectAsState()

    // Work Card Display Toggles from UI settings
    val showCardRating by viewModel.showCardRating.collectAsState()
    val showCardPrice by viewModel.showCardPrice.collectAsState()
    val showCardDuration by viewModel.showCardDuration.collectAsState()
    val showCardSales by viewModel.showCardSales.collectAsState()
    val showCardReleaseDate by viewModel.showCardReleaseDate.collectAsState()
    val showCardCircle by viewModel.showCardCircle.collectAsState()
    val showCardSubtitle by viewModel.showCardSubtitle.collectAsState()
    val showCardAgeRating by viewModel.showCardAgeRating.collectAsState()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showTagFilterSheet by remember { mutableStateOf(false) }
    var workForPlaylist by remember { mutableStateOf<AsmrOneWork?>(null) }

    // Scroll Position Preservation
    val gridState = rememberLazyStaggeredGridState(
        initialFirstVisibleItemIndex = viewModel.scrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.scrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )

    LaunchedEffect(currentPage) {
        if (currentPage > 0) {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(isOnline) {
        if (isOnline && works.isEmpty()) {
            viewModel.loadPage(viewModel.currentPage.value)
        }
    }

    DisposableEffect(gridState, isListView) {
        onDispose {
            if (!isListView) {
                viewModel.scrollIndex = gridState.firstVisibleItemIndex
                viewModel.scrollOffset = gridState.firstVisibleItemScrollOffset
            }
        }
    }
    DisposableEffect(listState, isListView) {
        onDispose {
            if (isListView) {
                viewModel.scrollIndex = listState.firstVisibleItemIndex
                viewModel.scrollOffset = listState.firstVisibleItemScrollOffset
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        // Top Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Drawer",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            if (!isSearchExpanded) {
                // Category Dropdown Button (Semua / Populer / Rekomendasi)
                var showTabDropdown by remember { mutableStateOf(false) }
                val currentTabLabel = when (currentTab) {
                    BrowseTabType.ALL -> stringResource(R.string.browse_tab_all)
                    BrowseTabType.POPULAR -> stringResource(R.string.browse_btn_popular)
                    BrowseTabType.RECOMMENDED -> stringResource(R.string.browse_btn_recommended)
                }

                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTabDropdown = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = currentTabLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTabDropdown,
                        onDismissRequest = { showTabDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.browse_tab_all),
                                    fontWeight = if (currentTab == BrowseTabType.ALL) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showTabDropdown = false
                                viewModel.setTab(BrowseTabType.ALL)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.browse_btn_popular),
                                    fontWeight = if (currentTab == BrowseTabType.POPULAR) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showTabDropdown = false
                                viewModel.setTab(BrowseTabType.POPULAR)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.browse_btn_recommended),
                                    fontWeight = if (currentTab == BrowseTabType.RECOMMENDED) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showTabDropdown = false
                                viewModel.setTab(BrowseTabType.RECOMMENDED)
                            }
                        )
                    }
                }

                // Dynamic Title Translate Button
                IconButton(
                    onClick = { viewModel.toggleTranslation(context) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = stringResource(R.string.translate_toggle_title),
                        tint = if (isTranslationActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Tag Filter Button
                IconButton(
                    onClick = { showTagFilterSheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalOffer,
                        contentDescription = stringResource(R.string.tag_filter_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // View Toggle Button (Grid / List)
                IconButton(
                    onClick = {
                        val nextMode = if (isListView) "grid" else "list"
                        viewModel.setViewMode(nextMode)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isListView) Icons.Default.GridView else Icons.Default.ViewAgenda,
                        contentDescription = "Toggle View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sort Options Button
                IconButton(
                    onClick = { showSortSheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.browse_sort_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Search Icon Button
                IconButton(
                    onClick = { isSearchExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = {
                        Text(
                            stringResource(R.string.browse_search_hint),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                viewModel.onSearchQueryChange("")
                                isSearchExpanded = false
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                )
            }
        }

        // Active Search / Sort / Filter Chip Bar
        val hasActiveFilters = searchQuery.isNotEmpty() || selectedTags.isNotEmpty() || sortField != AsmrSortField.RELEASE_DATE
        if (hasActiveFilters) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchQuery.isNotEmpty()) {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.onSearchQueryChange("") },
                        label = { Text("\"$searchQuery\"") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear Search",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
                selectedTags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.removeTagFilter(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $tag",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
                if (selectedTags.size > 1) {
                    InputChip(
                        selected = false,
                        onClick = { viewModel.clearTagFilters() },
                        label = { Text(stringResource(R.string.tag_filter_reset)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear All Tags",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
                if (sortField != AsmrSortField.RELEASE_DATE) {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.setSort(AsmrSortField.RELEASE_DATE, AsmrSortDirection.DESC) },
                        label = {
                            Text("${stringResource(sortField.labelRes)} (${stringResource(sortDirection.labelRes)})")
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Reset Sort",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                isLoading && works.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                !isOnline && works.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.network_offline_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.network_offline_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadPage(1) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.browse_action_retry))
                        }
                    }
                }
                errorMessage != null && works.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.browse_error_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadPage(1) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.browse_action_retry))
                        }
                    }
                }
                works.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.browse_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.browse_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (isListView) {
                                // KikoFlu List View style
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(works, key = { it.rjid }) { work ->
                                        val displayTitle = if (isTranslationActive) {
                                            translatedTitles[work.rjid] ?: work.title
                                        } else {
                                            work.title
                                        }
                                        AsmrOneWorkListItem(
                                            work = work,
                                            displayTitle = displayTitle,
                                            isDownloaded = downloadedRjids.contains(work.rjid),
                                            isSavedToPlaylist = allSavedRjids.contains(work.rjid),
                                            showCardCircle = showCardCircle,
                                            showCardReleaseDate = showCardReleaseDate,
                                            onCopyRj = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("RJ Code", work.rjid)
                                                clipboard.setPrimaryClip(clip)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.browse_toast_rj_copied, work.rjid),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onDownloadClick = {
                                                viewModel.enqueueDownload(work)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.browse_toast_enqueued, work.rjid),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onAddToPlaylist = {
                                                workForPlaylist = work
                                            },
                                            onCardClick = {
                                                viewModel.selectWork(work)
                                            }
                                        )
                                    }

                                    item {
                                        PaginationBar(
                                            currentPage = currentPage,
                                            totalPages = totalPages,
                                            totalCount = totalCount,
                                            onPageSelected = { viewModel.goToPage(it) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp, bottom = 20.dp)
                                        )
                                    }
                                }
                            } else {
                                // Pinterest Grid style
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(2),
                                    state = gridState,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalItemSpacing = 8.dp,
                                    contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(works, key = { it.rjid }) { work ->
                                        val displayTitle = if (isTranslationActive) {
                                            translatedTitles[work.rjid] ?: work.title
                                        } else {
                                            work.title
                                        }
                                        AsmrOneWorkCard(
                                            work = work,
                                            displayTitle = displayTitle,
                                            isDownloaded = downloadedRjids.contains(work.rjid),
                                            isSavedToPlaylist = allSavedRjids.contains(work.rjid),
                                            showCardCircle = showCardCircle,
                                            showCardSubtitle = showCardSubtitle,
                                            showCardAgeRating = showCardAgeRating,
                                            showCardReleaseDate = showCardReleaseDate,
                                            onCopyRj = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("RJ Code", work.rjid)
                                                clipboard.setPrimaryClip(clip)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.browse_toast_rj_copied, work.rjid),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onDownloadClick = {
                                                viewModel.enqueueDownload(work)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.browse_toast_enqueued, work.rjid),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onAddToPlaylist = {
                                                workForPlaylist = work
                                            },
                                            onCardClick = {
                                                viewModel.selectWork(work)
                                            }
                                        )
                                    }

                                    item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                        PaginationBar(
                                            currentPage = currentPage,
                                            totalPages = totalPages,
                                            totalCount = totalCount,
                                            onPageSelected = { viewModel.goToPage(it) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp, bottom = 20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Sort Options Bottom Sheet
    if (showSortSheet) {
        val fieldOptions = remember {
            AsmrSortField.values().map {
                SortOptionItem(value = it, labelRes = it.labelRes)
            }
        }
        val directionOptions = remember {
            AsmrSortDirection.values().map {
                SortOptionItem(value = it, labelRes = it.labelRes)
            }
        }

        SortOptionsSheet(
            title = stringResource(R.string.browse_sort_title),
            fieldOptions = fieldOptions,
            selectedField = sortField,
            onFieldSelected = { field ->
                viewModel.setSort(field, sortDirection)
            },
            directionOptions = directionOptions,
            selectedDirection = sortDirection,
            onDirectionSelected = { dir ->
                viewModel.setSort(sortField, dir)
            },
            onDismissRequest = { showSortSheet = false }
        )
    }

    // Tag Filter Sheet
    if (showTagFilterSheet) {
        TagFilterSheet(
            initialSelectedTags = selectedTags,
            onApplyTags = { tags ->
                viewModel.setTagFilters(tags)
                showTagFilterSheet = false
            },
            onDismiss = { showTagFilterSheet = false }
        )
    }

    // Save to Playlist Sheet
    if (workForPlaylist != null) {
        val work = workForPlaylist!!
        PlaylistSelectionSheet(
            workRjid = work.rjid,
            workTitle = work.title,
            coverUrl = work.coverUrl,
            circle = work.circle,
            cv = work.cv,
            playerViewModel = playerViewModel,
            onDismiss = { workForPlaylist = null }
        )
    }
}

/**
 * Pinterest-style Card with status indicators, download at bottom-left, and 3-dots menu at bottom-right.
 */
@Composable
fun AsmrOneWorkCard(
    work: AsmrOneWork,
    displayTitle: String,
    isDownloaded: Boolean,
    isSavedToPlaylist: Boolean,
    showCardCircle: Boolean,
    showCardSubtitle: Boolean,
    showCardAgeRating: Boolean,
    showCardReleaseDate: Boolean,
    onCopyRj: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(12.dp)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable { onCardClick() },
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cover Image Area with Overlay Badges (Aspect Ratio 1.3:1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(work.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = work.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-Left Badges: RJ Code & Download/Playlist Indicators
                Row(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    ) {
                        Text(
                            text = work.rjid,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    if (isDownloaded) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                    if (isSavedToPlaylist) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                                    contentDescription = "In Playlist",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // Top-Right Badge: Age Rating (R18 / SAFE / R-15)
                if (showCardAgeRating) {
                    val normalizedRating = com.saymaven.downloader.japaneseasmr.util.AgeRatingHelper.normalize(work.ageRating)
                    if (normalizedRating != "-") {
                        val ageBgColor = com.saymaven.downloader.japaneseasmr.util.AgeRatingHelper.getBadgeColor(normalizedRating)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ageBgColor,
                            modifier = Modifier
                                .padding(6.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = normalizedRating,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Bottom-Left Badge: Subtitle
                if (showCardSubtitle && work.hasSubtitle) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.88f),
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = "SUB",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Bottom-Right Badge: Release Date
                if (showCardReleaseDate && !work.releaseDate.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = work.releaseDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Info Content Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Circle Name
                if (showCardCircle && work.circle.isNotBlank() && work.circle != "-") {
                    Text(
                        text = work.circle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Voice Actor (CV) - Pill Badge
                if (work.cv.isNotBlank() && work.cv != "-") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = work.cv,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Genre / Tags - English Pill Badges
                if (work.tags.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        work.tags.forEach { rawTag ->
                            val localizedTag = TagLocalizer.localize(null, rawTag)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = localizedTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Action Bar: Download at bottom-left, 3-dots Menu at bottom-right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.browse_action_download),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_action_copy_id)) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onCopyRj()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_action_add_to_playlist)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onAddToPlaylist()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * KikoFlu-style Horizontal Work List Item with status indicators and 3-dots menu.
 */
@Composable
fun AsmrOneWorkListItem(
    work: AsmrOneWork,
    displayTitle: String,
    isDownloaded: Boolean,
    isSavedToPlaylist: Boolean,
    showCardCircle: Boolean,
    showCardReleaseDate: Boolean,
    onCopyRj: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(12.dp)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable { onCardClick() },
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Square Cover image on left with RJ tag overlay & indicators
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(work.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = work.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = work.rjid,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                if (isDownloaded || isSavedToPlaylist) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (isDownloaded) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (isSavedToPlaylist) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                                contentDescription = "In Playlist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right side metadata column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (showCardCircle && work.circle.isNotBlank() && work.circle != "-") {
                    Text(
                        text = work.circle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showCardReleaseDate && !work.releaseDate.isNullOrBlank()) {
                    Text(
                        text = work.releaseDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                if (work.cv.isNotBlank() && work.cv != "-") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = work.cv,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (work.tags.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        work.tags.forEach { rawTag ->
                            val localizedTag = TagLocalizer.localize(null, rawTag)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = localizedTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Download & 3-dots Menu on right
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(R.string.browse_action_download),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_action_copy_id)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onCopyRj()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_action_add_to_playlist)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            }
                        )
                    }
                }
            }
        }
    }
}
