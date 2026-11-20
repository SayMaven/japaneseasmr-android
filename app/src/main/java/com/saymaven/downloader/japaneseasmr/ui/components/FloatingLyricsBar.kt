package com.saymaven.downloader.japaneseasmr.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel
import kotlin.math.roundToInt

/**
 * In-app floating lyrics bar rendered above MiniPlayer (KikoFlu style).
 */
@Composable
fun FloatingLyricsBar(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val enabled by viewModel.floatingLyricsEnabled.collectAsState()
    val lyrics by viewModel.currentLyrics.collectAsState()
    val activeIdx by viewModel.activeLyricIndex.collectAsState()
    val fontSize by viewModel.floatingLyricsFontSize.collectAsState()
    val opacity by viewModel.floatingLyricsOpacity.collectAsState()
    val cornerRadius by viewModel.floatingLyricsCornerRadius.collectAsState()
    val showTranslation by viewModel.floatingLyricsShowTranslation.collectAsState()
    val isLocked by viewModel.floatingLyricsLocked.collectAsState()
    val isTranslated by viewModel.isLyricsTranslated.collectAsState()

    val activeLine = lyrics.getOrNull(activeIdx) ?: lyrics.firstOrNull()
    val isVisible = enabled && lyrics.isNotEmpty() && activeLine != null

    var offsetY by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (activeLine != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .then(
                        if (!isLocked) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offsetY = (offsetY + dragAmount.y).coerceIn(-400f, 40f)
                                }
                            }
                        } else Modifier
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(cornerRadius.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = opacity),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = activeLine.text,
                                fontSize = fontSize.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val tr = activeLine.translatedText
                            if (showTranslation && isTranslated && !tr.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr,
                                    fontSize = (fontSize - 1.5f).coerceAtLeast(10f).sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { viewModel.setFloatingLyricsLocked(!isLocked) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.setFloatingLyricsEnabled(false) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
