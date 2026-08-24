package com.saymaven.downloader.japaneseasmr.ui.screens.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onPlayTrack: (HistoryEntity) -> Unit,
    onRedownload: (HistoryEntity) -> Unit
) {
    val context = LocalContext.current
    val historyList by viewModel.historyList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    val listState = rememberLazyListState()

    // Saat urutan diubah, paksa posisi scroll tetap diam di index 0 (paling atas)
    LaunchedEffect(sortOrder) {
        listState.scrollToItem(0)
    }

    var itemToDelete by remember { mutableStateOf<HistoryEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val hasMissingFiles = remember(historyList) {
        historyList.any { item ->
            val path = item.localFilePath
            path.isNullOrBlank() || !File(path).exists()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Clean Header with Overflow Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Riwayat Koleksi (${historyList.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opsi Riwayat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (hasMissingFiles) {
                        DropdownMenuItem(
                            text = { Text("Bersihkan File Hilang") },
                            leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.cleanMissingFiles()
                                Toast.makeText(context, "File hilang dibersihkan dari riwayat.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    if (historyList.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Hapus Semua Riwayat", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                viewModel.clearAllHistory()
                                Toast.makeText(context, "Semua riwayat dihapus.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            label = { Text("Cari karya, CV, atau circle...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Sorting Filter Chips Row (Waktu & Nama with Asc/Desc toggle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isDateActive = (sortOrder == HistorySortOrder.DATE_DESC || sortOrder == HistorySortOrder.DATE_ASC)
            val isTitleActive = (sortOrder == HistorySortOrder.TITLE_ASC || sortOrder == HistorySortOrder.TITLE_DESC)

            FilterChip(
                selected = isDateActive,
                onClick = { viewModel.toggleSortByDate() },
                label = {
                    Text(
                        text = if (sortOrder == HistorySortOrder.DATE_ASC) "Waktu (Terlama \u2191)" else "Waktu (Terbaru \u2193)",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (sortOrder == HistorySortOrder.DATE_ASC) Icons.Default.ArrowUpward else Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(10.dp)
            )

            FilterChip(
                selected = isTitleActive,
                onClick = { viewModel.toggleSortByTitle() },
                label = {
                    Text(
                        text = if (sortOrder == HistorySortOrder.TITLE_DESC) "Nama (Z - A \u2191)" else "Nama (A - Z \u2193)",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (sortOrder == HistorySortOrder.TITLE_DESC) Icons.Default.ArrowUpward else Icons.Default.SortByAlpha,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "Belum ada riwayat unduhan." else "Tidak ada karya yang cocok.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            // Menggunakan key bertaut sortOrder agar Compose tidak melompat melacak item lama ke bawah
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyList, key = { "${sortOrder.name}_${it.rjid}" }) { item ->
                    val isPresent = remember(item.localFilePath) { viewModel.isFilePresent(item) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPresent) {
                                    onPlayTrack(item)
                                } else {
                                    Toast.makeText(context, "File audio tidak ditemukan. Silakan unduh ulang.", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPresent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val coverUrl = item.coverUrl
                            if (!coverUrl.isNullOrBlank()) {
                                val imageReq = remember(coverUrl) {
                                    ImageRequest.Builder(context)
                                        .data(coverUrl)
                                        .memoryCacheKey(coverUrl)
                                        .crossfade(false)
                                        .build()
                                }
                                AsyncImage(
                                    model = imageReq,
                                    contentDescription = "Cover",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(70.dp, 52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp, 52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "[${item.rjid}] ${item.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isPresent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "CV: ${item.cv} \u2022 ${item.circle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isPresent) "${item.downloadDate} \u2022 ${item.fileSize}" else "File belum diunduh / hilang",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row {
                                if (isPresent) {
                                    IconButton(onClick = { onPlayTrack(item) }) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Putar",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { onRedownload(item) }) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "Unduh Ulang",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                IconButton(onClick = { itemToDelete = item }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus Riwayat",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    itemToDelete?.let { item ->
        val fileExists = remember(item.localFilePath) {
            val path = item.localFilePath
            !path.isNullOrBlank() && File(path).exists()
        }
        var alsoDeleteLocalFile by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus Riwayat") },
            text = {
                Column {
                    Text("Hapus [${item.rjid}] ${item.title} dari riwayat koleksi?")
                    if (fileExists) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { alsoDeleteLocalFile = !alsoDeleteLocalFile }
                        ) {
                            Checkbox(
                                checked = alsoDeleteLocalFile,
                                onCheckedChange = { alsoDeleteLocalFile = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hapus juga file audio dari memori HP", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHistory(item, deleteFile = alsoDeleteLocalFile)
                        itemToDelete = null
                        Toast.makeText(context, "Riwayat dihapus", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
