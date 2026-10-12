package com.saymaven.downloader.japaneseasmr.ui.screens.queue

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.data.model.DownloadStatus
import com.saymaven.downloader.japaneseasmr.service.DownloadService

@Composable
fun QueueScreen(viewModel: QueueViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val queue by viewModel.queueState.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val previewWork by viewModel.previewWork.collectAsState()
    val previewTranslatedTitle by viewModel.previewTranslatedTitle.collectAsState()
    val isTranslationActive by viewModel.isTranslationActive.collectAsState()
    val translationTargetLang by viewModel.translationTargetLang.collectAsState()
    val isLoadingPreview by viewModel.isLoadingPreview.collectAsState()
    val logs by DownloadService.logsState.collectAsState()
    val showConsole by viewModel.showConsole.collectAsState()
    val listState = rememberLazyListState()

    var showLogsInside by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.initPreferences(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAutoClipboard(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && showConsole && showLogsInside) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ================= 0. TOP APP HEADER WITH CONSOLE TOGGLE =================
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "JapaneseASMR Downloader",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (queue.isNotEmpty()) "${queue.size} item dalam antrean" else "Siap mengunduh",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleShowConsole(context) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (showConsole) Icons.Default.Terminal else Icons.Default.Code,
                        contentDescription = if (showConsole) "Sembunyikan Konsol Log" else "Tampilkan Konsol Log",
                        tint = if (showConsole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ================= 1. INPUT CARD =================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tambah Unduhan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { viewModel.onInputChanged(it) },
                        label = { Text("Kode RJ atau Tautan (misal: RJ337874)") },
                        placeholder = { Text("RJ337874") },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.pasteFromClipboard(context) }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Tempel Clipboard", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Preview Card
                    if (isLoadingPreview) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (previewWork != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            AsyncImage(
                                model = previewWork?.coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp, 44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val showTrans = isTranslationActive && !previewTranslatedTitle.isNullOrBlank()
                                val displayPreviewTitle = if (showTrans) previewTranslatedTitle!! else (previewWork?.title ?: "")

                                Text(
                                    text = "[${previewWork?.rjid}] $displayPreviewTitle",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (showTrans && previewTranslatedTitle != previewWork?.title) {
                                    Text(
                                        text = previewWork?.title ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "CV: ${previewWork?.cv ?: "-"} \u2022 ${previewWork?.circle ?: "-"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (inputText.isBlank()) {
                                    Toast.makeText(context, "Masukkan kode RJ terlebih dahulu", Toast.LENGTH_SHORT).show()
                                } else {
                                    val added = viewModel.addToQueue()
                                    if (added) {
                                        Toast.makeText(context, "Berhasil ditambahkan ke antrean", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Format kode RJ tidak valid", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Antrean",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                if (inputText.isBlank() && queue.isEmpty()) {
                                    Toast.makeText(context, "Masukkan kode RJ terlebih dahulu", Toast.LENGTH_SHORT).show()
                                } else {
                                    val added = viewModel.addToQueue()
                                    if (added || queue.isNotEmpty()) {
                                        viewModel.startDownload(context)
                                    } else {
                                        Toast.makeText(context, "Format kode RJ tidak valid", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Unduh",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // ================= 2. QUEUE LIST SECTION =================
        if (queue.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Antrean Unduhan Kosong",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Masukkan kode RJ atau salin tautan karya dari browser untuk mulai mengunduh audio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Antrean Unduhan (${queue.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearQueue() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bersihkan", style = MaterialTheme.typography.labelMedium)
                        }

                        if (!isDownloading) {
                            FilledTonalButton(
                                onClick = { viewModel.startDownload(context) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mulai", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            items(queue, key = { it.rjid }) { item ->
                QueueItemCard(
                    item = item,
                    onDismiss = { viewModel.removeItem(item.rjid) }
                )
            }
        }

        // ================= 3. REAL-TIME LOG CONSOLE (PERSISTED HIDE/UNHIDE) =================
        if (showConsole) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Live Log Proses Unduhan",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = { showLogsInside = !showLogsInside },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    if (showLogsInside) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Log",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = showLogsInside) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp, max = 240.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    if (logs.isEmpty()) {
                                        Text(
                                            text = "Belum ada proses unduhan berjalan.\nMasukkan kode RJ di atas lalu tekan 'Unduh' atau 'Antrean'.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    } else {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(logs) { logLine ->
                                                Text(
                                                    text = logLine,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    fontFamily = FontFamily.Monospace,
                                                    color = when {
                                                        logLine.contains("[ERROR]") || logLine.contains("[!]") -> MaterialTheme.colorScheme.error
                                                        logLine.contains("[SUCCESS]") -> MaterialTheme.colorScheme.primary
                                                        logLine.contains("====") -> MaterialTheme.colorScheme.secondary
                                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (logs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = { DownloadService.clearLogs() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Bersihkan Log", style = MaterialTheme.typography.labelSmall)
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
}

@Composable
fun QueueItemCard(
    item: DownloadQueueItem,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp, 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val hasTranslation = !item.translatedTitle.isNullOrBlank()
                    val mainTitle = if (hasTranslation) item.translatedTitle!! else item.title.ifBlank { "Mengambil info..." }

                    Text(
                        text = "[${item.rjid}] $mainTitle",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hasTranslation && item.translatedTitle != item.title) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Status Pill Chip
                    val (statusBg, statusFg) = when (item.status) {
                        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
                        DownloadStatus.PROCESSING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBg.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = item.statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (item.status != DownloadStatus.DOWNLOADING && item.status != DownloadStatus.PROCESSING) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Hapus Antrean",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PROCESSING) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(item.progress * 100).toInt()}% • ${item.speed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${item.downloadedSize} / ${item.totalSize} • ETA: ${item.eta}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
