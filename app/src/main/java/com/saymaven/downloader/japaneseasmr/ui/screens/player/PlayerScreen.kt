package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentRjId by viewModel.currentRjId.collectAsState()
    val currentTitle by viewModel.currentTitle.collectAsState()
    val currentArtist by viewModel.currentArtist.collectAsState()
    val currentCoverUrl by viewModel.currentCoverUrl.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleMode by viewModel.shuffleMode.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val fileExistenceMap by viewModel.fileExistenceMap.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val dacState by viewModel.dacState.collectAsState()
    val audioSpecs by viewModel.audioSpecs.collectAsState()

    val activity = context as? Activity
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var isSliderDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val effectivePosition = if (isSliderDragging) dragPosition else currentPosition

    // Standard High-Performance Drag-to-Reorder States
    val listState = rememberLazyListState()
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val slotHeightPx = remember(density) { with(density) { 74.dp.toPx() } }

    // Controlled Gentle Auto-Scroll only when held at the very edge (<42dp from boundary)
    LaunchedEffect(draggedIndex) {
        while (isActive && draggedIndex != null) {
            val curIdx = draggedIndex ?: break
            val visibleInfo = listState.layoutInfo.visibleItemsInfo
            val itemInfo = visibleInfo.firstOrNull { it.index == curIdx }

            if (itemInfo != null) {
                val itemTop = itemInfo.offset + dragAccumulatedY
                val itemBottom = itemTop + itemInfo.size
                val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                val edgeZone = with(density) { 44.dp.toPx() }

                if (itemTop < edgeZone && listState.canScrollBackward) {
                    listState.scrollBy(-12f)
                    if (curIdx > 0 && dragAccumulatedY < -slotHeightPx * 0.4f) {
                        viewModel.reorderPlaylistInMemory(curIdx, curIdx - 1)
                        draggedIndex = curIdx - 1
                        dragAccumulatedY += slotHeightPx
                    }
                } else if (itemBottom > viewportHeight - edgeZone && listState.canScrollForward) {
                    listState.scrollBy(12f)
                    if (curIdx < playlist.size - 1 && dragAccumulatedY > slotHeightPx * 0.4f) {
                        viewModel.reorderPlaylistInMemory(curIdx, curIdx + 1)
                        draggedIndex = curIdx + 1
                        dragAccumulatedY -= slotHeightPx
                    }
                }
            }
            delay(16)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ================= 1. HEADER (TITLE & CV) & COVER ART =================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Metadata Title (Top)
            Text(
                text = currentTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // CV Text (Below Title)
            Text(
                text = "CV: $currentArtist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Big Center Cover Art (0ms Instant Image with RAM cache preloaded)
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .size(260.dp)
                    .aspectRatio(1f)
            ) {
                if (!currentCoverUrl.isNullOrBlank()) {
                    val imageRequest = remember(currentCoverUrl) {
                        ImageRequest.Builder(context)
                            .data(currentCoverUrl)
                            .memoryCacheKey(currentCoverUrl)
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Cover Art",
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            }
        }

        // ================= 2. TIMELINE, DAC/SPECS & CONTROLS =================
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timeline Slider
            Slider(
                value = if (duration > 0) (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                onValueChange = { frac ->
                    isSliderDragging = true
                    dragPosition = (frac * duration).toLong()
                },
                onValueChangeFinished = {
                    viewModel.seekTo(dragPosition)
                    isSliderDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Timeline Labels (Elapsed & Duration/Remaining)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(effectivePosition),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (showRemainingTime) "-${formatDuration((duration - effectivePosition).coerceAtLeast(0L))}" else formatDuration(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                )
            }

            // DAC Status & Audio Specs (Below Timeline, Centered)
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (dacState.isExclusiveActive) {
                    Text(
                        text = "DAC Eksklusif: ${dacState.dacName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = audioSpecs,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Control Row 1: Repeat (Left) & Shuffle (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeat Mode Button
                IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                // Shuffle Mode Button
                IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Control Row 2: Prev, Replay10, Play/Pause, Forward10, Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Track
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Sebelumnya",
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Replay 10s
                IconButton(
                    onClick = { viewModel.replay10() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Mundur 10 Detik",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Play / Pause Main Button (Big Circle)
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Forward 10s
                IconButton(
                    onClick = { viewModel.forward10() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Maju 10 Detik",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Next Track
                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Berikutnya",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Playlist Drawer Opener Button (Exact Screenshot 2 style)
            Surface(
                onClick = { showBottomSheet = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "BERIKUTNYA • DAFTAR PUTAR (${playlist.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Buka Daftar Putar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ================= 3. PLAYLIST BOTTOM SHEET =================
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Putar Koleksi (${playlist.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (playlist.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada audio di koleksi. Unduh kode RJ terlebih dahulu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 52.dp)
                    ) {
                        itemsIndexed(playlist, key = { _, item -> item.rjid }) { index, item ->
                            val fileExists = fileExistenceMap[item.rjid] ?: true
                            val isCurrentTrack = item.rjid == currentRjId
                            val isDragged = draggedIndex == index

                            PlaylistItemCard(
                                modifier = Modifier.animateItemPlacement(
                                    animationSpec = spring(stiffness = 800f)
                                ),
                                item = item,
                                isCurrentTrack = isCurrentTrack,
                                fileExists = fileExists,
                                isDragged = isDragged,
                                dragOffsetY = if (isDragged) dragAccumulatedY else 0f,
                                onDragHandleGesture = {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            draggedIndex = index
                                            dragAccumulatedY = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            dragAccumulatedY += dragAmount

                                            val curIdx = draggedIndex ?: return@detectVerticalDragGestures
                                            val threshold = slotHeightPx * 0.48f

                                            if (dragAccumulatedY > threshold && curIdx < playlist.size - 1) {
                                                viewModel.reorderPlaylistInMemory(curIdx, curIdx + 1)
                                                draggedIndex = curIdx + 1
                                                dragAccumulatedY -= slotHeightPx
                                            } else if (dragAccumulatedY < -threshold && curIdx > 0) {
                                                viewModel.reorderPlaylistInMemory(curIdx, curIdx - 1)
                                                draggedIndex = curIdx - 1
                                                dragAccumulatedY += slotHeightPx
                                            }
                                        },
                                        onDragEnd = {
                                            viewModel.commitPlaylistReorder()
                                            draggedIndex = null
                                            dragAccumulatedY = 0f
                                        },
                                        onDragCancel = {
                                            viewModel.commitPlaylistReorder()
                                            draggedIndex = null
                                            dragAccumulatedY = 0f
                                        }
                                    )
                                },
                                onClick = {
                                    if (fileExists) {
                                        viewModel.playLocalTrack(item, playlist)
                                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                                            showBottomSheet = false
                                        }
                                    } else {
                                        Toast.makeText(context, "File [${item.rjid}] tidak ditemukan di memori HP.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistItemCard(
    modifier: Modifier = Modifier,
    item: HistoryEntity,
    isCurrentTrack: Boolean,
    fileExists: Boolean,
    isDragged: Boolean,
    dragOffsetY: Float,
    onDragHandleGesture: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 100f else 1f)
            .graphicsLayer {
                translationY = if (isDragged) dragOffsetY else 0f
                val s = if (isDragged) 1.03f else 1.0f
                scaleX = s
                scaleY = s
                shadowElevation = if (isDragged) 30f else 0f
                shape = RoundedCornerShape(12.dp)
                clip = false
            }
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragged -> MaterialTheme.colorScheme.secondaryContainer
                !fileExists -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                isCurrentTrack -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageReq = remember(item.coverUrl) {
                ImageRequest.Builder(context)
                    .data(item.coverUrl)
                    .memoryCacheKey(item.coverUrl)
                    .crossfade(false)
                    .build()
            }

            AsyncImage(
                model = imageReq,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp, 40.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "[${item.rjid}] ${item.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        !fileExists -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isCurrentTrack -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = if (fileExists) "CV: ${item.cv} \u2022 ${item.fileSize}" else "File belum diunduh / terhapus",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fileExists) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!fileExists) {
                Icon(
                    Icons.Default.FileDownloadOff,
                    contentDescription = "File Hilang",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            } else if (isCurrentTrack) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Area Ikon Drag Handle Hamburger
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .pointerInput(item.rjid, onDragHandleGesture),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Atur Urutan",
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

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
