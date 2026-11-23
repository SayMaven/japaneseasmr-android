package com.saymaven.downloader.japaneseasmr.ui.screens.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import com.saymaven.downloader.japaneseasmr.service.UsbDacInfo
import java.util.Locale

@Composable
fun PlayerControlsSection(
    currentRjId: String?,
    isPlaying: Boolean,
    effectivePosition: Long,
    duration: Long,
    showRemainingTime: Boolean,
    onToggleRemainingTime: () -> Unit,
    onSliderDrag: (Long) -> Unit,
    onSliderDragFinished: () -> Unit,
    chapters: List<ChapterItem>,
    isLoadingChapters: Boolean,
    activeChapterIndex: Int,
    activeChapter: ChapterItem?,
    translatedChapters: List<ChapterItem>,
    isTranslationActive: Boolean,
    isTranslating: Boolean,
    dacState: UsbDacInfo,
    audioSpecs: String,
    playerButtonOrder: String,
    repeatMode: Int,
    sleepTimerRemainingMs: Long?,
    isOnlineStreaming: Boolean,
    shuffleMode: Boolean,
    playbackSpeed: Float,
    onOpenChapterSheet: () -> Unit,
    onOpenSleepTimerSheet: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenAudioGainDialog: () -> Unit,
    onOpenPlaylistPickerSheet: () -> Unit,
    onOpenLinkRjDialog: () -> Unit,
    onOpenCoverPickerSheet: () -> Unit,
    onToggleTranslation: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffleMode: () -> Unit,
    onPlayPrevious: () -> Unit,
    onReplay10: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onForward10: () -> Unit,
    onPlayNext: () -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (chapters.isNotEmpty() || isLoadingChapters) {
            Surface(
                onClick = onOpenChapterSheet,
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
                            modifier = Modifier.widthIn(max = 240.dp)
                        )
                        if (activeChapter != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeChapter.formatTime(),
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
            Spacer(modifier = Modifier.height(6.dp))
        }

        Slider(
            value = if (duration > 0) (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = { frac ->
                onSliderDrag((frac * duration).toLong())
            },
            onValueChangeFinished = onSliderDragFinished,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

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
                modifier = Modifier.clickable { onToggleRemainingTime() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
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

            if (!currentRjId.isNullOrBlank()) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opsi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_menu_link_rj)) },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenLinkRjDialog()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_menu_change_cover)) },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenCoverPickerSheet()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_action_add_to_playlist)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenPlaylistPickerSheet()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_audio_gain)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenAudioGainDialog()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val orderedButtons = remember(playerButtonOrder) {
            val validKeys = listOf("repeat", "sleep_timer", "chapters", "translate", "shuffle", "speed", "volume", "bookmark")
            val parsed = playerButtonOrder.split(",")
                .map { it.trim() }
                .filter { it in validKeys }
                .distinct()
                .toMutableList()
            validKeys.forEach { k -> if (!parsed.contains(k)) parsed.add(k) }
            parsed.take(5)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            orderedButtons.forEach { btnKey ->
                when (btnKey) {
                    "repeat" -> {
                        IconButton(onClick = onCycleRepeatMode) {
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
                    }
                    "sleep_timer" -> {
                        Surface(
                            onClick = onOpenSleepTimerSheet,
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
                                    val totalSecs = (sleepTimerRemainingMs / 1000).coerceAtLeast(0)
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
                    }
                    "chapters" -> {
                        Surface(
                            onClick = onOpenChapterSheet,
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
                    }
                    "translate" -> {
                        Surface(
                            onClick = onToggleTranslation,
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
                    }
                    "shuffle" -> {
                        IconButton(
                            onClick = { if (!isOnlineStreaming) onToggleShuffleMode() },
                            enabled = !isOnlineStreaming
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = stringResource(R.string.player_action_shuffle),
                                tint = if (isOnlineStreaming) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                       else if (shuffleMode) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                    "speed" -> {
                        Surface(
                            onClick = onOpenSpeedDialog,
                            shape = RoundedCornerShape(20.dp),
                            color = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = stringResource(R.string.player_speed_dialog_title),
                                    tint = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (playbackSpeed != 1.0f) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${playbackSpeed}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    "volume" -> {
                        IconButton(onClick = onOpenAudioGainDialog) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(R.string.settings_audio_gain),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                    "bookmark" -> {
                        IconButton(onClick = onOpenPlaylistPickerSheet) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = stringResource(R.string.browse_action_add_to_playlist),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlayPrevious,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.player_action_prev),
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(
                onClick = onReplay10,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = stringResource(R.string.player_action_replay10),
                    modifier = Modifier.size(28.dp)
                )
            }

            FilledIconButton(
                onClick = onTogglePlayPause,
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

            IconButton(
                onClick = onForward10,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = stringResource(R.string.player_action_forward10),
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onPlayNext,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.player_action_next),
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(44.dp))
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
