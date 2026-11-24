package com.saymaven.downloader.japaneseasmr.ui.screens.playlist.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saymaven.downloader.japaneseasmr.R

data class ExplorerFolderEntry(
    val key: Any,
    val title: String,
    val subtitle: String,
    val coverUrls: List<String>,
    val onClick: () -> Unit,
    val onRename: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null
)

@Composable
fun ExplorerFolderCollection(
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
    cornerRadius: Dp = 6.dp,
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
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
