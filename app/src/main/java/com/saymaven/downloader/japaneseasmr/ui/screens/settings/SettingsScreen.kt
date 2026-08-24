package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
import java.io.File

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

    // Player Settings
    val exclusiveAudioFocus by viewModel.exclusiveAudioFocus.collectAsState()
    val pauseOnUnplug by viewModel.pauseOnUnplug.collectAsState()
    val skipSilence by viewModel.skipSilence.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val autoResume by viewModel.autoResume.collectAsState()
    val defaultSpeed by viewModel.defaultSpeed.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showConnDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showReadmeDialog by remember { mutableStateOf(false) }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val docId = it.path ?: ""
            val rawPath = if (docId.contains(":")) {
                val split = docId.split(":")
                if (split.size > 1) {
                    val relativePath = split[1]
                    "/storage/emulated/0/$relativePath"
                } else docId
            } else docId
            viewModel.setDownloadDir(rawPath)
        }
    }

    val defaultDownloadPath = remember {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "JapaneseASMR").absolutePath
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Pengaturan",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ================= KATEGORI 1: UMUM =================
        item {
            SettingsCategoryHeader(title = "Umum", icon = Icons.Default.Folder)
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Lokasi Unduhan") },
                        supportingContent = {
                            Text(
                                text = downloadDir ?: defaultDownloadPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            FilledTonalButton(
                                onClick = { openDocumentTreeLauncher.launch(null) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Ubah", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Gunakan Judul Karya") },
                        supportingContent = {
                            Text(
                                if (useDetailedFilename) "Nama file: [RJxxxxxx] Judul.m4a" else "Nama file: RJxxxxxx.m4a",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(
                                checked = useDetailedFilename,
                                onCheckedChange = { viewModel.setUseDetailedFilename(it) }
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Deteksi Otomatis Clipboard") },
                        supportingContent = { Text("Otomatis isi kode RJ baru saat disalin", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(
                                checked = autoClipboard,
                                onCheckedChange = { viewModel.setAutoClipboard(it) }
                            )
                        }
                    )
                }
            }
        }

        // ================= KATEGORI 2: PEMUTAR AUDIO (BARU) =================
        item {
            SettingsCategoryHeader(title = "Pemutar Audio", icon = Icons.Default.Headphones)
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Fokus Audio Eksklusif") },
                        supportingContent = { Text("Jeda penuh saat ada audio/notifikasi lain agar fokus tidak terpecah", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.VolumeOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(
                                checked = exclusiveAudioFocus,
                                onCheckedChange = { viewModel.setExclusiveAudioFocus(it) }
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Jeda Saat Headset Terputus") },
                        supportingContent = { Text("Otomatis pause saat earphone kabel atau TWS Bluetooth terputus", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.HeadsetOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(
                                checked = pauseOnUnplug,
                                onCheckedChange = { viewModel.setPauseOnUnplug(it) }
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Lewati Jeda Hening (Skip Silence)") },
                        supportingContent = { Text("Otomatis mempercepat bagian hening tanpa merusak tempo dialog", style = MaterialTheme.typography.bodySmall) },
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
                        headlineContent = { Text("Layar Tetap Menyala") },
                        supportingContent = { Text("Mencegah layar HP mati/terkunci saat berada di tab pemutar", style = MaterialTheme.typography.bodySmall) },
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
                        headlineContent = { Text("Lanjutkan Pemutaran Otomatis") },
                        supportingContent = { Text("Memuat track dan posisi detik terakhir saat aplikasi dibuka", style = MaterialTheme.typography.bodySmall) },
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
                        modifier = Modifier.clickable { showSpeedDialog = true },
                        headlineContent = { Text("Kecepatan Putar Bawaan") },
                        supportingContent = { Text("Kecepatan awal saat memutar audio karya", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.SlowMotionVideo, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Text(
                                text = "${defaultSpeed}x",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }

        // ================= KATEGORI 3: JARINGAN =================
        item {
            SettingsCategoryHeader(title = "Jaringan & Unduhan", icon = Icons.Default.Speed)
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                ListItem(
                    modifier = Modifier.clickable { showConnDialog = true },
                    headlineContent = { Text("Koneksi Paralel Multi-Thread") },
                    supportingContent = { Text("$parallelConn thread simultan (HLS & Range MP3)", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Text(
                            "$parallelConn Thread",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }

        // ================= KATEGORI 4: TAMPILAN & BAHASA =================
        item {
            SettingsCategoryHeader(title = "Tampilan & Bahasa", icon = Icons.Default.Palette)
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        modifier = Modifier.clickable { showThemeDialog = true },
                        headlineContent = { Text("Mode Tema") },
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
                        leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Warna Dinamis (Material You)") },
                        supportingContent = { Text("Menyesuaikan warna aksen wallpaper HP (Android 12+)", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { viewModel.setDynamicColor(it) }
                            )
                        }
                    )

                    if (!dynamicColor) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ListItem(
                            modifier = Modifier.clickable { showPaletteDialog = true },
                            headlineContent = { Text("Palet Warna Aksen") },
                            supportingContent = { Text(colorPalette.title, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = { Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        modifier = Modifier.clickable { showLanguageDialog = true },
                        headlineContent = { Text("Bahasa (Language)") },
                        supportingContent = { Text("Bahasa Indonesia", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
        }

        // ================= KATEGORI 5: TENTANG =================
        item {
            SettingsCategoryHeader(title = "Tentang Aplikasi", icon = Icons.Default.Info)
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SayMaven"))
                            context.startActivity(intent)
                        },
                        headlineContent = { Text("Pengembang (Developer)") },
                        supportingContent = { Text("SayMaven (Ketuk untuk buka profil GitHub)", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Engine Unduhan") },
                        supportingContent = { Text("High-Speed Native Engine", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = { Text("Versi Aplikasi") },
                        supportingContent = { Text("v1.1.0 (Release Build)", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        modifier = Modifier.clickable { showChangelogDialog = true },
                        headlineContent = { Text("Log Versi (Changelog)") },
                        supportingContent = { Text("Lihat riwayat pembaruan & fitur baru", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        modifier = Modifier.clickable { showReadmeDialog = true },
                        headlineContent = { Text("Baca Panduan & Fitur (README)") },
                        supportingContent = { Text("Ringkasan fitur lengkap & panduan penggunaan", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SayMaven/japaneseasmr-android/issues"))
                            context.startActivity(intent)
                        },
                        headlineContent = { Text("Laporkan Isu / Bug") },
                        supportingContent = { Text("Buka GitHub Issues untuk saran & pelaporan bug", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SayMaven/japaneseasmr-android"))
                            context.startActivity(intent)
                        },
                        headlineContent = { Text("Repositori GitHub") },
                        supportingContent = { Text("SayMaven/japaneseasmr-android", style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
        }
    }

    // ================= DIALOGS =================

    // 1. Theme Dialog
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
                                .padding(vertical = 10.dp)
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

    // 2. Color Palette Dialog
    if (showPaletteDialog) {
        AlertDialog(
            onDismissRequest = { showPaletteDialog = false },
            title = { Text("Pilih Palet Warna") },
            text = {
                Column {
                    ColorPalette.values().forEach { pal ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setColorPalette(pal)
                                    showPaletteDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(selected = colorPalette == pal, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(pal.title)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPaletteDialog = false }) { Text("Tutup") }
            }
        )
    }

    // 3. Playback Speed Dialog (Baru)
    if (showSpeedDialog) {
        val speeds = listOf(0.75f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Kecepatan Putar Bawaan") },
            text = {
                Column {
                    speeds.forEach { spd ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDefaultSpeed(spd)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(selected = defaultSpeed == spd, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (spd == 1.0f) "1.0x (Normal)" else "${spd}x")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Tutup") }
            }
        )
    }

    // 4. Parallel Connections Dialog
    if (showConnDialog) {
        val options = listOf(4, 8, 16, 24, 32)
        AlertDialog(
            onDismissRequest = { showConnDialog = false },
            title = { Text("Pilih Jumlah Koneksi") },
            text = {
                Column {
                    options.forEach { conn ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setParallelConnections(conn)
                                    showConnDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(selected = parallelConn == conn, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("$conn Thread ${if (conn == 16) "(Rekomendasi)" else ""}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnDialog = false }) { Text("Tutup") }
            }
        )
    }

    // 5. Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Pilih Bahasa") },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageDialog = false }
                            .padding(vertical = 10.dp)
                    ) {
                        RadioButton(selected = true, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Bahasa Indonesia (Default)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Tutup") }
            }
        )
    }

    // 6. Changelog Dialog
    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = { Text("Riwayat Pembaruan (Changelog)") },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("Versi 1.1.0 (Pembaruan Fitur Pemutar)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Grup Pengaturan Pemutar Audio lengkap (Fokus Audio Eksklusif, Jeda Saat Headset Dilepas, Lewati Jeda Hening, Layar Tetap Menyala, Kecepatan Putar).", style = MaterialTheme.typography.bodySmall)
                    Text("• Penyimpanan permanen urutan daftar putar drag-and-drop.", style = MaterialTheme.typography.bodySmall)
                    Text("• Tombol toggle sembunyikan/tampilkan konsol live log dengan ikon dinamis (>_ / <>).", style = MaterialTheme.typography.bodySmall)
                    Text("• Pemulihan otomatis lagu lama di penyimpanan saat aplikasi dibuka.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Versi 1.0.0 (Rilis Awal)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text("• Engine unduhan native multi-thread paralel (4-32 koneksi).", style = MaterialTheme.typography.bodySmall)
                    Text("• Pemutar audio native dengan kontrol MediaSession notifikasi & lockscreen.", style = MaterialTheme.typography.bodySmall)
                    Text("• Penanaman cover art resolusi tinggi dan ID3 metadata otomatis.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) { Text("Tutup") }
            }
        )
    }

    // 7. Readme Dialog
    if (showReadmeDialog) {
        AlertDialog(
            onDismissRequest = { showReadmeDialog = false },
            title = { Text("Panduan Penggunaan") },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("1. Mengunduh Karya:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Masukkan kode RJ (misal: RJ337874) di tab Home, lalu tekan 'Unduh' untuk mengunduh langsung atau '+ Antrean' untuk menumpuk unduhan.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("2. Memutar Audio:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Buka tab Riwayat atau Pemutar, pilih lagu yang ingin didengarkan. Geser ikon = di daftar putar untuk mengatur urutan lagu.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("3. Audio Eksklusif & Headset:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Gunakan menu Pengaturan -> Pemutar Audio untuk mengatur fokus audio eksklusif dan auto-pause saat headset terputus.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showReadmeDialog = false }) { Text("Tutup") }
            }
        )
    }
}

@Composable
fun SettingsCategoryHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
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
