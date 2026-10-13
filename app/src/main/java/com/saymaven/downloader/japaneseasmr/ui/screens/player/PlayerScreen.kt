package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import androidx.compose.foundation.BorderStroke
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

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
    val showRemainingTime by viewModel.showRemainingTime.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val availableFolders by viewModel.availableFolders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val filteredPlaylist by viewModel.filteredPlaylist.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val dacState by viewModel.dacState.collectAsState()
    val hardwareVolume by viewModel.hardwareVolume.collectAsState()
    val audioSpecs by viewModel.audioSpecs.collectAsState()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val chapters by viewModel.currentChapters.collectAsState()
    val isLoadingChapters by viewModel.isLoadingChapters.collectAsState()
    val activeChapterIndex by viewModel.activeChapterIndex.collectAsState()
    val activeChapter by viewModel.activeChapter.collectAsState()
    val isTranslationActive by viewModel.isTranslationActive.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val translatedTitle by viewModel.translatedTitle.collectAsState()
    val translatedChapters by viewModel.translatedChapters.collectAsState()

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
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showChapterSheet by remember { mutableStateOf(false) }
    val chapterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var itemToMove by remember { mutableStateOf<HistoryEntity?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }

    // NestedScrollConnection to prevent upward fast-fling from accidentally closing ModalBottomSheet
    val sheetNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Consume remaining vertical velocity to prevent bottom sheet dismiss
                return available
            }
        }
    }

    var showSleepTimerSheet by remember { mutableStateOf(false) }
    val sleepTimerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftTimerMinutes by remember { mutableIntStateOf(15) }

    val effectivePosition = if (isSliderDragging) dragPosition else currentPosition

    // Standard High-Performance Drag-to-Reorder States using unique draggedRjid
    val listState = rememberLazyListState()
    var draggedRjid by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val slotHeightPx = remember(density) { with(density) { 74.dp.toPx() } }

    // Controlled Gentle Auto-Scroll only when held at the very edge (<44dp from boundary)
    LaunchedEffect(draggedRjid) {
        while (isActive && draggedRjid != null) {
            val targetRjid = draggedRjid ?: break
            val curIdx = playlist.indexOfFirst { it.rjid == targetRjid }
            if (curIdx == -1) break

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
                        dragAccumulatedY += slotHeightPx
                    }
                } else if (itemBottom > viewportHeight - edgeZone && listState.canScrollForward) {
                    listState.scrollBy(12f)
                    if (curIdx < playlist.size - 1 && dragAccumulatedY > slotHeightPx * 0.4f) {
                        viewModel.reorderPlaylistInMemory(curIdx, curIdx + 1)
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
            Spacer(modifier = Modifier.height(4.dp))

            // RJID Pill Badge
            if (!currentRjId.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = currentRjId ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Clean Title (Clean from [RJxxxx] prefix for cleaner presentation)
            val cleanOriginalTitle = currentTitle.replace(Regex("^\\[(?:RJ|VJ|BJ)?\\d+\\]\\s*"), "").trim()
            val cleanTranslatedTitle = translatedTitle?.replace(Regex("^\\[(?:RJ|VJ|BJ)?\\d+\\]\\s*"), "")?.trim()

            val displayTitle = if (isTranslationActive && !cleanTranslatedTitle.isNullOrBlank()) {
                cleanTranslatedTitle
            } else cleanOriginalTitle

            Text(
                text = displayTitle.ifBlank { stringResource(R.string.player_no_track) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            // Original Title Subtext (when translation is active and differs)
            if (isTranslationActive && !cleanTranslatedTitle.isNullOrBlank() && cleanOriginalTitle.isNotBlank() && cleanTranslatedTitle != cleanOriginalTitle) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = cleanOriginalTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

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

            if (chapters.isNotEmpty() || isLoadingChapters) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = { showChapterSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (isLoadingChapters) {
                            Text(
                                text = stringResource(R.string.player_scanning_chapters),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            val activeChapTitle = if (isTranslationActive && activeChapterIndex in translatedChapters.indices) {
                                translatedChapters[activeChapterIndex].title
                            } else activeChapter?.title ?: stringResource(R.string.player_chapter_detected, chapters.size)

                            Text(
                                text = activeChapTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 220.dp)
                            )
                            if (activeChapter != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeChapter!!.formatTime(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Big Center Cover Art (Adaptive responsive sizing)
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(1f)
                    .heightIn(max = 260.dp)
            ) {
                val cover = currentCoverUrl
                if (!cover.isNullOrBlank()) {
                    val imageRequest = remember(cover) {
                        ImageRequest.Builder(context)
                            .data(cover)
                            .memoryCacheKey(cover)
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
                    modifier = Modifier.clickable { viewModel.toggleShowRemainingTime() }
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
                        text = stringResource(R.string.player_dac_label_format, dacState.dacName),
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

            // Control Row 1: Repeat, Sleep Timer, Bab (CUE), Shuffle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
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
                        contentDescription = stringResource(R.string.player_action_repeat),
                        tint = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                // Sleep Timer Button
                Surface(
                    onClick = { showSleepTimerSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = if (sleepTimerRemainingMs != null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = stringResource(R.string.player_timer_dialog_title),
                            tint = if (sleepTimerRemainingMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                        if (sleepTimerRemainingMs != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val totalSecs = (sleepTimerRemainingMs!! / 1000).coerceAtLeast(0)
                            val m = totalSecs / 60
                            val s = totalSecs % 60
                            Text(
                                text = String.format("%02d:%02d", m, s),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Chapter List Button
                Surface(
                    onClick = { showChapterSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = if (chapters.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = stringResource(R.string.player_action_chapters),
                            tint = if (chapters.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                        if (chapters.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${chapters.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Translate Button (Terjemahkan Judul & Bab)
                Surface(
                    onClick = { viewModel.toggleTranslation() },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isTranslationActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    border = if (isTranslationActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = stringResource(R.string.player_action_translate),
                                tint = if (isTranslationActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(20.dp)
                            )
                            if (isTranslationActive) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ID",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Shuffle Mode Button
                IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.player_action_shuffle),
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
                        contentDescription = stringResource(R.string.player_action_prev),
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
                        contentDescription = stringResource(R.string.player_action_replay10),
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
                        contentDescription = if (isPlaying) stringResource(R.string.player_action_pause) else stringResource(R.string.player_action_play),
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
                        contentDescription = stringResource(R.string.player_action_forward10),
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
                        contentDescription = stringResource(R.string.player_action_next),
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
                            text = stringResource(R.string.player_playlist_next_title, playlist.size),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.player_action_open_playlist),
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
                    .fillMaxHeight(0.80f)
                    .padding(horizontal = 16.dp)
                    .nestedScroll(sheetNestedScroll)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.player_playlist_sheet_header, filteredPlaylist.size),
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
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Subfolder Selector Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
                    val onDragStartAction = remember {
                        { rjid: String ->
                            val currentIdx = filteredPlaylist.indexOfFirst { it.rjid == rjid }
                            if (currentIdx != -1) {
                                draggedRjid = rjid
                                dragAccumulatedY = 0f
                            }
                        }
                    }
                    val onDragAction = remember(slotHeightPx) {
                        { dragAmount: Float ->
                            dragAccumulatedY += dragAmount

                            val targetRjid = draggedRjid ?: return@remember
                            val curIdx = filteredPlaylist.indexOfFirst { it.rjid == targetRjid }
                            if (curIdx == -1) return@remember

                            val threshold = slotHeightPx * 0.48f

                            if (dragAccumulatedY > threshold && curIdx < filteredPlaylist.size - 1) {
                                viewModel.reorderPlaylistInMemory(curIdx, curIdx + 1)
                                dragAccumulatedY -= slotHeightPx
                            } else if (dragAccumulatedY < -threshold && curIdx > 0) {
                                viewModel.reorderPlaylistInMemory(curIdx, curIdx - 1)
                                dragAccumulatedY += slotHeightPx
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
                    val onItemClickAction = remember {
                        { clickedItem: HistoryEntity ->
                            viewModel.playLocalTrack(clickedItem, filteredPlaylist)
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showBottomSheet = false
                            }
                            Unit
                        }
                    }
                    val onItemMoveAction = remember {
                        { clickedItem: HistoryEntity ->
                            itemToMove = clickedItem
                            showMoveDialog = true
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .nestedScroll(sheetNestedScroll),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 52.dp)
                    ) {
                        items(
                            items = filteredPlaylist,
                            key = { it.rjid },
                            contentType = { "playlist_item" }
                        ) { item ->
                            val isCurrentTrack = (item.rjid == currentRjId)
                            val isDragged = (draggedRjid == item.rjid)

                            PlaylistItemCard(
                                modifier = Modifier.fillMaxWidth(),
                                item = item,
                                isCurrentTrack = isCurrentTrack,
                                isPlaying = (isCurrentTrack && isPlaying),
                                isDragged = isDragged,
                                dragOffsetY = if (isDragged) dragAccumulatedY else 0f,
                                onDragStart = onDragStartAction,
                                onDrag = onDragAction,
                                onDragEnd = onDragEndAction,
                                onClick = onItemClickAction,
                                onMoveClick = onItemMoveAction
                            )
                        }
                    }
                }
            }
        }
    }

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
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                val targetFolderName = if (folder == "Utama") stringResource(R.string.player_folder_main) else folder
                                Text(targetFolderName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showSleepTimerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerSheet = false },
            sheetState = sleepTimerSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.player_timer_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (sleepTimerRemainingMs != null) {
                        TextButton(
                            onClick = {
                                viewModel.stopSleepTimer()
                                Toast.makeText(context, context.getString(R.string.player_timer_toast_off), Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(stringResource(R.string.player_timer_turn_off), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (sleepTimerRemainingMs != null) {
                        val totalSecs = (sleepTimerRemainingMs!! / 1000).coerceAtLeast(0)
                        val m = totalSecs / 60
                        val s = totalSecs % 60
                        stringResource(R.string.player_timer_active_desc, m, s)
                    } else {
                        stringResource(R.string.player_timer_inactive_desc)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Stepper + / - (Kelipatan 5 Menit)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                if (sleepTimerRemainingMs != null) {
                                    val newM = viewModel.adjustSleepTimerMinutes(-5)
                                    draftTimerMinutes = newM
                                } else {
                                    draftTimerMinutes = (draftTimerMinutes - 5).coerceAtLeast(5)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.player_timer_minus5))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val displayMinutes = if (sleepTimerRemainingMs != null) {
                                ((sleepTimerRemainingMs!! + 59999) / 60000).toInt()
                            } else {
                                draftTimerMinutes
                            }
                            Text(
                                text = stringResource(R.string.player_timer_minutes_format, displayMinutes),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.player_timer_step_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                if (sleepTimerRemainingMs != null) {
                                    val newM = viewModel.adjustSleepTimerMinutes(5)
                                    draftTimerMinutes = newM
                                } else {
                                    draftTimerMinutes = (draftTimerMinutes + 5).coerceAtMost(360)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.player_timer_plus5))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.player_timer_quick_choice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Presets Grid: 5m, 10m, 15m, 30m, 1 jam (60m), 2 jam (120m)
                val presets = listOf(
                    5 to stringResource(R.string.player_timer_5min),
                    10 to stringResource(R.string.player_timer_10min),
                    15 to stringResource(R.string.player_timer_15min),
                    30 to stringResource(R.string.player_timer_30min),
                    60 to stringResource(R.string.player_timer_1hour),
                    120 to stringResource(R.string.player_timer_2hours)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.take(3).forEach { (mins, label) ->
                        OutlinedButton(
                            onClick = {
                                draftTimerMinutes = mins
                                viewModel.startSleepTimer(mins)
                                showSleepTimerSheet = false
                                Toast.makeText(context, context.getString(R.string.player_timer_toast_set, label), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.drop(3).forEach { (mins, label) ->
                        OutlinedButton(
                            onClick = {
                                draftTimerMinutes = mins
                                viewModel.startSleepTimer(mins)
                                showSleepTimerSheet = false
                                Toast.makeText(context, context.getString(R.string.player_timer_toast_set, label), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Start / Update Timer Button
                Button(
                    onClick = {
                        viewModel.startSleepTimer(draftTimerMinutes)
                        showSleepTimerSheet = false
                        val minFormatted = context.getString(R.string.player_timer_minutes_format, draftTimerMinutes)
                        Toast.makeText(context, context.getString(R.string.player_timer_toast_set, minFormatted), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (sleepTimerRemainingMs != null) {
                            stringResource(R.string.player_timer_btn_update, draftTimerMinutes)
                        } else {
                            stringResource(R.string.player_timer_btn_start, draftTimerMinutes)
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showChapterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChapterSheet = false },
            sheetState = chapterSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.80f)
                    .padding(horizontal = 16.dp)
                    .nestedScroll(sheetNestedScroll)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.player_chapter_sheet_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (chapters.isNotEmpty()) stringResource(R.string.player_chapter_detected, chapters.size) else stringResource(R.string.player_chapter_sync),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Tombol Terjemahkan Bab (di sebelah kiri tombol refresh)
                    IconButton(
                        onClick = { viewModel.toggleTranslation() },
                        enabled = !isTranslating
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = stringResource(R.string.player_action_translate),
                                tint = if (isTranslationActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Refresh / Sync Online Button
                    IconButton(
                        onClick = {
                            viewModel.loadChaptersForCurrentTrack(forceOnline = true)
                            if (isTranslationActive) {
                                viewModel.loadOrFetchTranslations(forceOnline = true)
                            }
                            Toast.makeText(context, context.getString(R.string.player_chapter_resync_toast), Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isLoadingChapters && !isTranslating
                    ) {
                        if (isLoadingChapters) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            scope.launch { chapterSheetState.hide() }.invokeOnCompletion {
                                showChapterSheet = false
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (chapters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.player_chapter_none_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.player_chapter_none_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.loadChaptersForCurrentTrack(forceOnline = true)
                                },
                                enabled = !isLoadingChapters
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.player_chapter_search_online))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .nestedScroll(sheetNestedScroll),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
                    ) {
                        itemsIndexed(
                            items = chapters,
                            key = { idx, ch -> "${idx}_${ch.startMs}" },
                            contentType = { _, _ -> "chapter_item" }
                        ) { idx, chapter ->
                            val isActive = (idx == activeChapterIndex)
                            Surface(
                                onClick = {
                                    viewModel.seekToChapter(chapter)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Track Number Badge
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%02d", idx + 1),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Title & Time Range
                                    Column(modifier = Modifier.weight(1f)) {
                                        val translatedChapterTitle = if (isTranslationActive && idx in translatedChapters.indices) {
                                             translatedChapters[idx].title
                                        } else null

                                        Text(
                                            text = translatedChapterTitle ?: chapter.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (isTranslationActive && !translatedChapterTitle.isNullOrBlank() && translatedChapterTitle != chapter.title) {
                                            Text(
                                                text = chapter.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (chapter.endMs > chapter.startMs) {
                                            val durSec = (chapter.endMs - chapter.startMs) / 1000
                                            val m = durSec / 60
                                            val s = durSec % 60
                                            Text(
                                                text = stringResource(R.string.player_chapter_duration_format, String.format(Locale.US, "%02d:%02d", m, s)),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Timestamp Badge (e.g. 12:45)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    ) {
                                        Text(
                                            text = chapter.formatTime(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
    onMoveClick: (HistoryEntity) -> Unit
) {
    val context = LocalContext.current
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
                        .size(120, 90) // Exact thumbnail resolution
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageReq,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
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
                Text(
                    text = "[${item.rjid}] ${item.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "CV: ${item.cv} \u2022 ${item.fileSize}",
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

            // Tombol Pindah Folder
            IconButton(
                onClick = { onMoveClick(item) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = stringResource(R.string.player_action_move_folder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Dedicated Drag Handle Touch Target (pointerInput with rememberUpdatedState)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
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
        // Animasi bernapas sangat lembut, tenang, dan lambat (Slow ASMR Breathing Rhythm)
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


