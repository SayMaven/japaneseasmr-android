package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import kotlinx.coroutines.launch

@Composable
fun ColorPaletteCarousel(
    selectedPalette: ColorPalette,
    onSelectPalette: (ColorPalette) -> Unit
) {
    val palettes = remember { ColorPalette.values().toList() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val totalPages = remember(palettes.size) { (palettes.size + 3) / 4 }

    val activePageIndex by remember {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            (firstIdx / 4).coerceIn(0, totalPages - 1)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(palettes, key = { _, it -> it.name }) { idx, palette ->
                val isSelected = (palette == selectedPalette)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = if (isSelected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Transparent),
                    modifier = Modifier
                        .size(width = 82.dp, height = 82.dp)
                        .clickable {
                            onSelectPalette(palette)
                            scope.launch {
                                listState.animateScrollToItem((idx - 1).coerceAtLeast(0))
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(50.dp)) {
                            drawArc(
                                color = palette.primary,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = true
                            )
                            drawArc(
                                color = palette.secondary,
                                startAngle = 90f,
                                sweepAngle = 90f,
                                useCenter = true
                            )
                            drawArc(
                                color = palette.tertiary,
                                startAngle = 0f,
                                sweepAngle = 90f,
                                useCenter = true
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { pageIdx ->
                val isCurrent = (pageIdx == activePageIndex)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isCurrent) 8.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        )
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
