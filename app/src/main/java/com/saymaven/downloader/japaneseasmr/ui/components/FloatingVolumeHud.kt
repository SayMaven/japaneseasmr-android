package com.saymaven.downloader.japaneseasmr.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saymaven.downloader.japaneseasmr.service.UsbDacManager
import kotlin.math.roundToInt

@Composable
fun FloatingVolumeHud(
    modifier: Modifier = Modifier
) {
    val showHud by UsbDacManager.showVolumeHud.collectAsState()
    val hardwareVolume by UsbDacManager.hardwareVolume.collectAsState()
    val dacState by UsbDacManager.dacState.collectAsState()

    AnimatedVisibility(
        visible = showHud && dacState.isExclusiveActive,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 18.dp, end = 18.dp)
    ) {
        // 100% Solid (ZERO transparency), Slim Height, Horizontally Wide
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E1F22), // 100% Solid Dark Surface
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        hardwareVolume > 0.5f -> Icons.AutoMirrored.Filled.VolumeUp
                        hardwareVolume > 0.001f -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeMute
                    },
                    contentDescription = "Volume",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Slider(
                    value = hardwareVolume,
                    onValueChange = {
                        UsbDacManager.setHardwareVolume(it)
                        UsbDacManager.triggerVolumeHud()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color(0xFF33353A)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${(hardwareVolume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}
