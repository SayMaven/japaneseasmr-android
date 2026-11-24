package com.saymaven.downloader.japaneseasmr.ui.screens.playlist.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.service.TagLocalizer
import com.saymaven.downloader.japaneseasmr.util.AgeRatingHelper
import java.io.File

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
    val cardShape = RoundedCornerShape(14.dp)
    val hasSubtitle = remember(item.localFilePath, item.chaptersJson) {
        (!item.chaptersJson.isNullOrBlank() && (item.chaptersJson.contains(".vtt") || item.chaptersJson.contains(".lrc") || item.chaptersJson.contains(".srt"))) ||
        try {
            val f = File(item.localFilePath)
            if (f.isDirectory) {
                f.walkTopDown().maxDepth(3).any { it.isFile && it.extension.lowercase() in setOf("vtt", "lrc", "srt") }
            } else if (f.isFile) {
                val subExts = setOf("vtt", "lrc", "srt")
                subExts.any { ext -> File(f.parentFile, "${f.nameWithoutExtension}.$ext").exists() }
            } else {
                false
            }
        } catch (_: Exception) { false }
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentTrack) 2.dp else 1.dp),
        border = BorderStroke(
            width = if (isCurrentTrack) 1.25.dp else 0.75.dp,
            color = if (isCurrentTrack) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTrack) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
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
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )

                if (showCardCircle && item.circle.isNotBlank() && item.circle != "-") {
                    Text(
                        text = item.circle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

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
                            LocalWorkGenreChip(text = TagLocalizer.localize(null, rawTag))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

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
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_action_add_to_playlist)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onAddToPlaylist()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_folder_move_title)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onMoveFolder()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_copy_rj)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onCopyRj()
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_audio_info)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onAudioInfo()
                                },
                                leadingIcon = { Icon(Icons.Default.AudioFile, contentDescription = null) }
                            )

                            if (onLinkRj != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_menu_link_rj)) },
                                    onClick = {
                                        isMenuExpanded = false
                                        onLinkRj()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_play_now)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Replay, contentDescription = null) }
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
    val cardShape = RoundedCornerShape(14.dp)
    val hasSubtitle = remember(item.localFilePath, item.chaptersJson) {
        (!item.chaptersJson.isNullOrBlank() && (item.chaptersJson.contains(".vtt") || item.chaptersJson.contains(".lrc") || item.chaptersJson.contains(".srt"))) ||
        try {
            val f = File(item.localFilePath)
            if (f.isDirectory) {
                f.walkTopDown().maxDepth(3).any { it.isFile && it.extension.lowercase() in setOf("vtt", "lrc", "srt") }
            } else if (f.isFile) {
                val subExts = setOf("vtt", "lrc", "srt")
                subExts.any { ext -> File(f.parentFile, "${f.nameWithoutExtension}.$ext").exists() }
            } else {
                false
            }
        } catch (_: Exception) { false }
    }

    Card(
        onClick = { onClick(item) },
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentTrack) 2.dp else 1.dp),
        border = BorderStroke(
            width = if (isCurrentTrack) 1.25.dp else 0.75.dp,
            color = if (isCurrentTrack) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTrack) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
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

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

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
                            LocalWorkGenreChip(text = TagLocalizer.localize(null, rawTag))
                        }
                    }
                }
            }

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
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_action_add_to_playlist)) },
                            onClick = {
                                isMenuExpanded = false
                                onAddToPlaylist()
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_action_move_folder)) },
                            onClick = {
                                isMenuExpanded = false
                                onMoveFolder()
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) }
                        )

                        val rjToCopy = item.linkedRjId ?: item.rjid
                        if (rjToCopy.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_copy_rj, rjToCopy)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onCopyRj()
                                },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_menu_audio_info)) },
                            onClick = {
                                isMenuExpanded = false
                                onAudioInfo()
                            },
                            leadingIcon = { Icon(Icons.Default.AudioFile, contentDescription = null) }
                        )

                        if (onLinkRj != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_menu_link_rj)) },
                                onClick = {
                                    isMenuExpanded = false
                                    onLinkRj()
                                },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
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

@Composable
private fun LocalWorkGenreChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
