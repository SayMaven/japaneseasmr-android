package com.saymaven.downloader.japaneseasmr.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val title by viewModel.currentTitle.collectAsState()
    val artist by viewModel.currentArtist.collectAsState()
    val coverUrl by viewModel.currentCoverUrl.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPos by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()

    var sliderPos by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Large Artwork
        AsyncImage(
            model = coverUrl ?: "https://pic.weeabo0.xyz/RJ01673437_img_main.jpg",
            contentDescription = "Cover Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(280.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Title & CV
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "CV: $artist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Timeline Slider
        val displayPos = sliderPos ?: if (duration > 0) (currentPos.toFloat() / duration.toFloat()) else 0f
        Slider(
            value = displayPos.coerceIn(0f, 1f),
            onValueChange = { sliderPos = it },
            onValueChangeFinished = {
                sliderPos?.let { pos ->
                    val seekTarget = (pos * duration).toLong()
                    viewModel.seekTo(seekTarget)
                }
                sliderPos = null
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(if (sliderPos != null) (sliderPos!! * duration).toLong() else currentPos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleLoop() }) {
                Icon(
                    Icons.Default.Repeat,
                    contentDescription = "Loop",
                    tint = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { viewModel.seekTo(0) }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", modifier = Modifier.size(36.dp))
            }

            FilledIconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = { viewModel.seekTo((currentPos + 10000).coerceAtMost(duration)) }) {
                Icon(Icons.Default.Forward10, contentDescription = "Fast Forward", modifier = Modifier.size(36.dp))
            }

            IconButton(onClick = { viewModel.seekTo((currentPos - 10000).coerceAtLeast(0)) }) {
                Icon(Icons.Default.Replay10, contentDescription = "Rewind", modifier = Modifier.size(36.dp))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
