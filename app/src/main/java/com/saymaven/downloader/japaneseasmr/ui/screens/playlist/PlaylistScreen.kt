package com.saymaven.downloader.japaneseasmr.ui.screens.playlist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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

    // Tag Filter State (Multi-Tag Support)
    var selectedTagFilters by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTagFilterSheet by remember { mutableStateOf(false) }

    val allLocalTags = remember(playlist) {
        playlist.map { it.genre }
            .filter { it.isNotBlank() && it != "-" }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    val displayedPlaylist = remember(filteredPlaylist, selectedTagFilters) {
        if (selectedTagFilters.isEmpty()) filteredPlaylist
        else {
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
                                    viewModel.playPlaylistItem(playlistItems!!.first())
                                    onOpenPlayer()
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
                                        viewModel.playPlaylistItem(item)
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
                                        viewModel.playPlaylistItem(item)
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

    // Dialog: Create New Folder
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.player_folder_create_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.player_folder_create_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderNameInput,
                        onValueChange = { newFolderNameInput = it },
                        placeholder = { Text(stringResource(R.string.player_folder_create_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newFolderNameInput.isNotBlank()) {
                        viewModel.createNewFolder(newFolderNameInput.trim())
                        showNewFolderDialog = false
                    }
                }) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // Dialog: Move to Folder
    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = {
                showMoveDialog = false
                itemToMove = null
            },
            title = { Text(stringResource(R.string.player_folder_move_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val allFolderLabel = stringResource(R.string.player_folder_all)
                    val targetFolders = availableFolders.filter { it != "Semua" && it != allFolderLabel }
                    val workTitle = "[${itemToMove?.rjid ?: ""}] ${itemToMove?.title ?: ""}"
                    Text(stringResource(R.string.player_folder_move_desc, workTitle), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    targetFolders.forEach { folder ->
                        Surface(
                            onClick = {
                                val target = itemToMove
                                if (target != null) {
                                    viewModel.moveTrackToFolder(target, folder)
                                }
                                showMoveDialog = false
                                itemToMove = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                val folderDisplayName = if (folder == "Utama") stringResource(R.string.player_folder_main) else folder
                                Text(folderDisplayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showMoveDialog = false
                    itemToMove = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Sheet: Save Local Work to Playlist
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

    // Dialog: Audio Format Specification & Details
    if (showAudioInfoDialog && itemForAudioInfo != null) {
        val info = itemForAudioInfo!!
        val detectedFormat = detectAudioFormat(info.localFilePath)

        AlertDialog(
            onDismissRequest = {
                showAudioInfoDialog = false
                itemForAudioInfo = null
            },
            title = { Text(stringResource(R.string.player_audio_info_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.player_audio_info_format, detectedFormat),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.player_audio_info_channels, "Stereo 2.0 (Binaural)"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.player_audio_info_size, info.fileSize),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CV: ${info.cv}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Circle: ${info.circle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.player_audio_info_path, info.localFilePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showAudioInfoDialog = false
                    itemForAudioInfo = null
                }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // Dialog: Link DLsite RJ Code
    if (itemToLinkRj != null) {
        val target = itemToLinkRj!!
        AlertDialog(
            onDismissRequest = {
                if (!isLinkingRj) {
                    itemToLinkRj = null
                }
            },
            title = { Text(stringResource(R.string.player_link_rj_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.player_link_rj_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = rjLinkInput,
                        onValueChange = { rjLinkInput = it },
                        label = { Text(stringResource(R.string.player_link_rj_input_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLinkingRj
                    )
                    if (isLinkingRj) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Menghubungkan ke DLsite...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rjLinkInput.isNotBlank() && !isLinkingRj) {
                            isLinkingRj = true
                            viewModel.linkRjCode(target.rjid, rjLinkInput) { success, rjOrMsg ->
                                isLinkingRj = false
                                if (success) {
                                    itemToLinkRj = null
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.player_link_rj_success_toast, rjOrMsg),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        rjOrMsg ?: context.getString(R.string.player_link_rj_error_toast, rjLinkInput),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    enabled = !isLinkingRj && rjLinkInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.player_link_rj_btn_link))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemToLinkRj = null },
                    enabled = !isLinkingRj
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Dialog: Delete Work Confirmation
    if (showDeleteDialog && itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                itemToDelete = null
            },
            title = { Text(stringResource(R.string.player_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.player_delete_confirm_desc, target.title),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrack(target)
                        Toast.makeText(context, context.getString(R.string.player_toast_deleted), Toast.LENGTH_SHORT).show()
                        showDeleteDialog = false
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    itemToDelete = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Sort Options Bottom Sheet
    if (showSortSheet) {
        val fieldOptions = remember {
            listOf(
                SortOptionItem(value = PlaylistSortType.DATE, labelRes = R.string.playlist_sort_date),
                SortOptionItem(value = PlaylistSortType.ALL_AGES, labelRes = R.string.browse_sort_all_ages),
                SortOptionItem(value = PlaylistSortType.TITLE, labelRes = R.string.playlist_sort_title),
                SortOptionItem(value = PlaylistSortType.DURATION, labelRes = R.string.playlist_sort_duration),
                SortOptionItem(value = PlaylistSortType.RJID, labelRes = R.string.playlist_sort_rj),
                SortOptionItem(value = PlaylistSortType.CUSTOM, labelRes = R.string.playlist_sort_custom)
            )
        }
        val directionOptions = remember {
            listOf(
                SortOptionItem(value = true, labelRes = R.string.browse_sort_asc),
                SortOptionItem(value = false, labelRes = R.string.browse_sort_desc)
            )
        }

        SortOptionsSheet(
            title = stringResource(R.string.browse_sort_title),
            fieldOptions = fieldOptions,
            selectedField = sortState.type,
            onFieldSelected = { field ->
                viewModel.setSort(field, sortState.isAscending)
            },
            directionOptions = directionOptions,
            selectedDirection = sortState.isAscending,
            onDirectionSelected = { isAsc ->
                viewModel.setSort(sortState.type, isAsc)
            },
            onDismissRequest = { showSortSheet = false }
        )
    }

    // Dialog: Create New Playlist
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(R.string.playlist_dialog_new)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistNameInput,
                    onValueChange = { newPlaylistNameInput = it },
                    placeholder = { Text(stringResource(R.string.playlist_dialog_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPlaylistNameInput.isNotBlank()) {
                        viewModel.createPlaylist(newPlaylistNameInput.trim())
                        showCreatePlaylistDialog = false
                    }
                }) {
                    Text(stringResource(R.string.playlist_dialog_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Dialog: Rename Playlist
    if (playlistToRename != null) {
        val target = playlistToRename!!
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text(stringResource(R.string.playlist_dialog_rename)) },
            text = {
                OutlinedTextField(
                    value = renamePlaylistInput,
                    onValueChange = { renamePlaylistInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renamePlaylistInput.isNotBlank()) {
                        viewModel.renamePlaylist(target.id, renamePlaylistInput.trim())
                        playlistToRename = null
                    }
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Dialog: Delete Playlist Confirmation
    if (playlistToDelete != null) {
        val target = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.playlist_dialog_delete)) },
            text = {
                Text(
                    text = "${stringResource(R.string.playlist_dialog_delete)}: \"${target.name}\"?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(target)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Tag Filter Sheet (Multi-Tag)
    if (showTagFilterSheet) {
        TagFilterSheet(
            availableTags = allLocalTags,
            initialSelectedTags = selectedTagFilters,
            onApplyTags = { tags ->
                selectedTagFilters = tags
                showTagFilterSheet = false
            },
            onDismiss = { showTagFilterSheet = false }
        )
    }
}

private data class ExplorerFolderEntry(
    val key: Any,
    val title: String,
    val subtitle: String,
    val coverUrls: List<String>,
    val onClick: () -> Unit,
    val onRename: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null
)

@Composable
private fun ExplorerFolderCollection(
    entries: List<ExplorerFolderEntry>,
    isGridMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (isGridMode) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(entries, key = { it.key }) { entry ->
                ExplorerFolderCard(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    coverUrls = entry.coverUrls,
                    onClick = entry.onClick,
                    onRename = entry.onRename,
                    onDelete = entry.onDelete
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
        ) {
            items(entries, key = { it.key }) { entry ->
                ExplorerFolderListRow(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    coverUrls = entry.coverUrls,
                    onClick = entry.onClick,
                    onRename = entry.onRename,
                    onDelete = entry.onDelete
                )
            }
        }
    }
}

@Composable
private fun FolderThumbnailCoverTile(
    coverUrl: String,
    cornerRadius: androidx.compose.ui.unit.Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(coverUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(cornerRadius))
    )
}

@Composable
private fun FolderThumbnailPreviewBox(
    coverUrls: List<String>,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val folderTabColor = Color(0xFFD9A438)
    val folderBorderColor = Color(0xFFE6B64B).copy(alpha = 0.7f)
    val spacing = if (compact) 2.dp else 4.dp
    val tileRadius = if (compact) 3.dp else 6.dp
    val innerPad = if (compact) 3.dp else 6.dp

    Column(modifier = modifier) {
        // Top-left Windows File Explorer folder tab lip
        Box(
            modifier = Modifier
                .padding(start = if (compact) 4.dp else 8.dp)
                .width(if (compact) 26.dp else 56.dp)
                .height(if (compact) 5.dp else 9.dp)
                .background(
                    color = folderTabColor,
                    shape = RoundedCornerShape(
                        topStart = if (compact) 4.dp else 7.dp,
                        topEnd = if (compact) 4.dp else 7.dp
                    )
                )
        )

        Surface(
            shape = RoundedCornerShape(
                topStart = if (compact) 2.dp else 3.dp,
                topEnd = if (compact) 7.dp else 12.dp,
                bottomStart = if (compact) 7.dp else 12.dp,
                bottomEnd = if (compact) 7.dp else 12.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, folderBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (coverUrls.size) {
                    0 -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = folderTabColor.copy(alpha = 0.85f),
                                modifier = Modifier.size(if (compact) 24.dp else 42.dp)
                            )
                        }
                    }
                    1 -> {
                        FolderThumbnailCoverTile(
                            coverUrl = coverUrls[0],
                            cornerRadius = tileRadius,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPad)
                        )
                    }
                    2 -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPad),
                            horizontalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            FolderThumbnailCoverTile(
                                coverUrl = coverUrls[0],
                                cornerRadius = tileRadius,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            FolderThumbnailCoverTile(
                                coverUrl = coverUrls[1],
                                cornerRadius = tileRadius,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPad),
                            verticalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                FolderThumbnailCoverTile(
                                    coverUrl = coverUrls[0],
                                    cornerRadius = tileRadius,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                FolderThumbnailCoverTile(
                                    coverUrl = coverUrls[1],
                                    cornerRadius = tileRadius,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                FolderThumbnailCoverTile(
                                    coverUrl = coverUrls[2],
                                    cornerRadius = tileRadius,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                if (coverUrls.size >= 4) {
                                    FolderThumbnailCoverTile(
                                        coverUrl = coverUrls[3],
                                        cornerRadius = tileRadius,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(tileRadius),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = folderTabColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(if (compact) 12.dp else 18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Explorer folder front sleeve strip
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(if (compact) 5.dp else 8.dp)
                        .background(folderTabColor.copy(alpha = 0.85f))
                )
            }
        }
    }
}

@Composable
private fun ExplorerFolderOptionsMenu(
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    if (onRename == null && onDelete == null) return
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { isMenuExpanded = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = { isMenuExpanded = false }
        ) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_dialog_rename)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        isMenuExpanded = false
                        onRename()
                    }
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_dialog_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        isMenuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun ExplorerFolderCard(
    title: String,
    subtitle: String,
    coverUrls: List<String>,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            FolderThumbnailPreviewBox(
                coverUrls = coverUrls,
                compact = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.22f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ExplorerFolderOptionsMenu(onRename = onRename, onDelete = onDelete)
            }
        }
    }
}

@Composable
fun ExplorerFolderListRow(
    title: String,
    subtitle: String,
    coverUrls: List<String>,
    onClick: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderThumbnailPreviewBox(
                coverUrls = coverUrls,
                compact = true,
                modifier = Modifier.size(width = 84.dp, height = 66.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onRename != null || onDelete != null) {
                ExplorerFolderOptionsMenu(onRename = onRename, onDelete = onDelete)
            } else {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistItemOptionsMenu(
    onCopyRj: () -> Unit,
    onRemove: () -> Unit,
    onViewDetail: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { isMenuExpanded = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = tint
            )
        }
        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = { isMenuExpanded = false }
        ) {
            if (onViewDetail != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browse_action_view_detail)) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        isMenuExpanded = false
                        onViewDetail()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browse_action_copy_id)) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    isMenuExpanded = false
                    onCopyRj()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_dialog_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    isMenuExpanded = false
                    onRemove()
                }
            )
        }
    }
}

@Composable
fun PlaylistItemGridCard(
    item: PlaylistItemEntity,
    displayTitle: String = item.title,
    isCurrentTrack: Boolean = false,
    isOffline: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onCopyRj: () -> Unit,
    onViewDetail: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentTrack) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTrack) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
            ) {
                if (item.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = item.rjid,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Badge Status Unduhan (OFFLINE vs ONLINE)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isOffline) Color(0xFF2E7D32).copy(alpha = 0.9f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Text(
                        text = if (isOffline) stringResource(R.string.playlist_badge_offline) else stringResource(R.string.playlist_badge_online),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopEnd)
                ) {
                    PlaylistItemOptionsMenu(
                        onCopyRj = onCopyRj,
                        onRemove = onRemove,
                        onViewDetail = onViewDetail,
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.circle.isNotBlank() && item.circle != "-") {
                    Text(
                        text = item.circle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistItemRow(
    item: PlaylistItemEntity,
    displayTitle: String = item.title,
    isOffline: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onCopyRj: () -> Unit,
    onViewDetail: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = item.rjid,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isOffline) Color(0xFF2E7D32).copy(alpha = 0.9f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = if (isOffline) stringResource(R.string.playlist_badge_offline) else stringResource(R.string.playlist_badge_online),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.cv.isNotBlank() && item.cv != "-") {
                    Text(
                        text = "CV: ${item.cv}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            PlaylistItemOptionsMenu(
                onCopyRj = onCopyRj,
                onRemove = onRemove,
                onViewDetail = onViewDetail
            )
        }
    }
}

/**
 * Detect audio format label (FLAC, WAV, MP3, etc.) based on file path or name.
 */
private fun detectAudioFormat(path: String?): String {
    if (path.isNullOrBlank()) return "AUDIO"
    val lower = path.lowercase()
    return when {
        lower.endsWith(".flac") -> "FLAC Hi-Res"
        lower.endsWith(".wav") -> "WAV 24-bit"
        lower.endsWith(".mp3") -> "MP3 320k"
        lower.endsWith(".m4a") || lower.endsWith(".aac") -> "AAC"
        lower.endsWith(".ogg") -> "OGG"
        File(path).isDirectory -> "HI-RES ALBUM"
        else -> "PCM AUDIO"
    }
}

/**
 * Pinterest-style local work card with 1.3:1 cover aspect ratio,
 * audio format badges, and 3-dots overflow menu.
 */
@Composable
fun LocalWorkPinterestCard(
    item: HistoryEntity,
    displayTitle: String = item.title,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isInPlaylist: Boolean = false,
    onClick: () -> Unit,
    onMoveFolder: () -> Unit,
    onCopyRj: () -> Unit,
    onAudioInfo: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onViewDetail: (() -> Unit)? = null,
    onLinkRj: (() -> Unit)? = null,
    showCardCircle: Boolean = true,
    showCardSubtitle: Boolean = true,
    showCardAgeRating: Boolean = true,
    showCardReleaseDate: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isMenuExpanded by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(10.dp)
    val detectedFormat = remember(item.localFilePath) { detectAudioFormat(item.localFilePath) }
    val hasSubtitle = remember(item.localFilePath, item.chaptersJson) {
        (!item.chaptersJson.isNullOrBlank() && (item.chaptersJson.contains(".vtt") || item.chaptersJson.contains(".lrc") || item.chaptersJson.contains(".srt"))) ||
        try {
            val f = java.io.File(item.localFilePath)
            if (f.isDirectory) {
                f.walkTopDown().maxDepth(3).any { it.isFile && it.extension.lowercase() in setOf("vtt", "lrc", "srt") }
            } else if (f.isFile) {
                val subExts = setOf("vtt", "lrc", "srt")
                subExts.any { ext -> java.io.File(f.parentFile, "${f.nameWithoutExtension}.$ext").exists() }
            } else {
                false
            }
        } catch (_: Exception) { false }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable { onClick() },
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentTrack) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTrack) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cover Image Area (Aspect Ratio 1.3:1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
            ) {
                val cover = item.coverUrl
                if (!cover.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(cover)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Top-Left Badge: RJ Code & In-Playlist Indicator
                val isLocal = item.sourceType != "DL_SITE" || item.rjid.startsWith("LOCAL_")
                val displayTag = when {
                    isLocal && !item.linkedRjId.isNullOrBlank() -> item.linkedRjId!!
                    isLocal -> "IMPORT"
                    else -> item.rjid
                }
                Row(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = displayTag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    if (isInPlaylist) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                                contentDescription = "In Playlist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(15.dp)
                            )
                        }
                    }
                }

                // Top-Right Badge: Age Rating (SAFE / R-15 / R18)
                if (showCardAgeRating) {
                    val normalizedRating = AgeRatingHelper.normalize(item.ageRating)
                    if (normalizedRating != "-") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AgeRatingHelper.getBadgeColor(normalizedRating),
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
                if (showCardSubtitle && hasSubtitle) {
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

                // Bottom-Right Badge: File Size / Date
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    val badgeText = if (showCardReleaseDate && item.downloadDate.isNotBlank()) {
                        "${item.fileSize} • ${item.downloadDate}"
                    } else {
                        item.fileSize
                    }
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Info Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title - Full title without truncation
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )

                // Circle Name (Plain text, no badge pill)
                if (showCardCircle && item.circle.isNotBlank() && item.circle != "-") {
                    Text(
                        text = item.circle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Voice Actor (CV) Pill Badge
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

                // Genre / Tags Pill Badges
                val genreList = remember(item.genre) {
                    item.genre?.split(",", "/", "、", ";")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()
                }
                if (genreList.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        genreList.forEach { rawTag ->
                            val tag = TagLocalizer.localize(null, rawTag)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Action Row: Play/Equalizer & 3-dots Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrentTrack) {
                        AnimatedEqualizerWave(
                            isPlaying = isPlaying,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.player_action_play),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { isMenuExpanded = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.player_menu_more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            if (onViewDetail != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browse_action_view_detail)) },
                                    onClick = {
                                        isMenuExpanded = false
                                        onViewDetail()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Info, contentDescription = null)
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_action_add_to_playlist)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onAddToPlaylist()
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_folder_move_title)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onMoveFolder()
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_copy_rj)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onCopyRj()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_audio_info)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onAudioInfo()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AudioFile, contentDescription = null)
                                }
                            )

                            if (onLinkRj != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_menu_link_rj)) },
                                    onClick = {
                                        isMenuExpanded = false
                                        onLinkRj()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Link, contentDescription = null)
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_play_now)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Replay, contentDescription = null)
                                }
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.player_menu_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
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
 * KikoFlu-style Horizontal Work List Item for Local Collection matching Screenshot 4.
 */
@Composable
fun LocalWorkListViewItem(
    modifier: Modifier = Modifier,
    item: HistoryEntity,
    displayTitle: String = item.title,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isInPlaylist: Boolean = false,
    onClick: (HistoryEntity) -> Unit,
    onMoveFolder: () -> Unit,
    onCopyRj: () -> Unit,
    onAudioInfo: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onViewDetail: (() -> Unit)? = null,
    onLinkRj: (() -> Unit)? = null,
    showCardCircle: Boolean = true,
    showCardSubtitle: Boolean = true,
    showCardAgeRating: Boolean = true,
    showCardReleaseDate: Boolean = true
) {
    val context = LocalContext.current
    var isMenuExpanded by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)
    val hasSubtitle = remember(item.localFilePath, item.chaptersJson) {
        (!item.chaptersJson.isNullOrBlank() && (item.chaptersJson.contains(".vtt") || item.chaptersJson.contains(".lrc") || item.chaptersJson.contains(".srt"))) ||
        try {
            val f = java.io.File(item.localFilePath)
            if (f.isDirectory) {
                f.walkTopDown().maxDepth(3).any { it.isFile && it.extension.lowercase() in setOf("vtt", "lrc", "srt") }
            } else if (f.isFile) {
                val subExts = setOf("vtt", "lrc", "srt")
                subExts.any { ext -> java.io.File(f.parentFile, "${f.nameWithoutExtension}.$ext").exists() }
            } else {
                false
            }
        } catch (_: Exception) { false }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable { onClick(item) },
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentTrack) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTrack) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left: Square Cover art (90dp x 90dp) with badges
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                val cover = item.coverUrl
                if (!cover.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(cover)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Top-Left Badge: RJ Code & In-Playlist
                val isLocal = item.sourceType != "DL_SITE" || item.rjid.startsWith("LOCAL_")
                val displayTag = when {
                    isLocal && !item.linkedRjId.isNullOrBlank() -> item.linkedRjId!!
                    isLocal -> "IMPORT"
                    else -> item.rjid
                }
                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = displayTag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (isInPlaylist) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                                contentDescription = "In Playlist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(13.dp)
                            )
                        }
                    }
                }

                // Top-Right Badge: Age Rating (SAFE / R-15 / R18)
                if (showCardAgeRating) {
                    val normalizedRating = AgeRatingHelper.normalize(item.ageRating)
                    if (normalizedRating != "-") {
                        Surface(
                            shape = RoundedCornerShape(bottomStart = 6.dp),
                            color = AgeRatingHelper.getBadgeColor(normalizedRating),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = normalizedRating,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Bottom-Left Badge: Subtitle
                if (showCardSubtitle && hasSubtitle) {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 6.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.88f),
                        modifier = Modifier.align(Alignment.BottomStart)
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right side metadata column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title (full title)
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                // Circle Name & Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCardCircle && item.circle.isNotBlank() && item.circle != "-") {
                        Text(
                            text = item.circle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (showCardReleaseDate && item.downloadDate.isNotBlank()) {
                        Text(
                            text = item.downloadDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // CV Pill Badge
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

                // Genre / Tags in English Pill Badges
                val genreList = remember(item.genre) {
                    item.genre?.split(",", "/", "、", ";")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()
                }
                if (genreList.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        genreList.forEach { rawTag ->
                            val tag = TagLocalizer.localize(null, rawTag)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Trailing Actions: Play / Equalizer and 3-dots Menu
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isCurrentTrack) {
                    AnimatedEqualizerWave(
                        isPlaying = isPlaying,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = { onClick(item) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.player_action_play),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { isMenuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.player_menu_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        if (onViewDetail != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_action_view_detail)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onViewDetail()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_action_add_to_playlist)) },
                            onClick = {
                                isMenuExpanded = false
                                onAddToPlaylist()
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_action_move_folder)) },
                            onClick = {
                                isMenuExpanded = false
                                onMoveFolder()
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                            }
                        )

                        val rjToCopy = item.linkedRjId ?: item.rjid
                        if (rjToCopy.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_copy_rj, rjToCopy)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onCopyRj()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_menu_audio_info)) },
                            onClick = {
                                isMenuExpanded = false
                                onAudioInfo()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AudioFile, contentDescription = null)
                            }
                        )

                        if (onLinkRj != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_link_rj)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onLinkRj()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Link, contentDescription = null)
                                }
                            )
                        }

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.player_menu_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private val PlaylistItemCardShape = RoundedCornerShape(12.dp)
private val PlaylistItemThumbShape = RoundedCornerShape(6.dp)

@Composable
fun PlaylistItemCard(
    modifier: Modifier = Modifier,
    item: HistoryEntity,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isDragged: Boolean,
    dragOffsetY: Float,
    onDragStart: (String) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: (HistoryEntity) -> Unit,
    onMoveFolder: () -> Unit,
    onCopyRj: () -> Unit,
    onAudioInfo: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isMenuExpanded by remember { mutableStateOf(false) }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentRjid by rememberUpdatedState(item.rjid)

    val cardModifier = if (isDragged) {
        modifier
            .fillMaxWidth()
            .zIndex(100f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = 1.03f
                scaleY = 1.03f
                shadowElevation = 30f
                shape = PlaylistItemCardShape
                clip = false
            }
            .clickable { onClick(item) }
    } else {
        modifier
            .fillMaxWidth()
            .clickable { onClick(item) }
    }

    Card(
        modifier = cardModifier,
        shape = PlaylistItemCardShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragged -> MaterialTheme.colorScheme.secondaryContainer
                isCurrentTrack -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cover = item.coverUrl
            if (!cover.isNullOrBlank()) {
                val imageReq = remember(cover) {
                    ImageRequest.Builder(context)
                        .data(cover)
                        .memoryCacheKey(cover)
                        .allowHardware(true)
                        .size(120, 90)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageReq,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier
                        .size(54.dp, 40.dp)
                        .clip(PlaylistItemThumbShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp, 40.dp)
                        .clip(PlaylistItemThumbShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val isLocal = item.sourceType != "DL_SITE" || item.rjid.startsWith("LOCAL_")
                val displayTag = when {
                    isLocal && !item.linkedRjId.isNullOrBlank() -> "IMPORT • ${item.linkedRjId}"
                    isLocal -> "IMPORT"
                    else -> item.rjid
                }
                Text(
                    text = "[$displayTag] ${item.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "CV: ${item.cv} • ${item.fileSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrentTrack) {
                AnimatedEqualizerWave(
                    isPlaying = isPlaying,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.player_action_play),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 3-Dots Overflow Menu Button (Replaces old move icon)
            Box {
                IconButton(
                    onClick = { isMenuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.player_menu_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_folder_move_title)) },
                        onClick = {
                            isMenuExpanded = false
                            onMoveFolder()
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_menu_copy_rj)) },
                        onClick = {
                            isMenuExpanded = false
                            onCopyRj()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_menu_audio_info)) },
                        onClick = {
                            isMenuExpanded = false
                            onAudioInfo()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.AudioFile, contentDescription = null)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_menu_play_now)) },
                        onClick = {
                            isMenuExpanded = false
                            onClick(item)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Replay, contentDescription = null)
                        }
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.player_menu_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }

            // Dedicated Drag Handle Touch Target
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pointerInput(currentRjid) {
                        detectVerticalDragGestures(
                            onDragStart = { currentOnDragStart(currentRjid) },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDrag(dragAmount)
                            },
                            onDragEnd = { currentOnDragEnd() },
                            onDragCancel = { currentOnDragEnd() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.player_action_reorder),
                    tint = if (isDragged) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedEqualizerWave(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val barColor = color
    if (!isPlaying) {
        androidx.compose.foundation.Canvas(modifier = modifier.size(width = 22.dp, height = 18.dp)) {
            val totalWidth = size.width
            val totalHeight = size.height
            val barWidth = 3.dp.toPx()
            val spacing = (totalWidth - (4 * barWidth)) / 3
            val staticHeights = listOf(0.35f, 0.6f, 0.45f, 0.7f)

            staticHeights.forEachIndexed { i, hFraction ->
                val barHeight = totalHeight * hFraction
                val left = i * (barWidth + spacing)
                val top = (totalHeight - barHeight) / 2
                drawRoundRect(
                    color = barColor.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            }
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "asmr_calm_wave")
        val anim1 by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
        val anim2 by infiniteTransition.animateFloat(
            initialValue = 0.75f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
        val anim3 by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(2100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
        val anim4 by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar4"
        )

        androidx.compose.foundation.Canvas(modifier = modifier.size(width = 22.dp, height = 18.dp)) {
            val totalWidth = size.width
            val totalHeight = size.height
            val barWidth = 3.dp.toPx()
            val spacing = (totalWidth - (4 * barWidth)) / 3
            val animHeights = listOf(anim1, anim2, anim3, anim4)

            animHeights.forEachIndexed { i, hFraction ->
                val barHeight = totalHeight * hFraction
                val left = i * (barWidth + spacing)
                val top = (totalHeight - barHeight) / 2
                drawRoundRect(
                    color = barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            }
        }
    }
}
