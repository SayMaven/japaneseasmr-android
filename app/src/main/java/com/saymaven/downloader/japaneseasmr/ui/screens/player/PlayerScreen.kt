package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
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

    var showRemainingTime by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }

    val displayPosition = if (isDraggingSlider) dragPosition else currentPosition

    // Bottom Sheet state for YouTube Music style playlist drawer
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    // Multi-track continuous fluid drag-and-drop state
    var draggedRjid by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val itemHeightPx = with(density) { 68.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Work Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (currentRjId != null) currentTitle else "JapaneseASMR Player",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (currentRjId != null) "CV: $currentArtist" else "Pemutar Audio Asli",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // High-Res Artwork Cover (Square 260dp)
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(20.dp))
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
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Placeholder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }

            // Timeline & Durasi (Clickable Toggle Remaining vs Total Time)
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) (displayPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                    onValueChange = { frac ->
                        isDraggingSlider = true
                        dragPosition = (frac * duration).toLong()
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(dragPosition)
                        isDraggingSlider = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(displayPosition),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = if (showRemainingTime && duration > 0) {
                            val remaining = (duration - displayPosition).coerceAtLeast(0L)
                            "-${formatDuration(remaining)}"
                        } else {
                            formatDuration(duration)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                    )
                }
            }

            // Kontrol Pemutar Terpusat (Upper Utility Row: Repeat & Shuffle)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Repeat 3-Mode (Off -> Repeat All -> Repeat One)
                    IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Mode Ulangi",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Tombol Shuffle (On / Off)
                    IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Mode Acak",
                            tint = if (shuffleMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Baris Kontrol Utama: [Prev] • [Mundur 10s] • [Play/Pause Besar] • [Maju 10s] • [Next]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.playPrevious() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Audio Sebelumnya",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.seekTo(currentPosition - 10000L) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Mundur 10 Detik",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Jeda" else "Putar",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.seekTo(currentPosition + 10000L) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Maju 10 Detik",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Audio Selanjutnya",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // YouTube Music Style Bottom Bar Trigger ("BERIKUTNYA • DAFTAR PUTAR")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showBottomSheet = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BERIKUTNYA • DAFTAR PUTAR (${playlist.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Buka Laci",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // YouTube Music Style Modal Bottom Sheet Playlist Drawer
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.80f)
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
                        Text(
                            text = "Geser Ikon ≡ Bebas ke Atas/Bawah",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider()

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
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            itemsIndexed(playlist, key = { _, item -> item.rjid }) { index, item ->
                                val fileExists = remember(item.localFilePath) { File(item.localFilePath).exists() }
                                val isCurrentTrack = item.rjid == currentRjId
                                val isDragged = draggedRjid == item.rjid

                                PlaylistItemCard(
                                    item = item,
                                    isCurrentTrack = isCurrentTrack,
                                    fileExists = fileExists,
                                    isDragged = isDragged,
                                    dragOffsetY = if (isDragged) dragOffsetY else 0f,
                                    onDragHandleGesture = {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                draggedRjid = item.rjid
                                                dragOffsetY = 0f
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount

                                                val currentIdx = playlist.indexOfFirst { it.rjid == draggedRjid }
                                                if (currentIdx != -1) {
                                                    // Threshold 45% of item height for fluid continuous swapping
                                                    val threshold = itemHeightPx * 0.45f
                                                    if (dragOffsetY > threshold && currentIdx < playlist.size - 1) {
                                                        viewModel.reorderPlaylist(currentIdx, currentIdx + 1)
                                                        dragOffsetY -= itemHeightPx
                                                    } else if (dragOffsetY < -threshold && currentIdx > 0) {
                                                        viewModel.reorderPlaylist(currentIdx, currentIdx - 1)
                                                        dragOffsetY += itemHeightPx
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggedRjid = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedRjid = null
                                                dragOffsetY = 0f
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
}

@Composable
fun PlaylistItemCard(
    item: HistoryEntity,
    isCurrentTrack: Boolean,
    fileExists: Boolean,
    isDragged: Boolean,
    dragOffsetY: Float,
    onDragHandleGesture: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isDragged) 1.04f else 1.0f, label = "scale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 20f else 1f)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .scale(scale)
            .shadow(
                elevation = if (isDragged) 16.dp else 0.dp,
                shape = RoundedCornerShape(12.dp)
            )
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
            AsyncImage(
                model = item.coverUrl,
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
                    text = if (fileExists) "CV: ${item.cv} • ${item.fileSize}" else "File belum diunduh / terhapus",
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

            // Area Ikon Drag Handle (Geser terus-menerus ke atas/bawah tanpa jeda)
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
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
