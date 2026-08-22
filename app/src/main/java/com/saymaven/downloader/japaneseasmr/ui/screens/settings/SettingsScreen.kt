package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.BuildConfig
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import java.io.File

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
            try {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val relativePath = split[1]
                    if (type.equals("primary", ignoreCase = true)) {
                        val path = "/storage/emulated/0/$relativePath"
                        viewModel.setDownloadDir(path)
                        return@rememberLauncherForActivityResult
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
            viewModel.setDownloadDir(uri.path ?: uri.toString())
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
                        Text(if (useDetailedFilename) "Format: [RJ01673437] Judul Karya.m4a/.mp3" else "Format: RJ01673437.m4a/.mp3")
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
                    supportingContent = { Text("$parallelConn koneksi simultan (High-speed multi-thread)") },
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
                    supportingContent = { Text("High-Speed Native Engine") },
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
            title = { Text("Pilih Lokasi Penyimpanan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pilih folder untuk menyimpan file audio:")

                    Button(
                        onClick = {
                            showFolderDialog = false
                            dirPickerLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Folder dari File Manager")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.setDownloadDir("/storage/emulated/0/Download/JapaneseASMR")
                            showFolderDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Download/JapaneseASMR (Standar)")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.setDownloadDir("/storage/emulated/0/Music/JapaneseASMR")
                            showFolderDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Music/JapaneseASMR")
                    }

                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = { customPathInput = it },
                        label = { Text("Atau edit path manual") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
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
                    Text("Simpan")
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
            title = { Text("Pilih Jumlah Koneksi") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEach { count ->
                        val isSelected = count == parallelConn
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setParallelConnections(count)
                                        showConnectionDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$count Koneksi Simultan" + if (count == 16) " (Disarankan)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        ThemeMode.SYSTEM to "Mengikuti Sistem",
                        ThemeMode.DARK to "Gelap (Dracula / Dark)",
                        ThemeMode.LIGHT to "Terang (Light)"
                    ).forEach { (mode, label) ->
                        val isSelected = mode == themeMode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setThemeMode(mode)
                                        showThemeDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
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
