package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.BuildConfig
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current

    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val colorPalette by viewModel.colorPalette.collectAsState()
    val downloadDir by viewModel.downloadDir.collectAsState()
    val parallelConn by viewModel.parallelConnections.collectAsState()
    val autoClipboard by viewModel.autoClipboard.collectAsState()
    val useDetailedFilename by viewModel.useDetailedFilename.collectAsState()

    // Audio Player States
    val exclusiveUsbDac by viewModel.exclusiveUsbDac.collectAsState()
    val audioFocus by viewModel.audioFocus.collectAsState()
    val pauseOnUnplug by viewModel.pauseOnUnplug.collectAsState()
    val skipSilence by viewModel.skipSilence.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val autoResume by viewModel.autoResume.collectAsState()
    val defaultSpeed by viewModel.defaultSpeed.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showThreadDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
            }
            val resolved = com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper.resolvePhysicalPathFromUri(context, uri.toString()) ?: uri.toString()
            viewModel.setDownloadDir(resolved)
            Toast.makeText(context, "Folder unduhan berhasil diubah!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Pengaturan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // ================= 1. PENGATURAN PEMUTAR AUDIO =================
        SettingsCategoryHeader(title = "Pemutar Audio Hi-Res", icon = Icons.AutoMirrored.Filled.VolumeUp)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Mode Eksklusif USB DAC", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Klaim hardware DAC langsung untuk output murni bit-perfect", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = exclusiveUsbDac,
                            onCheckedChange = { viewModel.setExclusiveUsbDac(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Fokus Audio Eksklusif", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Jeda otomatis jika aplikasi lain memutar audio", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Hearing, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = audioFocus,
                            onCheckedChange = { viewModel.setAudioFocus(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Jeda Saat Headset Dicabut", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Mencegah audio bocor ke speaker HP saat kabel/DAC lepas", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = pauseOnUnplug,
                            onCheckedChange = { viewModel.setPauseOnUnplug(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Lewati Bagian Hening", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Otomatis mempercepat bagian tanpa suara", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = skipSilence,
                            onCheckedChange = { viewModel.setSkipSilence(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Layar Tetap Menyala (Keep Screen On)", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Mencegah layar tidur saat berada di tab Pemutar", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.StayCurrentPortrait, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { viewModel.setKeepScreenOn(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Lanjutkan Pemutaran (Auto-Resume)", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Ingat posisi track, durasi, repeat, shuffle & status waktu saat buka ulang app", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = autoResume,
                            onCheckedChange = { viewModel.setAutoResume(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Kecepatan Putar Default", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${defaultSpeed}x Normal", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showSpeedDialog = true }
                )
            }
        }

        // ================= 2. PENGATURAN UNDUHAN =================
        SettingsCategoryHeader(title = "Pengunduhan", icon = Icons.Default.Download)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Folder Unduhan", fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        val physicalPath = com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper.resolvePhysicalPathFromUri(context, downloadDir)
                            ?: DownloadService.getDefaultDownloadDirectory().absolutePath
                        val cleanDisplayPath = com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper.formatPathForDisplay(physicalPath)
                        Text(
                            cleanDisplayPath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        FilledTonalButton(onClick = { dirPickerLauncher.launch(null) }) {
                            Text("Ubah")
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Koneksi Paralel", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("$parallelConn thread simultan (HLS & MP3)", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showThreadDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Deteksi Otomatis Clipboard", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Otomatis isi input jika ada kode RJ di clipboard", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = autoClipboard,
                            onCheckedChange = { viewModel.setAutoClipboard(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Nama File Lengkap", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Sertakan judul karya pada nama file audio", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = useDetailedFilename,
                            onCheckedChange = { viewModel.setUseDetailedFilename(it) }
                        )
                    }
                )
            }
        }

        // ================= 3. TAMPILAN & TEMA (DENGAN 36 COLOR PALETTE PREVIEWS) =================
        SettingsCategoryHeader(title = "Tampilan & Warna", icon = Icons.Default.Palette)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Mode Tema", fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            when (themeMode) {
                                ThemeMode.DARK -> "Gelap (Dark Mode)"
                                ThemeMode.LIGHT -> "Terang (Light Mode)"
                                ThemeMode.SYSTEM -> "Mengikuti Sistem"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Warna Dinamis (Material You)", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Warna aksen menyesuaikan wallpaper HP Android", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { viewModel.setDynamicColor(it) }
                            )
                        }
                    )
                }

                if (!dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Palet Warna Aplikasi (36 Warna)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = colorPalette.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal Palettes Preview Carousel with Exact 4-Palettes = 1 Dot indicator
                        ColorPaletteCarousel(
                            selectedPalette = colorPalette,
                            onSelectPalette = { viewModel.setColorPalette(it) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Bahasa Aplikasi", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Bahasa Indonesia", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
            }
        }

        // ================= 4. TENTANG & INFORMASI =================
        SettingsCategoryHeader(title = "Tentang Aplikasi", icon = Icons.Default.Info)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Pengembang", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("SayMaven (GitHub)", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SayMaven"))
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Arsitektur", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("High-Speed Native Engine & Jetpack Media3", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Versi Aplikasi", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Catatan Rilis (Changelog)", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Riwayat pembaruan & fitur baru", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showChangelogDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Panduan Penggunaan", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Cara penggunaan & tips fitur", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showGuideDialog = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Laporkan Masalah / Bug", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("GitHub Issues", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SayMaven/japaneseasmr-android/issues"))
                        context.startActivity(intent)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                ListItem(
                    headlineContent = { Text("Repository GitHub", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("SayMaven/japaneseasmr-android", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SayMaven/japaneseasmr-android"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }

    // Dialogs
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Pilih Mode Tema") },
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                when (mode) {
                                    ThemeMode.DARK -> "Gelap (Dark Mode)"
                                    ThemeMode.LIGHT -> "Terang (Light Mode)"
                                    ThemeMode.SYSTEM -> "Mengikuti Sistem"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Tutup") }
            }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Kecepatan Putar Default") },
            text = {
                Column {
                    speeds.forEach { speed ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDefaultSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = defaultSpeed == speed, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${speed}x ${if (speed == 1.0f) "(Normal)" else ""}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Tutup") }
            }
        )
    }

    if (showThreadDialog) {
        val options = listOf(4, 8, 16, 24, 32)
        AlertDialog(
            onDismissRequest = { showThreadDialog = false },
            title = { Text("Jumlah Koneksi Paralel") },
            text = {
                Column {
                    options.forEach { opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setParallelConnections(opt)
                                    showThreadDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = parallelConn == opt, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("$opt Koneksi ${if (opt == 16) "(Rekomendasi)" else ""}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThreadDialog = false }) { Text("Tutup") }
            }
        )
    }

    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = { Text("Catatan Rilis (Changelog)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Versi 1.2.0", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Sleep Timer (Pengatur Waktu Tidur) terpadu dengan preset & kelipatan 5 menit.", style = MaterialTheme.typography.bodySmall)
                    Text("• Sinkronisasi hitung mundur timer otomatis terhenti saat audio di-pause.", style = MaterialTheme.typography.bodySmall)
                    Text("• Optimasi performa scroll riwayat & koleksi hingga 120 FPS tanpa frame drop.", style = MaterialTheme.typography.bodySmall)
                    Text("• Floating Hardware Volume HUD melayang ultra-ramping & solid.", style = MaterialTheme.typography.bodySmall)
                    Text("• Prioritas Output USB DAC dengan routing audio jernih.", style = MaterialTheme.typography.bodySmall)
                    Text("• Antrean unduh dinamis & berkelanjutan otomatis hingga tuntas.", style = MaterialTheme.typography.bodySmall)
                    Text("• Tampilan path folder penyimpanan ringkas & sinkron.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Versi 1.1.0", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Fitur Drag & Drop Reorder interaktif & halus di daftar putar koleksi.", style = MaterialTheme.typography.bodySmall)
                    Text("• Filter otomatis file hilang di daftar putar koleksi.", style = MaterialTheme.typography.bodySmall)
                    Text("• Auto-Sync Penyimpanan Realtime saat audio masuk/berpindah.", style = MaterialTheme.typography.bodySmall)
                    Text("• Mode Eksklusif USB DAC dengan direct USB hardware claim.", style = MaterialTheme.typography.bodySmall)
                    Text("• Mode Format Waktu Persisten & 36 Palet Warna Kustom.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) { Text("Tutup") }
            }
        )
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = { Text("Panduan Penggunaan") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("1. Mengunduh Karya:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Masukkan kode RJ di tab Home, lalu tekan Unduh atau + Antrean.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("2. Memutar Koleksi:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Buka tab Riwayat lalu tekan karya yang sudah selesai diunduh.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuideDialog = false }) { Text("Tutup") }
            }
        )
    }
}

@Composable
fun ColorPaletteCarousel(
    selectedPalette: ColorPalette,
    onSelectPalette: (ColorPalette) -> Unit
) {
    val palettes = remember { ColorPalette.values().toList() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 1 Page Indicator = Setiap 4 Palet Warna (36 Palet = 9 Titik Indikator)
    val totalPages = remember(palettes.size) { (palettes.size + 3) / 4 }

    // Hitung dot index secara dinamis: bergeser setiap 4 warna dilewati
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
                        // 3-Segment Preview Circle (Top Half: Primary, Bottom Left: Secondary, Bottom Right: Tertiary)
                        Canvas(modifier = Modifier.size(50.dp)) {
                            // Top Half (Primary)
                            drawArc(
                                color = palette.primary,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = true
                            )
                            // Bottom Left Quadrant (Secondary)
                            drawArc(
                                color = palette.secondary,
                                startAngle = 90f,
                                sweepAngle = 90f,
                                useCenter = true
                            )
                            // Bottom Right Quadrant (Tertiary)
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

        // Indikator Titik: 1 Titik per 4 Warna (Total 9 Titik untuk 36 Palet)
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
