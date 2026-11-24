package com.saymaven.downloader.japaneseasmr.ui.screens.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.ui.components.AudioGainDialog
import com.saymaven.downloader.japaneseasmr.ui.components.PlaylistSelectionSheet
import com.saymaven.downloader.japaneseasmr.ui.screens.player.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit = {}
) {
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
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val dacState by viewModel.dacState.collectAsState()
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
    val isOnlineStreaming by viewModel.isOnlineStreaming.collectAsState()
    val isPlaylistQueueActive by viewModel.isPlaylistQueueActive.collectAsState()
    val isVirtualAlbum by viewModel.isVirtualAlbum.collectAsState()
    val currentStreamTrackTitle by viewModel.currentStreamTrackTitle.collectAsState()
    val currentDisplayBadge by viewModel.currentDisplayBadge.collectAsState()
    val privacyEnabled by viewModel.privacyEnabled.collectAsState()
    val privacyBlurInApp by viewModel.privacyBlurInApp.collectAsState()
    val privacyMaskTitle by viewModel.privacyMaskTitle.collectAsState()
    val privacyCustomMask by viewModel.privacyCustomMask.collectAsState()
    val playerButtonOrder by viewModel.playerButtonOrder.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    var temporaryUnblur by remember { mutableStateOf(false) }

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
    var showChapterSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var draftTimerMinutes by remember { mutableIntStateOf(15) }
    var showLinkRjDialog by remember { mutableStateOf(false) }
    var showCoverPickerSheet by remember { mutableStateOf(false) }
    var showPlaylistPickerSheet by remember { mutableStateOf(false) }
    var showAudioGainDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    val audioGainDb by viewModel.audioGainDb.collectAsState()
    var showLyricsView by remember { mutableStateOf(false) }
    var showSubtitlePickerSheet by remember { mutableStateOf(false) }
    val lyrics by viewModel.currentLyrics.collectAsState()
    val activeLyricIndex by viewModel.activeLyricIndex.collectAsState()
    val availableSubtitles by viewModel.availableSubtitles.collectAsState()
    val selectedSubtitleSource by viewModel.selectedSubtitleSource.collectAsState()
    val isLyricsTranslated by viewModel.isLyricsTranslated.collectAsState()
    val isTranslatingLyrics by viewModel.isTranslatingLyrics.collectAsState()

    val sheetNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    val effectivePosition = if (isSliderDragging) dragPosition else currentPosition

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ================= 1. HEADER (TITLE & CV) & COVER ART =================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    if (currentDisplayBadge.isNotBlank()) {
                        Surface(
                            onClick = {
                                val match = Regex("(?:RJ|VJ|BJ)\\d+", RegexOption.IGNORE_CASE).find(currentDisplayBadge)?.value
                                val rjToCopy = match ?: currentDisplayBadge
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("ASMR ID", rjToCopy)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.toast_id_copied, rjToCopy), Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = currentDisplayBadge,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(40.dp))
                    }

                    Spacer(modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))

                val shouldMask = privacyEnabled && privacyMaskTitle
                val cleanOriginalTitle = if (shouldMask) {
                    privacyCustomMask.ifBlank { stringResource(R.string.settings_privacy_custom_mask_default) }
                } else {
                    currentTitle.replace(Regex("^\\[(?:RJ|VJ|BJ)?\\d+\\]\\s*"), "").trim()
                }
                val cleanTranslatedTitle = if (shouldMask) null else translatedTitle?.replace(Regex("^\\[(?:RJ|VJ|BJ)?\\d+\\]\\s*"), "")?.trim()

                val displayTitle = if (isTranslationActive && !cleanTranslatedTitle.isNullOrBlank()) {
                    cleanTranslatedTitle
                } else cleanOriginalTitle

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 104.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = displayTitle.ifBlank { stringResource(R.string.player_no_track) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if ((isOnlineStreaming || isVirtualAlbum) && !currentStreamTrackTitle.isNullOrBlank() && !shouldMask) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentStreamTrackTitle!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isTranslationActive && !cleanTranslatedTitle.isNullOrBlank() && cleanOriginalTitle.isNotBlank() && cleanTranslatedTitle != cleanOriginalTitle) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = cleanOriginalTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "CV: $currentArtist",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                PlayerCoverLyricsPanel(
                    currentCoverUrl = currentCoverUrl,
                    lyrics = lyrics,
                    activeLyricIndex = activeLyricIndex,
                    availableSubtitles = availableSubtitles,
                    selectedSubtitleSource = selectedSubtitleSource,
                    isLyricsTranslated = isLyricsTranslated,
                    isTranslatingLyrics = isTranslatingLyrics,
                    showLyricsView = showLyricsView,
                    onShowLyricsViewChange = { showLyricsView = it },
                    onOpenSubtitlePicker = { showSubtitlePickerSheet = true },
                    onToggleLyricsTranslation = { viewModel.toggleLyricsTranslation() },
                    onSeekToLyricLine = { viewModel.seekToLyricLine(it) },
                    privacyEnabled = privacyEnabled,
                    privacyBlurInApp = privacyBlurInApp,
                    temporaryUnblur = temporaryUnblur,
                    onTemporaryUnblurChange = { temporaryUnblur = it }
                )
            }

            // ================= 2. TIMELINE, DAC/SPECS & CONTROLS =================
            PlayerControlsSection(
                currentRjId = currentRjId,
                isPlaying = isPlaying,
                effectivePosition = effectivePosition,
                duration = duration,
                showRemainingTime = showRemainingTime,
                onToggleRemainingTime = { viewModel.toggleShowRemainingTime() },
                onSliderDrag = { pos ->
                    isSliderDragging = true
                    dragPosition = pos
                },
                onSliderDragFinished = {
                    viewModel.seekTo(dragPosition)
                    isSliderDragging = false
                },
                chapters = chapters,
                isLoadingChapters = isLoadingChapters,
                activeChapterIndex = activeChapterIndex,
                activeChapter = activeChapter,
                translatedChapters = translatedChapters,
                isTranslationActive = isTranslationActive,
                isTranslating = isTranslating,
                dacState = dacState,
                audioSpecs = audioSpecs,
                playerButtonOrder = playerButtonOrder,
                repeatMode = repeatMode,
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                isOnlineStreaming = isOnlineStreaming && !isPlaylistQueueActive,
                shuffleMode = shuffleMode,
                playbackSpeed = playbackSpeed,
                onOpenChapterSheet = { showChapterSheet = true },
                onOpenSleepTimerSheet = { showSleepTimerSheet = true },
                onOpenSpeedDialog = { showSpeedDialog = true },
                onOpenAudioGainDialog = { showAudioGainDialog = true },
                onOpenPlaylistPickerSheet = { showPlaylistPickerSheet = true },
                onOpenLinkRjDialog = { showLinkRjDialog = true },
                onOpenCoverPickerSheet = { showCoverPickerSheet = true },
                onToggleTranslation = { viewModel.toggleTranslation() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onToggleShuffleMode = { viewModel.toggleShuffleMode() },
                onPlayPrevious = { viewModel.playPrevious() },
                onReplay10 = { viewModel.replay10() },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onForward10 = { viewModel.forward10() },
                onPlayNext = { viewModel.playNext() }
            )
        }
    }

    if (showSleepTimerSheet) {
        PlayerSleepTimerSheet(
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            draftTimerMinutes = draftTimerMinutes,
            onDraftTimerMinutesChange = { draftTimerMinutes = it },
            viewModel = viewModel,
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showChapterSheet) {
        PlayerChapterSheet(
            chapters = chapters,
            translatedChapters = translatedChapters,
            activeChapterIndex = activeChapterIndex,
            isLoadingChapters = isLoadingChapters,
            isTranslationActive = isTranslationActive,
            isTranslating = isTranslating,
            sheetNestedScroll = sheetNestedScroll,
            scope = scope,
            viewModel = viewModel,
            onDismiss = { showChapterSheet = false }
        )
    }

    if (showLinkRjDialog && !currentRjId.isNullOrBlank()) {
        PlayerLinkRjDialog(
            currentRjId = currentRjId!!,
            viewModel = viewModel,
            onDismiss = { showLinkRjDialog = false }
        )
    }

    if (showCoverPickerSheet && !currentRjId.isNullOrBlank()) {
        PlayerCoverPickerSheet(
            rjid = currentRjId!!,
            currentCoverUrl = currentCoverUrl,
            playlist = playlist,
            viewModel = viewModel,
            onDismiss = { showCoverPickerSheet = false }
        )
    }

    if (showPlaylistPickerSheet) {
        val currentRj = currentRjId ?: ""
        if (currentRj.isNotBlank()) {
            PlaylistSelectionSheet(
                workRjid = currentRj,
                workTitle = currentTitle,
                coverUrl = currentCoverUrl ?: "",
                cv = currentArtist,
                playerViewModel = viewModel,
                onDismiss = { showPlaylistPickerSheet = false }
            )
        }
    }

    if (showAudioGainDialog) {
        AudioGainDialog(
            currentGainDb = audioGainDb,
            onGainChanged = { viewModel.setAudioGainDb(it) },
            onDismiss = { showAudioGainDialog = false }
        )
    }

    if (showSubtitlePickerSheet) {
        PlayerSubtitlePickerSheet(
            availableSubtitles = availableSubtitles,
            selectedSubtitleSource = selectedSubtitleSource,
            onSelectSubtitle = { viewModel.selectSubtitleSource(it) },
            onDismiss = { showSubtitlePickerSheet = false }
        )
    }

    if (showSpeedDialog) {
        PlayerSpeedDialog(
            playbackSpeed = playbackSpeed,
            onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }
}
