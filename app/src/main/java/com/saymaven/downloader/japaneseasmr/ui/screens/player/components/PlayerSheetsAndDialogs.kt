package com.saymaven.downloader.japaneseasmr.ui.screens.player.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.model.ChapterItem
import com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper
import com.saymaven.downloader.japaneseasmr.service.lyrics.SubtitleSource
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSleepTimerSheet(
    sleepTimerRemainingMs: Long?,
    draftTimerMinutes: Int,
    onDraftTimerMinutesChange: (Int) -> Unit,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sleepTimerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentLocalConfig = LocalConfiguration.current
    val currentLocalContext = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sleepTimerSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        CompositionLocalProvider(
            LocalConfiguration provides currentLocalConfig,
            LocalContext provides currentLocalContext
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
                        val totalSecs = (sleepTimerRemainingMs / 1000).coerceAtLeast(0)
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
                                    onDraftTimerMinutesChange(newM)
                                } else {
                                    onDraftTimerMinutesChange((draftTimerMinutes - 5).coerceAtLeast(5))
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.player_timer_minus5))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val displayMinutes = if (sleepTimerRemainingMs != null) {
                                ((sleepTimerRemainingMs + 59999) / 60000).toInt()
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
                                    onDraftTimerMinutesChange(newM)
                                } else {
                                    onDraftTimerMinutesChange((draftTimerMinutes + 5).coerceAtMost(360))
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
                                onDraftTimerMinutesChange(mins)
                                viewModel.startSleepTimer(mins)
                                onDismiss()
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
                                onDraftTimerMinutesChange(mins)
                                viewModel.startSleepTimer(mins)
                                onDismiss()
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

                Button(
                    onClick = {
                        viewModel.startSleepTimer(draftTimerMinutes)
                        onDismiss()
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerChapterSheet(
    chapters: List<ChapterItem>,
    translatedChapters: List<ChapterItem>,
    activeChapterIndex: Int,
    isLoadingChapters: Boolean,
    isTranslationActive: Boolean,
    isTranslating: Boolean,
    sheetNestedScroll: NestedScrollConnection,
    scope: CoroutineScope,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val chapterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentLocalConfig = LocalConfiguration.current
    val currentLocalContext = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = chapterSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        CompositionLocalProvider(
            LocalConfiguration provides currentLocalConfig,
            LocalContext provides currentLocalContext
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
                                onDismiss()
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
                                onClick = { viewModel.seekToChapter(chapter) },
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

@Composable
fun PlayerLinkRjDialog(
    currentRjId: String,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rjInput by remember { mutableStateOf("") }
    var isLinking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLinking) onDismiss() },
        icon = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.player_link_rj_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.player_link_rj_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = rjInput,
                    onValueChange = { rjInput = it },
                    label = { Text(stringResource(R.string.player_link_rj_input_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLinking
                )
                if (isLinking) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Menghubungkan ke DLsite…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rjInput.isNotBlank() && !isLinking) {
                        isLinking = true
                        viewModel.linkRjCode(currentRjId, rjInput) { success, rjOrMsg ->
                            isLinking = false
                            if (success) {
                                onDismiss()
                                Toast.makeText(context, context.getString(R.string.player_link_rj_success_toast, rjOrMsg), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, rjOrMsg ?: context.getString(R.string.player_link_rj_error_toast, rjInput), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !isLinking && rjInput.isNotBlank()
            ) {
                Text(stringResource(R.string.player_link_rj_btn_link))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLinking
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerCoverPickerSheet(
    rjid: String,
    currentCoverUrl: String?,
    playlist: List<HistoryEntity>,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coverSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentItem = playlist.firstOrNull { it.rjid == rjid }
    val folderFile = currentItem?.localFilePath?.let { File(it) }?.let { if (it.isDirectory) it else it.parentFile }
    val localFolderImages = remember(rjid, folderFile?.canonicalPath) {
        if (folderFile != null && folderFile.exists()) {
            AudioStorageHelper.getAllLocalImagesInFolder(folderFile)
        } else {
            emptyList()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val coversDir = File(context.cacheDir, "covers").apply { if (!exists()) mkdirs() }
                val targetFile = File(coversDir, "custom_${rjid.filter { it.isLetterOrDigit() }}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    viewModel.updateCustomCover(rjid, targetFile.absolutePath)
                    onDismiss()
                    Toast.makeText(context, context.getString(R.string.player_cover_success_toast), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = coverSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.player_cover_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FilledTonalButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.player_cover_btn_gallery))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (localFolderImages.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.player_cover_folder_images),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(localFolderImages) { imgFile ->
                        val isSelected = currentCoverUrl == imgFile.absolutePath
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(96.dp)
                                .clickable {
                                    viewModel.updateCustomCover(rjid, imgFile.absolutePath)
                                    onDismiss()
                                    Toast.makeText(context, context.getString(R.string.player_cover_success_toast), Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                shadowElevation = 2.dp
                            ) {
                                AsyncImage(
                                    model = imgFile,
                                    contentDescription = imgFile.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(96.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = imgFile.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSubtitlePickerSheet(
    availableSubtitles: List<SubtitleSource>,
    selectedSubtitleSource: SubtitleSource?,
    onSelectSubtitle: (SubtitleSource) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.player_lyrics_select_file),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            availableSubtitles.forEach { src ->
                val isSelected = selectedSubtitleSource?.pathOrUrl == src.pathOrUrl
                ListItem(
                    headlineContent = { Text(src.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(src.format.uppercase(), style = MaterialTheme.typography.labelSmall) },
                    trailingContent = {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.clickable {
                        onSelectSubtitle(src)
                        onDismiss()
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PlayerSpeedDialog(
    playbackSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_speed_dialog_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                speedOptions.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectSpeed(speed)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (playbackSpeed == speed),
                            onClick = {
                                onSelectSpeed(speed)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
