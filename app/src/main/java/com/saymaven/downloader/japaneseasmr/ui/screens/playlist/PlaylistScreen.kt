package com.saymaven.downloader.japaneseasmr.ui.screens.playlist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.PlaylistItemEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.UserPlaylistEntity
import com.saymaven.downloader.japaneseasmr.service.StorageSyncManager
import com.saymaven.downloader.japaneseasmr.service.TagLocalizer
import com.saymaven.downloader.japaneseasmr.ui.components.PaginationBar
import com.saymaven.downloader.japaneseasmr.ui.components.PlaylistSelectionSheet
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionItem
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionsSheet
import com.saymaven.downloader.japaneseasmr.ui.components.TagFilterSheet
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlaylistSortType
import com.saymaven.downloader.japaneseasmr.ui.screens.playlist.components.*
import com.saymaven.downloader.japaneseasmr.util.AgeRatingHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PlaylistScreen(
    viewModel: PlayerViewModel,
    onOpenPlayer: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    onViewWorkDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val playlist by viewModel.playlist.collectAsState()
    val allDownloadedRjids = remember(playlist) { playlist.map { it.rjid.uppercase() }.toSet() }
    val filteredPlaylist by viewModel.filteredPlaylist.collectAsState()
    val availableFolders by viewModel.availableFolders.collectAsState()
    val folderItemsMap by viewModel.folderItemsMap.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val currentRjId by viewModel.currentRjId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val sortState by viewModel.sortState.collectAsState()
    val isTranslationActive by viewModel.isPlaylistTranslationActive.collectAsState()
    val playlistTranslatedTitles by viewModel.playlistTranslatedTitles.collectAsState()
    val homeViewMode by viewModel.homeViewMode.collectAsState()
    val isGridMode = (homeViewMode == "grid")
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val allSavedRjids by viewModel.allSavedRjids.collectAsState()
    var workForPlaylist by remember { mutableStateOf<HistoryEntity?>(null) }

    var selectedCategoryTab by rememberSaveable { mutableStateOf(0) } // 0: All, 1: Folder, 2: Playlist
    var openedFolderInTab by rememberSaveable { mutableStateOf<String?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategoryTab, openedFolderInTab) {
        when (selectedCategoryTab) {
            0 -> viewModel.selectFolder("Semua")
            1 -> viewModel.selectFolder(openedFolderInTab ?: "Semua")
        }
    }

    // Search & Tag Filter State (Multi-Tag Support)
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTagFilters by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTagFilterSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        searchQuery = ""
    }

    val allLocalTags = remember(playlist) {
        playlist.map { it.genre }
            .filter { it.isNotBlank() && it != "-" }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    val displayedPlaylist = remember(
        filteredPlaylist,
        selectedTagFilters,
        searchQuery,
        playlistTranslatedTitles
    ) {
        val tagFiltered = if (selectedTagFilters.isEmpty()) {
            filteredPlaylist
        } else {
            filteredPlaylist.filter { item ->
                val itemGenres = item.genre.split(",").map { it.trim() }
                selectedTagFilters.all { filterTag ->
                    val filterEn = TagLocalizer.localize(null, filterTag)
                    val filterJp = TagLocalizer.getJapaneseName(filterTag)
                    itemGenres.any { g ->
                        g.equals(filterTag, ignoreCase = true) ||
                            (filterJp != null && (g.equals(filterJp, ignoreCase = true) || g.contains(filterJp, ignoreCase = true))) ||
                            TagLocalizer.localize(null, g).equals(filterEn, ignoreCase = true) ||
                            (filterEn.isNotBlank() && TagLocalizer.localize(null, g).contains(filterEn, ignoreCase = true))
                    }
                }
            }
        }
        val q = searchQuery.trim()
        if (q.isBlank()) {
            tagFiltered
        } else {
            tagFiltered.filter { item ->
                val translatedTitle = playlistTranslatedTitles[item.rjid].orEmpty()
                item.title.contains(q, ignoreCase = true) ||
                    translatedTitle.contains(q, ignoreCase = true) ||
                    item.rjid.contains(q, ignoreCase = true) ||
                    (item.linkedRjId?.contains(q, ignoreCase = true) == true) ||
                    item.cv.contains(q, ignoreCase = true) ||
                    item.circle.contains(q, ignoreCase = true) ||
                    item.genre.contains(q, ignoreCase = true)
            }
        }
    }

    val pageSize by viewModel.pageSize.collectAsState()
    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val totalPages = maxOf(1, (displayedPlaylist.size + pageSize - 1) / pageSize)

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()

    val showCardRating by viewModel.showCardRating.collectAsState()
    val showCardPrice by viewModel.showCardPrice.collectAsState()
    val showCardDuration by viewModel.showCardDuration.collectAsState()
    val showCardSales by viewModel.showCardSales.collectAsState()
    val showCardReleaseDate by viewModel.showCardReleaseDate.collectAsState()
    val showCardCircle by viewModel.showCardCircle.collectAsState()
    val showCardSubtitle by viewModel.showCardSubtitle.collectAsState()
    val showCardAgeRating by viewModel.showCardAgeRating.collectAsState()

    LaunchedEffect(sortState, selectedFolder, selectedTagFilters) {
        currentPage = 1
        if (isGridMode) gridState.scrollToItem(0) else listState.scrollToItem(0)
    }

    LaunchedEffect(displayedPlaylist.size) {
        if (currentPage > totalPages) {
            currentPage = 1
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage > 0) {
            if (isGridMode) {
                gridState.scrollToItem(0)
            } else {
                listState.scrollToItem(0)
            }
        }
    }

    val pagedPlaylist = remember(displayedPlaylist, currentPage, pageSize) {
        val start = (currentPage - 1) * pageSize
        displayedPlaylist.drop(start).take(pageSize)
    }

    LaunchedEffect(isTranslationActive, isGridMode, pagedPlaylist) {
        if (!isTranslationActive || pagedPlaylist.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val visibleIndices = if (isGridMode) {
                gridState.layoutInfo.visibleItemsInfo.map { it.index }
            } else {
                listState.layoutInfo.visibleItemsInfo.map { it.index }
            }
            if (visibleIndices.isEmpty()) {
                pagedPlaylist.take(10)
            } else {
                val minIdx = (visibleIndices.min() - 2).coerceAtLeast(0)
                val maxIdx = (visibleIndices.max() + 2).coerceAtMost(pagedPlaylist.lastIndex)
                pagedPlaylist.subList(minIdx, maxIdx + 1)
            }
        }
            .collect { visibleSlice ->
                val missing = visibleSlice.filter { playlistTranslatedTitles[it.rjid].isNullOrBlank() }
                if (missing.isNotEmpty()) {
                    kotlinx.coroutines.delay(100)
                    viewModel.translateCurrentPageTitles(missing)
                }
            }
    }

    // User Playlist Interaction States
    var selectedUserPlaylist by remember { mutableStateOf<UserPlaylistEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }
    var playlistToRename by remember { mutableStateOf<UserPlaylistEntity?>(null) }
    var renamePlaylistInput by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<UserPlaylistEntity?>(null) }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var showMoveDialog by remember { mutableStateOf(false) }
    var itemToMove by remember { mutableStateOf<HistoryEntity?>(null) }

    var showAudioInfoDialog by remember { mutableStateOf(false) }
    var itemForAudioInfo by remember { mutableStateOf<HistoryEntity?>(null) }

    var itemToLinkRj by remember { mutableStateOf<HistoryEntity?>(null) }
    var rjLinkInput by remember { mutableStateOf("") }
    var isLinkingRj by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<HistoryEntity?>(null) }

    // Drag-and-drop state for List mode
    var draggedRjid by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current

    // Edge auto-scrolling during drag in list mode
    LaunchedEffect(draggedRjid) {
        val rjid = draggedRjid ?: return@LaunchedEffect
        while (true) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val draggedInfo = visibleItems.firstOrNull { it.key == rjid }
            if (draggedInfo != null) {
                val itemTop = draggedInfo.offset + dragAccumulatedY
                val itemBottom = itemTop + draggedInfo.size
                val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                val edgeZone = with(density) { 56.dp.toPx() }

                if (itemTop < edgeZone && listState.canScrollBackward) {
                    val scrollSpeed = ((edgeZone - itemTop) / edgeZone).coerceIn(0.2f, 1f) * -14f
                    listState.scrollBy(scrollSpeed)
                } else if (itemBottom > viewportHeight - edgeZone && listState.canScrollForward) {
                    val scrollSpeed = ((itemBottom - (viewportHeight - edgeZone)) / edgeZone).coerceIn(0.2f, 1f) * 14f
                    listState.scrollBy(scrollSpeed)
                }
            }
            delay(16)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp)
    ) {
        // Top Header matching Jelajah ASMR
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

            val headerCount = when {
                selectedCategoryTab == 1 && openedFolderInTab == null -> playlist.size
                selectedCategoryTab == 2 -> playlist.size
                else -> displayedPlaylist.size
            }
            Text(
                text = stringResource(R.string.player_playlist_sheet_header, headerCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            // Dynamic Title Translate Button
            IconButton(
                onClick = { viewModel.togglePlaylistTranslation() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = stringResource(R.string.translate_toggle_title),
                    tint = if (isTranslationActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tag Filter Button (Multi-Tag)
            IconButton(
                onClick = { showTagFilterSheet = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalOffer,
                    contentDescription = stringResource(R.string.tag_filter_title),
                    tint = if (selectedTagFilters.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Toggle Grid / List Layout (Persistent)
            IconButton(
                onClick = { viewModel.setHomeViewMode(if (isGridMode) "list" else "grid") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = if (isGridMode) stringResource(R.string.hiby_mode_list) else stringResource(R.string.hiby_mode_grid),
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

            // Search Toggle Button (far right, matching Jelajah ASMR)
            IconButton(
                onClick = {
                    if (isSearchExpanded) {
                        isSearchExpanded = false
                        searchQuery = ""
                    } else {
                        isSearchExpanded = true
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isSearchExpanded || searchQuery.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        AnimatedVisibility(visible = isSearchExpanded) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.browse_search_hint),
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
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) {
                                searchQuery = ""
                            } else {
                                isSearchExpanded = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close)
                        )
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

        // Active Tag Filter Badges (Horizontal scrolling row)
        if (selectedTagFilters.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedTagFilters.forEach { tag ->
                    val displayName = TagLocalizer.getLocalizedDisplayName(context, null, tag)
                    InputChip(
                        selected = true,
                        onClick = {
                            selectedTagFilters = selectedTagFilters - tag
                        },
                        label = { Text(displayName) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.tag_filter_clear),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                if (selectedTagFilters.size > 1) {
                    FilledTonalButton(
                        onClick = { selectedTagFilters = emptySet() },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(stringResource(R.string.tag_filter_reset), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Navigation Category Tabs: All, Folders, Playlists
        TabRow(
            selectedTabIndex = selectedCategoryTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Tab(
                selected = selectedCategoryTab == 0,
                onClick = {
                    selectedCategoryTab = 0
                    openedFolderInTab = null
                    viewModel.selectFolder("Semua")
                },
                text = { Text(stringResource(R.string.hiby_tab_all), style = MaterialTheme.typography.labelMedium) }
            )
            Tab(
                selected = selectedCategoryTab == 1,
                onClick = { selectedCategoryTab = 1 },
                text = { Text(stringResource(R.string.hiby_tab_folders), style = MaterialTheme.typography.labelMedium) }
            )
            Tab(
                selected = selectedCategoryTab == 2,
                onClick = { selectedCategoryTab = 2 },
                text = { Text(stringResource(R.string.playlist_tab_title), style = MaterialTheme.typography.labelMedium) }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Main Track, Folder, or Playlist Content
        if (selectedCategoryTab == 1 && openedFolderInTab == null) {
            val folderList = remember(availableFolders) {
                availableFolders.filter { it != "Semua" }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.hiby_tab_folders)} (${folderList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = {
                            newFolderNameInput = ""
                            showNewFolderDialog = true
                        }
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.player_folder_new))
                    }
                }

                if (folderList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.player_folder_all_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val folderEntries = folderList.map { folder ->
                        val itemsInFolder = remember(folderItemsMap, folder) {
                            folderItemsMap.entries
                                .firstOrNull { it.key.equals(folder, ignoreCase = true) }
                                ?.value ?: emptyList()
                        }
                        val covers = remember(itemsInFolder) {
                            itemsInFolder.map { it.coverUrl }.filter { it.isNotBlank() }.distinct().take(4)
                        }
                        val folderDisplayName = if (folder == "Utama") {
                            stringResource(R.string.player_folder_main)
                        } else {
                            folder
                        }
                        ExplorerFolderEntry(
                            key = folder,
                            title = folderDisplayName,
                            subtitle = stringResource(R.string.playlist_item_count, itemsInFolder.size),
                            coverUrls = covers,
                            onClick = {
                                openedFolderInTab = folder
                                viewModel.selectFolder(folder)
                            }
                        )
                    }
                    ExplorerFolderCollection(
                        entries = folderEntries,
                        isGridMode = isGridMode
                    )
                }
            }
        } else if (selectedCategoryTab == 2) {
            if (selectedUserPlaylist != null) {
                BackHandler { selectedUserPlaylist = null }
                val currentPl = selectedUserPlaylist!!
                val playlistItems by viewModel.getItemsForPlaylist(currentPl.id).collectAsState(initial = null)

                LaunchedEffect(isTranslationActive, playlistItems) {
                    if (isTranslationActive && !playlistItems.isNullOrEmpty()) {
                        viewModel.translateCustomPlaylistTitles(playlistItems!!)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedUserPlaylist = null },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentPl.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (playlistItems != null) {
                                Text(
                                    text = stringResource(R.string.playlist_item_count, playlistItems!!.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!playlistItems.isNullOrEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    val itemsList = playlistItems ?: emptyList()
                                    itemsList.firstOrNull()?.let { firstItem ->
                                        viewModel.playPlaylistItem(firstItem, itemsList)
                                        onOpenPlayer()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.player_action_play))
                            }
                        }
                    }

                    if (playlistItems == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                    } else if (playlistItems!!.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.playlist_items_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (isGridMode) {
                        val itemsList = playlistItems!!
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp,
                            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(itemsList, key = { it.id }) { item ->
                                val isCurrentTrack = (item.rjid == currentRjId)
                                val displayTitle = if (isTranslationActive) playlistTranslatedTitles[item.rjid] ?: item.title else item.title
                                val isOffline = allDownloadedRjids.contains(item.rjid.uppercase())
                                PlaylistItemGridCard(
                                    item = item,
                                    displayTitle = displayTitle,
                                    isCurrentTrack = isCurrentTrack,
                                    isOffline = isOffline,
                                    onClick = {
                                        viewModel.playPlaylistItem(item, itemsList)
                                        onOpenPlayer()
                                    },
                                    onRemove = {
                                        viewModel.removeWorkFromPlaylist(currentPl.id, item.rjid)
                                    },
                                    onCopyRj = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("RJ Code", item.rjid))
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        Toast.makeText(context, context.getString(R.string.browse_toast_rj_copied, item.rjid), Toast.LENGTH_SHORT).show()
                                    },
                                    onViewDetail = {
                                        onViewWorkDetail(item.rjid)
                                    }
                                )
                            }
                        }
                    } else {
                        val itemsList = playlistItems ?: emptyList()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
                        ) {
                            items(itemsList, key = { it.id }) { item ->
                                val displayTitle = if (isTranslationActive) playlistTranslatedTitles[item.rjid] ?: item.title else item.title
                                val isOffline = allDownloadedRjids.contains(item.rjid.uppercase())
                                PlaylistItemRow(
                                    item = item,
                                    displayTitle = displayTitle,
                                    isOffline = isOffline,
                                    onClick = {
                                        viewModel.playPlaylistItem(item, itemsList)
                                        onOpenPlayer()
                                    },
                                    onRemove = {
                                        viewModel.removeWorkFromPlaylist(currentPl.id, item.rjid)
                                    },
                                    onCopyRj = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("RJ Code", item.rjid))
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        Toast.makeText(context, context.getString(R.string.browse_toast_rj_copied, item.rjid), Toast.LENGTH_SHORT).show()
                                    },
                                    onViewDetail = {
                                        onViewWorkDetail(item.rjid)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${stringResource(R.string.playlist_tab_title)} (${userPlaylists.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = {
                                newPlaylistNameInput = ""
                                showCreatePlaylistDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.playlist_dialog_new))
                        }
                    }

                    if (userPlaylists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.playlist_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val playlistEntries = userPlaylists.map { pl ->
                            key(pl.id) {
                                val plItems by viewModel.getItemsForPlaylist(pl.id).collectAsState(emptyList())
                                val covers = remember(plItems) {
                                    plItems.map { it.coverUrl }.filter { it.isNotBlank() }.distinct().take(4)
                                }
                                ExplorerFolderEntry(
                                    key = pl.id,
                                    title = pl.name,
                                    subtitle = stringResource(R.string.playlist_item_count, plItems.size),
                                    coverUrls = covers,
                                    onClick = { selectedUserPlaylist = pl },
                                    onRename = {
                                        playlistToRename = pl
                                        renamePlaylistInput = pl.name
                                    },
                                    onDelete = { playlistToDelete = pl }
                                )
                            }
                        }
                        ExplorerFolderCollection(
                            entries = playlistEntries,
                            isGridMode = isGridMode
                        )
                    }
                }
            }
        } else {
            if (selectedCategoryTab == 1 && openedFolderInTab != null) {
                BackHandler {
                    openedFolderInTab = null
                    viewModel.selectFolder("Semua")
                }
                val currentFolder = openedFolderInTab!!
                val folderDisplayName = if (currentFolder == "Utama") {
                    stringResource(R.string.player_folder_main)
                } else {
                    currentFolder
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            openedFolderInTab = null
                            viewModel.selectFolder("Semua")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.playlist_item_count, displayedPlaylist.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (displayedPlaylist.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.playLocalTrack(displayedPlaylist.first(), displayedPlaylist)
                                onOpenPlayer()
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.player_action_play))
                        }
                    }
                }
            }

            if (displayedPlaylist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedFolder == "Semua" || selectedFolder == stringResource(R.string.player_folder_all)) {
                            stringResource(R.string.player_folder_all_empty)
                        } else {
                            val folderDisplay = if (selectedFolder == "Utama") stringResource(R.string.player_folder_main) else selectedFolder
                            stringResource(R.string.player_folder_empty_format, folderDisplay)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val onItemClickAction = remember(displayedPlaylist) {
                    { clickedItem: HistoryEntity ->
                        viewModel.playLocalTrack(clickedItem, displayedPlaylist)
                        onOpenPlayer()
                    }
                }
                val onMoveFolderClick = remember {
                    { item: HistoryEntity ->
                        itemToMove = item
                        showMoveDialog = true
                    }
                }
                val onCopyRjClick = remember {
                    { item: HistoryEntity ->
                        val rjToCopy = item.linkedRjId ?: item.rjid
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("RJ Code", rjToCopy))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(
                            context,
                            context.getString(R.string.browse_toast_rj_copied, rjToCopy),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                val onAudioInfoClick = remember {
                    { item: HistoryEntity ->
                        itemForAudioInfo = item
                        showAudioInfoDialog = true
                    }
                }
                val onLinkRjClick = remember {
                    { item: HistoryEntity ->
                        val candidate = item.linkedRjId?.takeIf { it.isNotBlank() }
                            ?: StorageSyncManager.RJ_REGEX.find(item.rjid)?.value
                            ?: StorageSyncManager.RJ_REGEX.find(item.title)?.value
                            ?: StorageSyncManager.RJ_REGEX.find(File(item.localFilePath).name)?.value
                            ?: ""
                        rjLinkInput = candidate
                        itemToLinkRj = item
                    }
                }
                val onDeleteClick = remember {
                    { item: HistoryEntity ->
                        itemToDelete = item
                        showDeleteDialog = true
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (isGridMode) {
                            // Pinterest Staggered Grid Layout (2 Columns)
                            LazyVerticalStaggeredGrid(
                                state = gridState,
                                columns = StaggeredGridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp,
                                contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(pagedPlaylist, key = { it.rjid }) { item ->
                                    val isCurrentTrack = (item.rjid == currentRjId)
                                    val validHistoryTrans = if (!item.translatedTitle.isNullOrBlank() && item.translatedTitle != item.title) item.translatedTitle else null
                                    val displayTitle = if (isTranslationActive) playlistTranslatedTitles[item.rjid] ?: validHistoryTrans ?: item.title else item.title
                                    LocalWorkPinterestCard(
                                        item = item,
                                        displayTitle = displayTitle,
                                        isCurrentTrack = isCurrentTrack,
                                        isPlaying = (isCurrentTrack && isPlaying),
                                        isInPlaylist = allSavedRjids.contains(item.rjid),
                                        showCardCircle = showCardCircle,
                                        showCardSubtitle = showCardSubtitle,
                                        showCardAgeRating = showCardAgeRating,
                                        showCardReleaseDate = showCardReleaseDate,
                                        onClick = { onItemClickAction(item) },
                                        onMoveFolder = { onMoveFolderClick(item) },
                                        onCopyRj = { onCopyRjClick(item) },
                                        onAudioInfo = { onAudioInfoClick(item) },
                                        onDelete = { onDeleteClick(item) },
                                        onAddToPlaylist = { workForPlaylist = item },
                                        onViewDetail = { onViewWorkDetail(item.linkedRjId ?: item.rjid) },
                                        onLinkRj = { onLinkRjClick(item) }
                                    )
                                }

                                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                    PaginationBar(
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        totalCount = displayedPlaylist.size,
                                        onPageSelected = { targetPage ->
                                            if (targetPage != currentPage) {
                                                currentPage = targetPage
                                                coroutineScope.launch {
                                                    gridState.scrollToItem(0)
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp, bottom = 20.dp)
                                    )
                                }
                            }
                        } else {
                            // KikoFlu-style Horizontal List View
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
                            ) {
                                items(pagedPlaylist, key = { it.rjid }) { item ->
                                    val isCurrentTrack = (item.rjid == currentRjId)
                                    val validHistoryTrans = if (!item.translatedTitle.isNullOrBlank() && item.translatedTitle != item.title) item.translatedTitle else null
                                    val displayTitle = if (isTranslationActive) playlistTranslatedTitles[item.rjid] ?: validHistoryTrans ?: item.title else item.title
                                    LocalWorkListViewItem(
                                        modifier = Modifier.fillMaxWidth(),
                                        item = item,
                                        displayTitle = displayTitle,
                                        isCurrentTrack = isCurrentTrack,
                                        isPlaying = (isCurrentTrack && isPlaying),
                                        isInPlaylist = allSavedRjids.contains(item.rjid),
                                        showCardCircle = showCardCircle,
                                        showCardSubtitle = showCardSubtitle,
                                        showCardAgeRating = showCardAgeRating,
                                        showCardReleaseDate = showCardReleaseDate,
                                        onClick = onItemClickAction,
                                        onMoveFolder = { onMoveFolderClick(item) },
                                        onCopyRj = { onCopyRjClick(item) },
                                        onAudioInfo = { onAudioInfoClick(item) },
                                        onDelete = { onDeleteClick(item) },
                                        onAddToPlaylist = { workForPlaylist = item },
                                        onViewDetail = { onViewWorkDetail(item.linkedRjId ?: item.rjid) },
                                        onLinkRj = { onLinkRjClick(item) }
                                    )
                                }

                                item {
                                    PaginationBar(
                                        currentPage = currentPage,
                                        totalPages = totalPages,
                                        totalCount = displayedPlaylist.size,
                                        onPageSelected = { targetPage ->
                                            if (targetPage != currentPage) {
                                                currentPage = targetPage
                                                coroutineScope.launch {
                                                    listState.scrollToItem(0)
                                                }
                                            }
                                        },
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

    if (showNewFolderDialog) {
        PlaylistNewFolderDialog(
            initialName = newFolderNameInput,
            onCreate = { viewModel.createNewFolder(it) },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    if (showMoveDialog) {
        PlaylistMoveToFolderDialog(
            item = itemToMove,
            availableFolders = availableFolders,
            onMove = { target, folder -> viewModel.moveTrackToFolder(target, folder) },
            onDismiss = { showMoveDialog = false; itemToMove = null }
        )
    }

    if (workForPlaylist != null) {
        val work = workForPlaylist!!
        PlaylistSelectionSheet(
            workRjid = work.rjid,
            workTitle = work.title,
            coverUrl = work.coverUrl ?: "",
            circle = work.circle,
            cv = work.cv,
            playerViewModel = viewModel,
            onDismiss = { workForPlaylist = null }
        )
    }

    if (showAudioInfoDialog && itemForAudioInfo != null) {
        PlaylistAudioInfoDialog(
            info = itemForAudioInfo!!,
            onDismiss = { showAudioInfoDialog = false; itemForAudioInfo = null }
        )
    }

    if (itemToLinkRj != null) {
        PlaylistLinkRjDialog(
            target = itemToLinkRj!!,
            initialRj = rjLinkInput,
            viewModel = viewModel,
            onDismiss = { itemToLinkRj = null }
        )
    }

    if (showDeleteDialog && itemToDelete != null) {
        val target = itemToDelete!!
        PlaylistDeleteWorkDialog(
            target = target,
            onConfirmDelete = {
                viewModel.deleteTrack(target)
                Toast.makeText(context, context.getString(R.string.player_toast_deleted), Toast.LENGTH_SHORT).show()
                showDeleteDialog = false
                itemToDelete = null
            },
            onDismiss = { showDeleteDialog = false; itemToDelete = null }
        )
    }

    if (showSortSheet) {
        PlaylistSortBottomSheet(
            sortState = sortState,
            onSetSort = { field, isAsc -> viewModel.setSort(field, isAsc) },
            onDismiss = { showSortSheet = false }
        )
    }

    if (showCreatePlaylistDialog) {
        PlaylistCreateDialog(
            onCreate = { viewModel.createPlaylist(it) },
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }

    if (playlistToRename != null) {
        val target = playlistToRename!!
        PlaylistRenameDialog(
            target = target,
            onRename = { viewModel.renamePlaylist(target.id, it) },
            onDismiss = { playlistToRename = null }
        )
    }

    if (playlistToDelete != null) {
        val target = playlistToDelete!!
        PlaylistDeleteDialog(
            target = target,
            onDelete = { viewModel.deletePlaylist(target); playlistToDelete = null },
            onDismiss = { playlistToDelete = null }
        )
    }

    if (showTagFilterSheet) {
        TagFilterSheet(
            availableTags = allLocalTags,
            initialSelectedTags = selectedTagFilters,
            onApplyTags = { tags -> selectedTagFilters = tags; showTagFilterSheet = false },
            onDismiss = { showTagFilterSheet = false }
        )
    }
}
