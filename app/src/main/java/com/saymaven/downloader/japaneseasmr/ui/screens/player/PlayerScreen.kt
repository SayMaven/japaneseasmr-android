package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
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
    var showPlaylistSheet by remember { mutableStateOf(false) }

    val effectivePosition = if (isSliderDragging) dragPosition else currentPosition

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

            // Big Center Cover Art
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .size(260.dp)
                    .aspectRatio(1f)
            ) {
                if (currentCoverUrl != null) {
                    AsyncImage(
                        model = currentCoverUrl,
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
                onClick = { showPlaylistSheet = true },
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

    // ================= 3. PLAYLIST BOTTOM SHEET WITH RELIABLE REORDERING =================
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BERIKUTNYA • DAFTAR PUTAR (${playlist.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = { showPlaylistSheet = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (playlist.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada audio yang diunduh.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = playlist,
                            key = { _, item -> item.rjid }
                        ) { index, item ->
                            val isCurrent = item.rjid == currentRjId
                            PlaylistTrackCard(
                                item = item,
                                isCurrent = isCurrent,
                                isFirst = index == 0,
                                isLast = index == playlist.size - 1,
                                onClick = {
                                    viewModel.playLocalTrack(item, playlist)
                                },
                                onMoveUp = {
                                    if (index > 0) {
                                        viewModel.reorderPlaylist(index, index - 1)
                                    }
                                },
                                onMoveDown = {
                                    if (index < playlist.size - 1) {
                                        viewModel.reorderPlaylist(index, index + 1)
                                    }
                                },
                                onReorderSteps = { steps ->
                                    val target = (index + steps).coerceIn(0, playlist.size - 1)
                                    if (target != index) {
                                        viewModel.reorderPlaylist(index, target)
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
fun PlaylistTrackCard(
    item: HistoryEntity,
    isCurrent: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onReorderSteps: (Int) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetAccumulator by remember { mutableFloatStateOf(0f) }
    val stepThresholdPx = 100f

    val scale by animateFloatAsState(targetValue = if (isDragging) 1.03f else 1.0f, label = "cardScale")

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = if (isDragging) 8.dp else 0.dp,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "[${item.rjid}] ${item.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "CV: ${item.cv}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrent) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Sedang Diputar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 4.dp)
                )
            }

            // Up / Down Quick Reordering Buttons
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Pindah Ke Atas",
                        tint = if (!isFirst) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Pindah Ke Bawah",
                        tint = if (!isLast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Hold & Drag Handle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(item.rjid) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isDragging = true
                                dragOffsetAccumulator = 0f
                            },
                            onDragEnd = {
                                isDragging = false
                                dragOffsetAccumulator = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffsetAccumulator = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetAccumulator += dragAmount.y
                                val steps = (dragOffsetAccumulator / stepThresholdPx).toInt()
                                if (steps != 0) {
                                    onReorderSteps(steps)
                                    dragOffsetAccumulator -= (steps * stepThresholdPx)
                                }
                            }
                        )
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Tahan & Geser",
                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
