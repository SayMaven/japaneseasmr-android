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

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val customDir by viewModel.downloadDir.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showCacheClearedSnackbar by remember { mutableStateOf(false) }

    val activeDownloadPath = remember(customDir) {
        if (!customDir.isNullOrBlank()) customDir!! else DownloadService.getDownloadDirectory(context).absolutePath
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
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

        // Tampilan & Tema Card
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

        // Penyimpanan & Unduhan
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
                            Icon(Icons.Default.OpenInNew, contentDescription = "Buka Folder")
                        }
                    }
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Reset ke Folder Standar Musik") },
                    supportingContent = { Text("Simpan di folder Music/JapaneseASMR perangkat") },
                    leadingContent = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.setDownloadDir("")
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

        // Tentang Aplikasi
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
                    supportingContent = { Text("Versi 1.0.1 (Native Android)") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
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
