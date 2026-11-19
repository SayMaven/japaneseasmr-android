package com.saymaven.downloader.japaneseasmr.ui.screens.browse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionItem
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionsSheet
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

    val selectedWork by viewModel.selectedWork.collectAsState()
    val workTracks by viewModel.workTracks.collectAsState()
    val fileTree by viewModel.fileTree.collectAsState()
    val isLoadingTracks by viewModel.isLoadingTracks.collectAsState()

    val isTranslationActive by viewModel.isTranslationActive.collectAsState()
    val translatedTitles by viewModel.translatedTitles.collectAsState()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var isListView by remember { mutableStateOf(false) }

    // If a work is selected, show KikoFlu-style full Work Detail screen
    if (selectedWork != null) {
        val work = selectedWork!!
        BackHandler {
            viewModel.selectWork(null)
        }
        AsmrOneWorkDetailScreen(
            work = work,
            fileTree = fileTree,
            audioTracks = workTracks,
            isLoadingFiles = isLoadingTracks,
            playerViewModel = playerViewModel,
            onBack = { viewModel.selectWork(null) },
            onOpenPlayer = onOpenPlayer,
            onDownload = {
                viewModel.enqueueDownload(work)
                Toast.makeText(
                    context,
                    context.getString(R.string.browse_toast_enqueued, work.rjid),
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = modifier
        )
        return
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

                // View Toggle Button (Grid / List)
                IconButton(
                    onClick = { isListView = !isListView },
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
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotEmpty()) {
                                viewModel.onSearchQueryChange("")
                            } else {
                                isSearchExpanded = false
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Main Content Area with Grid or List and Pagination
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
}

/**
 * Pinterest-style work card benchmarked from KikoFlu's EnhancedWorkCard.
 */
@Composable
fun AsmrOneWorkCard(
    work: AsmrOneWork,
    displayTitle: String,
    onCopyRj: () -> Unit,
    onDownloadClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(10.dp)

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

                // Top-Left Badge: RJ Code
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = work.rjid,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Top-Right Badge: Age Rating (R18 / ALL)
                val ageBgColor = if (work.ageRating.contains("18")) {
                    Color(0xFFD32F2F).copy(alpha = 0.88f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = ageBgColor,
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = work.ageRating,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Bottom-Left Badge: Subtitle
                if (work.hasSubtitle) {
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
                if (!work.releaseDate.isNullOrBlank()) {
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
                if (work.circle.isNotBlank() && work.circle != "-") {
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

                // Action Bar: Copy RJ & Direct Download
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onCopyRj,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.browse_action_copy_rj),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RJ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

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
                }
            }
        }
    }
}

/**
 * KikoFlu-style Horizontal Work List Item matching Screenshot 5.
 */
@Composable
fun AsmrOneWorkListItem(
    work: AsmrOneWork,
    displayTitle: String,
    onCopyRj: () -> Unit,
    onDownloadClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(12.dp)

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
            // Square Cover image on left with RJ tag overlay
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

                if (work.circle.isNotBlank() && work.circle != "-") {
                    Text(
                        text = work.circle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!work.releaseDate.isNullOrBlank()) {
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

            // Quick Download & Copy RJ buttons on right
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

                IconButton(
                    onClick = onCopyRj,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.browse_action_copy_rj),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
