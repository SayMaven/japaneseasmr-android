package com.saymaven.downloader.japaneseasmr.ui.screens.playlist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
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
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlaylistSortType
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PlaylistScreen(
    viewModel: PlayerViewModel,
    onOpenPlayer: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val playlist by viewModel.playlist.collectAsState()
    val filteredPlaylist by viewModel.filteredPlaylist.collectAsState()
    val availableFolders by viewModel.availableFolders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val currentRjId by viewModel.currentRjId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val sortState by viewModel.sortState.collectAsState()

    var isGridMode by rememberSaveable { mutableStateOf(true) }
    var selectedCategoryTab by rememberSaveable { mutableStateOf(0) } // 0: All, 1: Folder, 2: CV

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var showMoveDialog by remember { mutableStateOf(false) }
    var itemToMove by remember { mutableStateOf<HistoryEntity?>(null) }

    var showAudioInfoDialog by remember { mutableStateOf(false) }
    var itemForAudioInfo by remember { mutableStateOf<HistoryEntity?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<HistoryEntity?>(null) }

    val listState = rememberLazyListState()

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
        // Top HiBy Header with Drawer Button, Title, Audiophile Badge & Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Drawer",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.player_playlist_sheet_header, filteredPlaylist.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.hiby_badge_bitperfect),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Shuffle All Quick Action
            if (filteredPlaylist.isNotEmpty()) {
                IconButton(
                    onClick = {
                        val shuffled = filteredPlaylist.shuffled()
                        if (shuffled.isNotEmpty()) {
                            viewModel.playLocalTrack(shuffled.first(), filteredPlaylist)
                            onOpenPlayer()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.player_action_shuffle),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Toggle Grid / List Layout
            IconButton(onClick = { isGridMode = !isGridMode }) {
                Icon(
                    imageVector = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = if (isGridMode) stringResource(R.string.hiby_mode_list) else stringResource(R.string.hiby_mode_grid),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // HiBy Navigation Category Tabs: All, Folders, Voice Actors
        TabRow(
            selectedTabIndex = selectedCategoryTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Tab(
                selected = selectedCategoryTab == 0,
                onClick = { selectedCategoryTab = 0; viewModel.selectFolder("Semua") },
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
                text = { Text(stringResource(R.string.hiby_tab_cv), style = MaterialTheme.typography.labelMedium) }
            )
        }

        // Subfolder Selector Row (when Folder tab is active)
        if (selectedCategoryTab == 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(availableFolders) { folder ->
                    val isSelected = (folder == selectedFolder)
                    val folderDisplayName = when (folder) {
                        "Semua" -> stringResource(R.string.player_folder_all)
                        "Utama" -> stringResource(R.string.player_folder_main)
                        else -> folder
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectFolder(folder) },
                        label = { Text(folderDisplayName, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else {
                            { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            newFolderNameInput = ""
                            showNewFolderDialog = true
                        },
                        label = { Text(stringResource(R.string.player_folder_new), style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // Sorting Selector Row (Chips)
        val sortOptions = remember {
            listOf(
                PlaylistSortType.CUSTOM to R.string.playlist_sort_custom,
                PlaylistSortType.DATE to R.string.playlist_sort_date,
                PlaylistSortType.TITLE to R.string.playlist_sort_title,
                PlaylistSortType.DURATION to R.string.playlist_sort_duration,
                PlaylistSortType.RJID to R.string.playlist_sort_rj
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(sortOptions, key = { it.first.name }) { (type, resId) ->
                val label = stringResource(resId)
                val isSelected = (sortState.type == type)
                val arrow = if (isSelected && type != PlaylistSortType.CUSTOM) {
                    if (sortState.isAscending) " ▲" else " ▼"
                } else ""

                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.toggleSort(type) },
                    label = {
                        Text(
                            text = "$label$arrow",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        val icon = when (type) {
                            PlaylistSortType.CUSTOM -> Icons.Default.DragHandle
                            PlaylistSortType.DATE -> if (isSelected && sortState.isAscending) Icons.Default.ArrowUpward else Icons.Default.Schedule
                            PlaylistSortType.TITLE -> if (isSelected && !sortState.isAscending) Icons.Default.ArrowDownward else Icons.Default.SortByAlpha
                            PlaylistSortType.DURATION -> Icons.Default.AccessTime
                            PlaylistSortType.RJID -> Icons.Default.Tag
                        }
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Main Track Content (Pinterest Grid or List)
        if (filteredPlaylist.isEmpty()) {
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
            val onItemClickAction = remember {
                { clickedItem: HistoryEntity ->
                    viewModel.playLocalTrack(clickedItem, filteredPlaylist)
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
            val onDeleteClick = remember {
                { item: HistoryEntity ->
                    itemToDelete = item
                    showDeleteDialog = true
                }
            }

            if (isGridMode) {
                // Pinterest Staggered Grid Layout (2 Columns)
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredPlaylist, key = { it.rjid }) { item ->
                        val isCurrentTrack = (item.rjid == currentRjId)
                        LocalWorkPinterestCard(
                            item = item,
                            isCurrentTrack = isCurrentTrack,
                            isPlaying = (isCurrentTrack && isPlaying),
                            onClick = { onItemClickAction(item) },
                            onMoveFolder = { onMoveFolderClick(item) },
                            onCopyRj = { onCopyRjClick(item) },
                            onAudioInfo = { onAudioInfoClick(item) },
                            onDelete = { onDeleteClick(item) }
                        )
                    }
                }
            } else {
                // Compact List Mode with Drag-and-Drop Reordering
                val onDragStartAction = remember {
                    { rjid: String ->
                        val currentIdx = filteredPlaylist.indexOfFirst { it.rjid == rjid }
                        if (currentIdx != -1) {
                            draggedRjid = rjid
                            dragAccumulatedY = 0f
                        }
                    }
                }
                val onDragAction = remember {
                    { dragAmount: Float ->
                        dragAccumulatedY += dragAmount

                        val targetRjid = draggedRjid ?: return@remember
                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                        val draggedInfo = visibleItems.firstOrNull { it.key == targetRjid }

                        if (draggedInfo != null) {
                            val draggedCenterY = (draggedInfo.offset + dragAccumulatedY) + (draggedInfo.size / 2f)

                            val targetItem = visibleItems.firstOrNull { item ->
                                item.key != targetRjid &&
                                draggedCenterY in item.offset.toFloat()..(item.offset + item.size).toFloat()
                            }

                            if (targetItem != null) {
                                val fromIdx = filteredPlaylist.indexOfFirst { it.rjid == targetRjid }
                                val toIdx = filteredPlaylist.indexOfFirst { it.rjid == targetItem.key }
                                if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                                    val distance = targetItem.offset - draggedInfo.offset
                                    dragAccumulatedY -= distance
                                    viewModel.reorderPlaylistInMemory(fromIdx, toIdx)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    }
                }
                val onDragEndAction = remember {
                    {
                        viewModel.commitPlaylistReorder()
                        draggedRjid = null
                        dragAccumulatedY = 0f
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                ) {
                    items(
                        items = filteredPlaylist,
                        key = { it.rjid },
                        contentType = { "playlist_item" }
                    ) { item ->
                        val isCurrentTrack = (item.rjid == currentRjId)
                        val isDragged = (draggedRjid == item.rjid)

                        val itemModifier = if (isDragged) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier
                                .animateItem()
                                .fillMaxWidth()
                        }

                        PlaylistItemCard(
                            modifier = itemModifier,
                            item = item,
                            isCurrentTrack = isCurrentTrack,
                            isPlaying = (isCurrentTrack && isPlaying),
                            isDragged = isDragged,
                            dragOffsetY = if (isDragged) dragAccumulatedY else 0f,
                            onDragStart = onDragStartAction,
                            onDrag = onDragAction,
                            onDragEnd = onDragEndAction,
                            onClick = onItemClickAction,
                            onMoveFolder = { onMoveFolderClick(item) },
                            onCopyRj = { onCopyRjClick(item) },
                            onAudioInfo = { onAudioInfoClick(item) },
                            onDelete = { onDeleteClick(item) }
                        )
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
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoveFolder: () -> Unit,
    onCopyRj: () -> Unit,
    onAudioInfo: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isMenuExpanded by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(10.dp)
    val detectedFormat = remember(item.localFilePath) { detectAudioFormat(item.localFilePath) }

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

                // Top-Left Badge: RJ Code
                val isLocal = item.sourceType != "DL_SITE" || item.rjid.startsWith("LOCAL_")
                val displayTag = when {
                    isLocal && !item.linkedRjId.isNullOrBlank() -> item.linkedRjId!!
                    isLocal -> "IMPORT"
                    else -> item.rjid
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = displayTag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Top-Right Badge: Audio Format
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = detectedFormat.split(" ").firstOrNull() ?: "AUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Bottom-Right Badge: File Size
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = item.fileSize,
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
                    .padding(8.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "CV: ${item.cv}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

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
                                    Icon(Icons.Default.Info, contentDescription = null)
                                }
                            )

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
                            Icon(Icons.Default.Info, contentDescription = null)
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
