package com.saymaven.downloader.japaneseasmr.ui.screens.browse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import com.saymaven.downloader.japaneseasmr.ui.components.PlaylistSelectionSheet
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
import com.saymaven.downloader.japaneseasmr.data.model.AsmrOneWork
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneFileNode
import com.saymaven.downloader.japaneseasmr.data.remote.AsmrOneTrack
import com.saymaven.downloader.japaneseasmr.service.TranslationService
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AsmrOneWorkDetailScreen(
    work: AsmrOneWork,
    fileTree: List<AsmrOneFileNode>,
    audioTracks: List<AsmrOneTrack>,
    isLoadingFiles: Boolean,
    playerViewModel: PlayerViewModel,
    showRating: Boolean = false,
    showPrice: Boolean = false,
    showDuration: Boolean = true,
    showSales: Boolean = false,
    showReleaseDate: Boolean = true,
    showTranslateBtn: Boolean = true,
    showSubtitle: Boolean = true,
    showAgeRating: Boolean = true,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit = {},
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isTitleTranslated by remember { mutableStateOf(false) }
    var translatedTitleText by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }

    // Playlist picker state
    var showPlaylistPickerSheet by remember { mutableStateOf(false) }

    // File tree translation state
    var isFileTreeTranslated by remember { mutableStateOf(false) }
    var translatedNodeTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isTranslatingFileTree by remember { mutableStateOf(false) }

    // Smart default expansion: Only MP3 folder with SE あり is auto-expanded
    val autoExpandedFolders = remember(fileTree) {
        findAutoExpandedFolders(fileTree)
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // RJ Code clickable to copy
                Surface(
                    onClick = {
                        val match = Regex("(?:RJ|VJ|BJ)\\d+", RegexOption.IGNORE_CASE).find(work.rjid)?.value
                        val rjToCopy = match ?: work.rjid
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("RJ Code", rjToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(
                            context,
                            context.getString(R.string.browse_toast_rj_copied, rjToCopy),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = work.rjid,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.browse_action_download),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Save to playlist button
                    IconButton(onClick = { showPlaylistPickerSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(R.string.browse_action_add_to_playlist),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Large Top Cover Banner with standard 4:3 Aspect Ratio (KikoFlu Benchmark)
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(work.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = work.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 2. Title with Instant Translate Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    val displayTitle = if (isTitleTranslated && !translatedTitleText.isNullOrBlank()) {
                        translatedTitleText!!
                    } else {
                        work.title
                    }

                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (!isTitleTranslated && translatedTitleText == null) {
                                coroutineScope.launch {
                                    isTranslating = true
                                    val targetLang = TranslationService.getActiveTargetLanguage(context)
                                    val translated = TranslationService.translateText(work.title, targetLang)
                                    if (translated.isNotBlank()) {
                                        translatedTitleText = translated
                                        isTitleTranslated = true
                                    }
                                    isTranslating = false
                                }
                            } else {
                                isTitleTranslated = !isTitleTranslated
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Translate Title",
                                tint = if (isTitleTranslated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Metadata Row (Rating, Price, Duration, Sold, Release date, Subtitle, Age Rating)
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showAgeRating) {
                        val isR18 = work.nsfw || work.ageRating.contains("18", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isR18) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (isR18) "R-18" else "ALL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isR18) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (showSubtitle && work.hasSubtitle) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.browse_filter_subtitled),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (showRating && work.rating != null && work.rating > 0.0) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f (%d)", work.rating, work.reviewCount ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showPrice && work.price != null && work.price > 0) {
                        Text(
                            text = "${work.price} JPY",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showDuration && audioTracks.isNotEmpty()) {
                        val totalSec = audioTracks.sumOf { it.duration }
                        if (totalSec > 0) {
                            val mins = totalSec / 60
                            Text(
                                text = "${mins} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (showSales && work.dlCount != null) {
                        Text(
                            text = stringResource(R.string.browse_detail_sold, work.dlCount.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showReleaseDate && !work.releaseDate.isNullOrBlank()) {
                        Text(
                            text = work.releaseDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 4. Section: Circle | Voice Actors
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.browse_detail_circle_cv),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (work.circle.isNotBlank() && work.circle != "-") {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = work.circle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (work.cv.isNotBlank() && work.cv != "-") {
                            val cvList = work.cv.split(',', '、', '/').map { it.trim() }.filter { it.isNotEmpty() }
                            cvList.forEach { cvName ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        text = cvName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Section: Tags (English Genres)
            if (work.tags.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.browse_detail_tags),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            work.tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // 6. Section: Resource Files (Berkas file) with larger, functional Trans button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.browse_detail_resource_files),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (showTranslateBtn) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isFileTreeTranslated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (!isFileTreeTranslated && translatedNodeTitles.isEmpty()) {
                                        coroutineScope.launch {
                                            isTranslatingFileTree = true
                                            val targetLang = TranslationService.getActiveTargetLanguage(context)
                                            val titlesToTranslate = mutableSetOf<String>()
                                            fun collectTitles(nodes: List<AsmrOneFileNode>) {
                                                for (node in nodes) {
                                                    titlesToTranslate.add(node.title)
                                                    collectTitles(node.children)
                                                }
                                            }
                                            collectTitles(fileTree)
                                            if (audioTracks.isNotEmpty()) {
                                                for (tr in audioTracks) {
                                                    titlesToTranslate.add(tr.title)
                                                }
                                            }

                                            val map = TranslationService.translateBatch(titlesToTranslate.toList(), targetLang)
                                            translatedNodeTitles = map
                                            isFileTreeTranslated = true
                                            isTranslatingFileTree = false
                                        }
                                    } else {
                                        isFileTreeTranslated = !isFileTreeTranslated
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isTranslatingFileTree) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isFileTreeTranslated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.browse_detail_trans),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFileTreeTranslated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (isLoadingFiles) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            } else if (fileTree.isEmpty() && audioTracks.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.browse_empty_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (fileTree.isNotEmpty()) {
                    items(fileTree) { node ->
                        FileTreeNodeItem(
                            node = node,
                            fallbackAudioTracks = audioTracks,
                            work = work,
                            playerViewModel = playerViewModel,
                            autoExpandedFolders = autoExpandedFolders,
                            translatedTitles = translatedNodeTitles,
                            isTranslationActive = isFileTreeTranslated,
                            onOpenPlayer = onOpenPlayer
                        )
                    }
                } else {
                    // Fallback to flat audio tracks list
                    items(audioTracks) { track ->
                        val trackIdx = audioTracks.indexOf(track)
                        AudioTrackRow(
                            track = track,
                            onPlay = {
                                playerViewModel.playOnlineWork(
                                    rjid = work.rjid,
                                    workTitle = work.title,
                                    cv = work.cv,
                                    coverUrl = work.coverUrl,
                                    tracks = audioTracks,
                                    startIndex = trackIdx
                                )
                                onOpenPlayer()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.browse_stream_playing, track.title),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }

        if (showPlaylistPickerSheet) {
            PlaylistSelectionSheet(
                workRjid = work.rjid,
                workTitle = work.title,
                coverUrl = work.coverUrl,
                circle = work.circle,
                cv = work.cv,
                playerViewModel = playerViewModel,
                onDismiss = { showPlaylistPickerSheet = false }
            )
        }
    }
}

/**
 * Finds paths of folders that should be auto-expanded by default.
 * Specifically prioritizes MP3 folder with SE あり (SE ari).
 * All other folders (WAV, FLAC, M4A, SE なし, etc.) remain collapsed.
 */
private fun findAutoExpandedFolders(tree: List<AsmrOneFileNode>): Set<String> {
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

    fun folderScore(path: List<String>): Int {
        val full = path.joinToString("/").lowercase()
        var s = 0
        if (full.contains("mp3")) s += 100
        if (full.contains("seあり") || full.contains("se有り") || full.contains("se_ari") || full.contains("se ari") || full.contains("効果音あり")) s += 80
        if (full.contains("本編")) s += 50
        if (full.contains("wav") || full.contains("flac")) s -= 50
        if (full.contains("seなし") || full.contains("se無し") || full.contains("se_nashi") || full.contains("se nashi") || full.contains("効果音なし") || full.contains("効果音無")) s -= 120
        return s
    }

    val best = candidates.maxByOrNull { folderScore(it.path) }
    if (best != null && folderScore(best.path) > 0) {
        // Expand the best matching folder and all its parent folders
        expanded.addAll(best.path)
    }

    return expanded
}

/**
 * Extracts audio tracks within the given folder node only, isolating its stream and bab count.
 */
private fun extractFolderAudioTracks(folderNode: AsmrOneFileNode): List<AsmrOneTrack> {
    val directAudios = folderNode.children.filter { it.type == "audio" }
    if (directAudios.isNotEmpty()) {
        return directAudios.map {
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

    val recursive = mutableListOf<AsmrOneTrack>()
    fun walk(n: AsmrOneFileNode) {
        if (n.type == "audio") {
            recursive.add(
                AsmrOneTrack(
                    title = n.title,
                    hash = n.hash,
                    streamUrl = n.streamUrl,
                    duration = n.duration,
                    size = n.size,
                    type = "audio"
                )
            )
        } else if (n.type == "folder") {
            n.children.forEach { walk(it) }
        }
    }
    walk(folderNode)
    return recursive
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
    parentFolderTracks: List<AsmrOneTrack>? = null
) {
    val context = LocalContext.current
    val shouldExpandInitially = autoExpandedFolders.contains(node.title)
    var isExpanded by remember(node.title, shouldExpandInitially) { mutableStateOf(shouldExpandInitially) }

    val displayNodeTitle = if (isTranslationActive) {
        translatedTitles[node.title] ?: node.title
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
                            parentFolderTracks = if (isolatedFolderTracks.isNotEmpty()) isolatedFolderTracks else parentFolderTracks
                        )
                    }
                }
            }
        }
    } else {
        // Individual file item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 14).dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (node.type) {
                "audio" -> {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayNodeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (node.duration > 0L) {
                            val min = node.duration / 60
                            val sec = node.duration % 60
                            Text(
                                text = String.format("%02d:%02d", min, sec),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val streamTracks = if (!parentFolderTracks.isNullOrEmpty()) {
                                parentFolderTracks
                            } else {
                                fallbackAudioTracks.ifEmpty {
                                    listOf(AsmrOneTrack(title = node.title, streamUrl = node.streamUrl))
                                }
                            }

                            val targetIndex = streamTracks.indexOfFirst {
                                it.streamUrl == node.streamUrl || it.title == node.title
                            }.coerceAtLeast(0)

                            playerViewModel.playOnlineWork(
                                rjid = work.rjid,
                                workTitle = work.title,
                                cv = work.cv,
                                coverUrl = work.coverUrl,
                                tracks = streamTracks,
                                startIndex = targetIndex
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
                        onClick = {
                            Toast.makeText(context, displayNodeTitle, Toast.LENGTH_SHORT).show()
                        },
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
                        onClick = {
                            Toast.makeText(context, displayNodeTitle, Toast.LENGTH_SHORT).show()
                        },
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

@Composable
fun AudioTrackRow(
    track: AsmrOneTrack,
    onPlay: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
