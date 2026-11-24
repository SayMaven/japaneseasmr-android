package com.saymaven.downloader.japaneseasmr.ui.screens.browse.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.model.AsmrOneWork
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneFileNode
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneTrack
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel

internal fun findAutoExpandedFolders(
    tree: List<AsmrOneFileNode>,
    formatPriority: String = "mp3_se,mp3,flac,wav,opus,m4a,aac"
): Set<String> {
    val expanded = mutableSetOf<String>()

    data class FolderCandidate(val path: List<String>, val node: AsmrOneFileNode)
    val candidates = mutableListOf<FolderCandidate>()

    fun collect(nodes: List<AsmrOneFileNode>, currentPath: List<String>) {
        for (n in nodes) {
            if (n.type == "folder") {
                val newPath = currentPath + n.title
                candidates.add(FolderCandidate(newPath, n))
                collect(n.children, newPath)
            }
        }
    }
    collect(tree, emptyList())

    val priorities = formatPriority.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun folderScore(path: List<String>): Int {
        val full = path.joinToString("/").lowercase()
        var s = 0

        priorities.forEachIndexed { index, format ->
            val weight = (priorities.size - index) * 40
            when (format) {
                "mp3_se" -> {
                    if (full.contains("mp3") && (full.contains("seあり") || full.contains("se有り") || full.contains("se_ari") || full.contains("se ari") || full.contains("効果音あり"))) {
                        s += weight + 30
                    }
                }
                "mp3" -> if (full.contains("mp3")) s += weight
                "wav" -> if (full.contains("wav")) s += weight
                "flac" -> if (full.contains("flac")) s += weight
                "opus" -> if (full.contains("opus")) s += weight
                "m4a", "aac" -> if (full.contains("m4a") || full.contains("aac")) s += weight
            }
        }

        if (full.contains("本編") || full.contains("main")) s += 30
        if (full.contains("seなし") || full.contains("se無し") || full.contains("se_nashi") || full.contains("se nashi") || full.contains("効果音なし") || full.contains("効果音無") || full.contains("nose")) s -= 60
        if (full.contains("おまけ") || full.contains("特典") || full.contains("bonus") || full.contains("free talk") || full.contains("フリートーク")) s -= 40
        return s
    }

    val best = candidates.maxByOrNull { folderScore(it.path) }
    if (best != null && folderScore(best.path) > 0) {
        expanded.addAll(best.path)
    }

    return expanded
}

internal fun extractFolderAudioTracks(folderNode: AsmrOneFileNode): List<AsmrOneTrack> {
    return folderNode.children.filter { it.type == "audio" }.map {
        AsmrOneTrack(
            title = it.title,
            hash = it.hash,
            streamUrl = it.streamUrl,
            duration = it.duration,
            size = it.size,
            type = "audio"
        )
    }
}

@Composable
fun FileTreeNodeItem(
    node: AsmrOneFileNode,
    fallbackAudioTracks: List<AsmrOneTrack>,
    work: AsmrOneWork,
    playerViewModel: PlayerViewModel,
    autoExpandedFolders: Set<String>,
    translatedTitles: Map<String, String>,
    isTranslationActive: Boolean,
    onOpenPlayer: () -> Unit = {},
    depth: Int = 0,
    parentFolderTracks: List<AsmrOneTrack>? = null,
    onTranslateNodes: (List<String>) -> Unit = {}
) {
    val context = LocalContext.current
    val shouldExpandInitially = autoExpandedFolders.contains(node.title)
    var isExpanded by remember(node.title, shouldExpandInitially) { mutableStateOf(shouldExpandInitially) }

    LaunchedEffect(isExpanded, isTranslationActive) {
        if (isExpanded && isTranslationActive && node.type == "folder") {
            val missing = node.children.map { it.title }
                .filter { it.isNotBlank() && translatedTitles[it.trim()].isNullOrBlank() && translatedTitles[it].isNullOrBlank() }
                .distinct()
            if (missing.isNotEmpty()) {
                onTranslateNodes(missing)
            }
        }
    }

    val displayNodeTitle = if (isTranslationActive) {
        translatedTitles[node.title.trim()] ?: translatedTitles[node.title] ?: node.title
    } else {
        node.title
    }

    if (node.type == "folder") {
        val isolatedFolderTracks = remember(node) { extractFolderAudioTracks(node) }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(start = (depth * 14).dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayNodeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    node.children.forEach { child ->
                        FileTreeNodeItem(
                            node = child,
                            fallbackAudioTracks = fallbackAudioTracks,
                            work = work,
                            playerViewModel = playerViewModel,
                            autoExpandedFolders = autoExpandedFolders,
                            translatedTitles = translatedTitles,
                            isTranslationActive = isTranslationActive,
                            onOpenPlayer = onOpenPlayer,
                            depth = depth + 1,
                            parentFolderTracks = if (isolatedFolderTracks.isNotEmpty()) isolatedFolderTracks else parentFolderTracks,
                            onTranslateNodes = onTranslateNodes
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 14).dp, top = 3.dp, bottom = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (node.type) {
                    "audio" -> {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayNodeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val tracksToPlay = parentFolderTracks?.takeIf { it.isNotEmpty() }
                                    ?: fallbackAudioTracks.takeIf { it.isNotEmpty() }
                                    ?: listOf(
                                        AsmrOneTrack(
                                            title = node.title,
                                            hash = node.hash,
                                            streamUrl = node.streamUrl,
                                            duration = node.duration,
                                            size = node.size,
                                            type = "audio"
                                        )
                                    )
                                val idx = tracksToPlay.indexOfFirst { it.hash == node.hash || it.title == node.title }.coerceAtLeast(0)
                                playerViewModel.playOnlineWork(
                                    rjid = work.rjid,
                                    workTitle = work.title,
                                    cv = work.cv,
                                    coverUrl = work.coverUrl,
                                    tracks = tracksToPlay,
                                    startIndex = idx
                                )
                                onOpenPlayer()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.browse_stream_playing, node.title),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    "image" -> {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayNodeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { Toast.makeText(context, displayNodeTitle, Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "View",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    "text" -> {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayNodeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { Toast.makeText(context, displayNodeTitle, Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Read",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayNodeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioTrackRow(
    track: AsmrOneTrack,
    displayTitle: String = track.title,
    onPlay: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color(0xFF4CAF50)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
