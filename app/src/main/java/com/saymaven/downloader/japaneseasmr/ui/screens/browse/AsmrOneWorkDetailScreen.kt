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
import com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager
import com.saymaven.downloader.japaneseasmr.service.TranslationService
import com.saymaven.downloader.japaneseasmr.ui.screens.browse.components.*
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
    val prefs = remember { PreferencesManager(context) }
    val autoTranslateDetail by prefs.autoTranslateDetailFlow.collectAsState(initial = prefs.getInitialAutoTranslateDetail())
    val formatPriority by prefs.audioFormatPriorityFlow.collectAsState(initial = "mp3_se,mp3,flac,wav,opus,m4a,aac")

    var isTitleTranslated by remember { mutableStateOf(false) }
    var translatedTitleText by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }

    // Playlist picker state
    var showPlaylistPickerSheet by remember { mutableStateOf(false) }

    // File tree translation state
    var isFileTreeTranslated by remember { mutableStateOf(false) }
    var translatedNodeTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isTranslatingFileTree by remember { mutableStateOf(false) }

    // Smart default expansion based on user format priority
    val autoExpandedFolders = remember(fileTree, formatPriority) {
        findAutoExpandedFolders(fileTree, formatPriority)
    }

    LaunchedEffect(work.title, autoTranslateDetail) {
        if (autoTranslateDetail && !isTitleTranslated && translatedTitleText == null && work.title.isNotBlank()) {
            isTranslating = true
            try {
                val targetLang = TranslationService.getActiveTargetLanguage(context)
                val provider = TranslationService.getActiveProvider(context)
                val translated = TranslationService.translateText(work.title, targetLang, provider)
                if (translated.isNotBlank() && translated != work.title) {
                    translatedTitleText = translated
                    isTitleTranslated = true
                }
            } finally {
                isTranslating = false
            }
        }
    }

    LaunchedEffect(fileTree, autoTranslateDetail) {
        if (autoTranslateDetail && !isFileTreeTranslated && translatedNodeTitles.isEmpty() && fileTree.isNotEmpty()) {
            isTranslatingFileTree = true
            try {
                val targetLang = TranslationService.getActiveTargetLanguage(context)
                val provider = TranslationService.getActiveProvider(context)
                val titlesToTranslate = mutableSetOf<String>()
                fun collectVisibleTitles(nodes: List<AsmrOneFileNode>) {
                    for (n in nodes) {
                        if (n.title.isNotBlank()) titlesToTranslate.add(n.title)
                        if (n.type == "folder" && autoExpandedFolders.contains(n.title) && n.children.isNotEmpty()) {
                            collectVisibleTitles(n.children)
                        }
                    }
                }
                collectVisibleTitles(fileTree)
                if (audioTracks.isNotEmpty()) {
                    for (tr in audioTracks) {
                        if (tr.title.isNotBlank()) titlesToTranslate.add(tr.title)
                    }
                }
                val map = TranslationService.translateBatch(titlesToTranslate.toList(), targetLang, provider)
                if (map.entries.any { (k, v) -> !TranslationService.isPoisonedTranslation(k, v, targetLang) }) {
                    translatedNodeTitles = map
                    isFileTreeTranslated = true
                }
            } finally {
                isTranslatingFileTree = false
            }
        }
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
                            val targetLangNow = TranslationService.getActiveTargetLanguage(context)
                            val hasValidTitle = !TranslationService.isPoisonedTranslation(work.title, translatedTitleText, targetLangNow)
                            if (!isTitleTranslated && !hasValidTitle) {
                                coroutineScope.launch {
                                    isTranslating = true
                                    try {
                                        val provider = TranslationService.getActiveProvider(context)
                                        val translated = TranslationService.translateText(work.title, targetLangNow, provider)
                                        if (!TranslationService.isPoisonedTranslation(work.title, translated, targetLangNow)) {
                                            translatedTitleText = translated
                                            isTitleTranslated = true
                                        }
                                    } finally {
                                        isTranslating = false
                                    }
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
                        val normalizedRating = com.saymaven.downloader.japaneseasmr.util.AgeRatingHelper.normalize(
                            if (work.nsfw) "R18" else work.ageRating
                        )
                        if (normalizedRating != "-") {
                            val isR18 = normalizedRating == com.saymaven.downloader.japaneseasmr.util.AgeRatingHelper.RATING_R18
                            val isR15 = normalizedRating == com.saymaven.downloader.japaneseasmr.util.AgeRatingHelper.RATING_R15
                            val chipBg = when {
                                isR18 -> MaterialTheme.colorScheme.errorContainer
                                isR15 -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                            val chipTextColor = when {
                                isR18 -> MaterialTheme.colorScheme.onErrorContainer
                                isR15 -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = chipBg
                            ) {
                                Text(
                                    text = normalizedRating,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = chipTextColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
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
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    val targetLangNow = TranslationService.getActiveTargetLanguage(context)
                                    val hasValidTranslations = translatedNodeTitles.entries.any { (k, v) ->
                                        !TranslationService.isPoisonedTranslation(k, v, targetLangNow)
                                    }
                                    if (!isFileTreeTranslated && !hasValidTranslations) {
                                        coroutineScope.launch {
                                            isTranslatingFileTree = true
                                            try {
                                                val titlesToTranslate = mutableSetOf<String>()
                                                fun collectVisibleTitles(nodes: List<AsmrOneFileNode>) {
                                                    for (node in nodes) {
                                                        if (node.title.isNotBlank()) titlesToTranslate.add(node.title)
                                                        if (node.type == "folder" && autoExpandedFolders.contains(node.title)) {
                                                            collectVisibleTitles(node.children)
                                                        }
                                                    }
                                                }
                                                collectVisibleTitles(fileTree)
                                                if (audioTracks.isNotEmpty()) {
                                                    for (tr in audioTracks) {
                                                        if (tr.title.isNotBlank()) titlesToTranslate.add(tr.title)
                                                    }
                                                }

                                                val provider = TranslationService.getActiveProvider(context)
                                                val map = TranslationService.translateBatch(titlesToTranslate.toList(), targetLangNow, provider)
                                                val anyValid = map.entries.any { (k, v) ->
                                                    !TranslationService.isPoisonedTranslation(k, v, targetLangNow)
                                                }
                                                if (anyValid) {
                                                    translatedNodeTitles = map
                                                    isFileTreeTranslated = true
                                                }
                                            } finally {
                                                isTranslatingFileTree = false
                                            }
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
                    val rootDirectTracks = fileTree.filter { it.type == "audio" }.map {
                        AsmrOneTrack(
                            title = it.title,
                            hash = it.hash,
                            streamUrl = it.streamUrl,
                            duration = it.duration,
                            size = it.size,
                            type = "audio"
                        )
                    }
                    items(fileTree) { node ->
                        FileTreeNodeItem(
                            node = node,
                            fallbackAudioTracks = audioTracks,
                            work = work,
                            playerViewModel = playerViewModel,
                            autoExpandedFolders = autoExpandedFolders,
                            translatedTitles = translatedNodeTitles,
                            isTranslationActive = isFileTreeTranslated,
                            onOpenPlayer = onOpenPlayer,
                            parentFolderTracks = rootDirectTracks.takeIf { it.isNotEmpty() },
                            onTranslateNodes = { missingTitles ->
                                coroutineScope.launch {
                                    val targetLangNow = TranslationService.getActiveTargetLanguage(context)
                                    val provider = TranslationService.getActiveProvider(context)
                                    val map = TranslationService.translateBatch(missingTitles, targetLangNow, provider)
                                    if (map.isNotEmpty()) {
                                        translatedNodeTitles = translatedNodeTitles + map
                                    }
                                }
                            }
                        )
                    }
                } else {
                    // Fallback to flat audio tracks list
                    items(audioTracks) { track ->
                        val trackIdx = audioTracks.indexOf(track)
                        val displayTrackTitle = if (isFileTreeTranslated) {
                            translatedNodeTitles[track.title.trim()] ?: translatedNodeTitles[track.title] ?: track.title
                        } else {
                            track.title
                        }
                        AudioTrackRow(
                            track = track,
                            displayTitle = displayTrackTitle,
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
