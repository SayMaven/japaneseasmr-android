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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
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
    val displayMode by viewModel.floatingLyricsDisplayMode.collectAsState()
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
            val primaryTextColor = if (opacity < 0.35f) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
            val textShadow = if (opacity < 0.35f) {
                Shadow(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 6f
                )
            } else {
                null
            }
            val surfaceColor = if (opacity <= 0.01f) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = opacity)
            }

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
                    color = surfaceColor,
                    shadowElevation = if (opacity >= 0.85f) 4.dp else 0.dp,
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
                            val tr = activeLine.translatedText
                            val hasTranslation = isTranslated && !tr.isNullOrBlank()

                            when (displayMode) {
                                "TRANSLATION_ONLY" -> {
                                    Text(
                                        text = if (hasTranslation) tr!! else activeLine.text,
                                        style = TextStyle(
                                            fontSize = fontSize.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor,
                                            shadow = textShadow
                                        ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                "ORIGINAL_ONLY" -> {
                                    Text(
                                        text = activeLine.text,
                                        style = TextStyle(
                                            fontSize = fontSize.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor,
                                            shadow = textShadow
                                        ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                else -> {
                                    Text(
                                        text = activeLine.text,
                                        style = TextStyle(
                                            fontSize = fontSize.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor,
                                            shadow = textShadow
                                        ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (hasTranslation) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = tr!!,
                                            style = TextStyle(
                                                fontSize = (fontSize - 1.5f).coerceAtLeast(10f).sp,
                                                color = primaryTextColor.copy(alpha = 0.85f),
                                                shadow = textShadow
                                            ),
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
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
                                tint = primaryTextColor.copy(alpha = 0.75f),
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
                                tint = primaryTextColor.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
