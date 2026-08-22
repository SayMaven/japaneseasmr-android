package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import java.io.File
import com.saymaven.downloader.japaneseasmr.BuildConfig

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val customDir by viewModel.downloadDir.collectAsState()
    val parallelConn by viewModel.parallelConnections.collectAsState()
    val autoClipboard by viewModel.autoClipboard.collectAsState()
    val useDetailedFilename by viewModel.useDetailedFilename.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showConnectionDialog by remember { mutableStateOf(false) }
    var customPathInput by remember { mutableStateOf("") }
    var showCacheClearedSnackbar by remember { mutableStateOf(false) }

    val activeDownloadPath = remember(customDir) {
        if (!customDir.isNullOrBlank()) customDir!! else DownloadService.getDefaultDownloadDirectory().absolutePath
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = uri.path ?: uri.toString()
            viewModel.setDownloadDir(path)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Pengaturan",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. TAMPILAN & TEMA
        Text(
            text = "TAMPILAN & TEMA",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Tema Aplikasi") },
                    supportingContent = {
                        Text(
                            when (themeMode) {
                                ThemeMode.SYSTEM -> "Mengikuti Sistem (Auto)"
                                ThemeMode.DARK -> "Tema Gelap (Dracula / Dark)"
                                ThemeMode.LIGHT -> "Tema Terang (Light Mode)"
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Material You (Warna Dinamis)") },
                        supportingContent = { Text("Menyesuaikan palet warna wallpaper perangkat") },
                        leadingContent = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { viewModel.setDynamicColor(it) }
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. PENYIMPANAN & UNDUHAN
        Text(
            text = "PENYIMPANAN & UNDUHAN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Folder Tujuan Unduhan") },
                    supportingContent = {
                        Text(
                            text = activeDownloadPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                customPathInput = activeDownloadPath
                                showFolderDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Ubah Folder")
                            }
                            IconButton(onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(activeDownloadPath), "resource/folder")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Buka Folder")
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        customPathInput = activeDownloadPath
                        showFolderDialog = true
                    }
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Gunakan Judul Karya sebagai Nama File") },
                    supportingContent = {
                        Text(if (useDetailedFilename) "Format: [RJ01673437] Judul Karya.mp3" else "Format: RJ01673437.mp3")
                    },
                    leadingContent = { Icon(Icons.Default.TextFields, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = useDetailedFilename,
                            onCheckedChange = { viewModel.setUseDetailedFilename(it) }
                        )
                    }
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Koneksi Paralel per Unduhan") },
                    supportingContent = { Text("$parallelConn koneksi simultan (HLS multi-thread)") },
                    leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) },
                    modifier = Modifier.clickable { showConnectionDialog = true }
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Otomatis Deteksi Clipboard") },
                    supportingContent = { Text("Otomatis mendeteksi kode RJ dari clipboard saat membuka tab Antrean") },
                    leadingContent = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = autoClipboard,
                            onCheckedChange = { viewModel.setAutoClipboard(it) }
                        )
                    }
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Bersihkan Cache Gambar & Temp") },
                    supportingContent = { Text("Hapus file cover cache & partisi sementara") },
                    leadingContent = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.clearCache()
                        showCacheClearedSnackbar = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. TENTANG APLIKASI
        Text(
            text = "TENTANG APLIKASI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("JapaneseASMR Downloader") },
                    supportingContent = { Text("Versi ${BuildConfig.VERSION_NAME} (Native Android Engine)") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Engine Unduhan") },
                    supportingContent = { Text("Native Kotlin HLS Demuxer ($parallelConn Threads) - Tanpa perlu yt-dlp.exe") },
                    leadingContent = { Icon(Icons.Default.Memory, contentDescription = null) }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Pengembang") },
                    supportingContent = { Text("SayMaven") },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                )
            }
        }
    }

    // Dialog Ubah Folder Unduhan
    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Ubah Folder Unduhan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pilih lokasi penyimpanan audio:")

                    OutlinedButton(
                        onClick = {
                            viewModel.setDownloadDir("/storage/emulated/0/Download/JapaneseASMR")
                            showFolderDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download/JapaneseASMR (Default)")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.setDownloadDir("/storage/emulated/0/Music/JapaneseASMR")
                            showFolderDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Music/JapaneseASMR")
                    }

                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = { customPathInput = it },
                        label = { Text("Atau masukkan path kustom") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customPathInput.isNotBlank()) {
                        viewModel.setDownloadDir(customPathInput.trim())
                    }
                    showFolderDialog = false
                }) {
                    Text("Simpan Path")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Jumlah Koneksi Paralel
    if (showConnectionDialog) {
        val options = listOf(4, 8, 16, 24, 32)
        AlertDialog(
            onDismissRequest = { showConnectionDialog = false },
            title = { Text("Pilih Jumlah Koneksi Paralel") },
            text = {
                Column {
                    options.forEach { count ->
                        ListItem(
                            headlineContent = { Text("$count Koneksi Simultan" + if (count == 16) " (Disarankan)" else "") },
                            modifier = Modifier.clickable {
                                viewModel.setParallelConnections(count)
                                showConnectionDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnectionDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Dialog Tema
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Pilih Tema") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Mengikuti Sistem") },
                        modifier = Modifier.clickable {
                            viewModel.setThemeMode(ThemeMode.SYSTEM)
                            showThemeDialog = false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Gelap (Dracula / Dark)") },
                        modifier = Modifier.clickable {
                            viewModel.setThemeMode(ThemeMode.DARK)
                            showThemeDialog = false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Terang (Light)") },
                        modifier = Modifier.clickable {
                            viewModel.setThemeMode(ThemeMode.LIGHT)
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}
