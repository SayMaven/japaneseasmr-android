package com.saymaven.downloader.japaneseasmr.ui.screens.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.service.lyrics.LyricLine
import com.saymaven.downloader.japaneseasmr.service.lyrics.SubtitleSource

@Composable
fun PlayerCoverLyricsPanel(
    currentCoverUrl: String?,
    lyrics: List<LyricLine>,
    activeLyricIndex: Int,
    availableSubtitles: List<SubtitleSource>,
    selectedSubtitleSource: SubtitleSource?,
    isLyricsTranslated: Boolean,
    isTranslatingLyrics: Boolean,
    showLyricsView: Boolean,
    onShowLyricsViewChange: (Boolean) -> Unit,
    onOpenSubtitlePicker: () -> Unit,
    onToggleLyricsTranslation: () -> Unit,
    onSeekToLyricLine: (LyricLine) -> Unit,
    privacyEnabled: Boolean,
    privacyBlurInApp: Boolean,
    temporaryUnblur: Boolean,
    onTemporaryUnblurChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var lastValidPainter by remember { mutableStateOf<Painter?>(null) }
    val isCoverBlurred = privacyEnabled && privacyBlurInApp && !temporaryUnblur
    val hasSubtitles = lyrics.isNotEmpty() || availableSubtitles.isNotEmpty()

    if (showLyricsView && hasSubtitles) {
        val lyricsListState = rememberLazyListState()
        LaunchedEffect(activeLyricIndex) {
            if (activeLyricIndex in lyrics.indices) {
                lyricsListState.animateScrollToItem((activeLyricIndex - 1).coerceAtLeast(0))
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(260.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { if (availableSubtitles.size > 1) onOpenSubtitlePicker() },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedSubtitleSource?.title ?: stringResource(R.string.player_lyrics_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleLyricsTranslation,
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isTranslatingLyrics) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = stringResource(R.string.player_lyrics_translate),
                                    tint = if (isLyricsTranslated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onShowLyricsViewChange(false) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = stringResource(R.string.player_lyrics_show_cover),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                if (lyrics.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.player_lyrics_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = lyricsListState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(lyrics) { idx, line ->
                            val isActive = idx == activeLyricIndex
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSeekToLyricLine(line) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    textAlign = TextAlign.Center
                                )
                                if (isLyricsTranslated && !line.translatedText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = line.translatedText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .aspectRatio(1f)
                .heightIn(max = 260.dp)
                .clickable {
                    if (privacyEnabled && privacyBlurInApp && !temporaryUnblur) {
                        onTemporaryUnblurChange(true)
                    } else if (hasSubtitles) {
                        onShowLyricsViewChange(true)
                    } else if (privacyEnabled && privacyBlurInApp) {
                        onTemporaryUnblurChange(!temporaryUnblur)
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val cover = currentCoverUrl
                if (!cover.isNullOrBlank()) {
                    val imageRequest = remember(cover) {
                        ImageRequest.Builder(context)
                            .data(cover)
                            .memoryCacheKey(cover)
                            .crossfade(false)
                            .build()
                    }
                    val imageModifier = Modifier.fillMaxSize().then(
                        if (isCoverBlurred) Modifier.blur(25.dp) else Modifier
                    )
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Cover Art",
                        contentScale = ContentScale.Crop,
                        placeholder = lastValidPainter,
                        error = lastValidPainter,
                        onSuccess = { success ->
                            lastValidPainter = success.painter
                        },
                        modifier = imageModifier
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

                if (hasSubtitles) {
                    Surface(
                        onClick = { onShowLyricsViewChange(true) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = stringResource(R.string.player_lyrics_title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.player_lyrics_title),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
