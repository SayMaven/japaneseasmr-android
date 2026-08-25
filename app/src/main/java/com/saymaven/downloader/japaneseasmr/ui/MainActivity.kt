package com.saymaven.downloader.japaneseasmr.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.service.StorageSyncManager
import com.saymaven.downloader.japaneseasmr.service.UsbDacManager
import com.saymaven.downloader.japaneseasmr.ui.components.BottomNavBar
import com.saymaven.downloader.japaneseasmr.ui.components.FloatingVolumeHud
import com.saymaven.downloader.japaneseasmr.ui.components.NavTab
import com.saymaven.downloader.japaneseasmr.ui.screens.history.HistoryScreen
import com.saymaven.downloader.japaneseasmr.ui.screens.history.HistoryViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerScreen
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.queue.QueueScreen
import com.saymaven.downloader.japaneseasmr.ui.screens.queue.QueueViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.settings.SettingsScreen
import com.saymaven.downloader.japaneseasmr.ui.screens.settings.SettingsViewModel
import com.saymaven.downloader.japaneseasmr.ui.theme.JapaneseASMRTheme

class MainActivity : ComponentActivity() {

    private val queueViewModel: QueueViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()
        StorageSyncManager.syncStorageWithDatabase(this)

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsState()
            val colorPalette by settingsViewModel.colorPalette.collectAsState()

            val navPrefs = remember { getSharedPreferences("app_nav_state", Context.MODE_PRIVATE) }
            val savedTabName = remember { navPrefs.getString("last_tab", NavTab.PLAYER.name) ?: NavTab.PLAYER.name }
            val initialTab = remember {
                try { NavTab.valueOf(savedTabName) } catch (e: Exception) { NavTab.PLAYER }
            }
            var currentTab by rememberSaveable { mutableStateOf(initialTab) }

            JapaneseASMRTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                colorPalette = colorPalette
            ) {
                Scaffold(
                    bottomBar = {
                        BottomNavBar(
                            currentTab = currentTab,
                            onTabSelected = { tab ->
                                currentTab = tab
                                navPrefs.edit().putString("last_tab", tab.name).apply()
                            }
                        )
                    }
                ) { innerPadding ->
                    // Mempertahankan semua tab di memori dengan GPU RenderNode switching
                    // Menghilangkan 100% delay perpindahan tab (0.0ms instant tab switching)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Tab 0: Home / Player (Pemutar)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (currentTab == NavTab.PLAYER) 1f else 0f)
                                .graphicsLayer {
                                    val active = (currentTab == NavTab.PLAYER)
                                    alpha = if (active) 1f else 0f
                                    translationX = if (active) 0f else 99999f
                                }
                        ) {
                            PlayerScreen(viewModel = playerViewModel)
                        }

                        // Tab 1: Riwayat / History
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (currentTab == NavTab.HISTORY) 1f else 0f)
                                .graphicsLayer {
                                    val active = (currentTab == NavTab.HISTORY)
                                    alpha = if (active) 1f else 0f
                                    translationX = if (active) 0f else 99999f
                                }
                        ) {
                            HistoryScreen(
                                viewModel = historyViewModel,
                                onPlayTrack = { historyEntity ->
                                    playerViewModel.playLocalTrack(historyEntity)
                                    currentTab = NavTab.PLAYER
                                    navPrefs.edit().putString("last_tab", NavTab.PLAYER.name).apply()
                                },
                                onRedownload = { historyEntity ->
                                    val item = DownloadQueueItem(
                                        rjid = historyEntity.rjid,
                                        title = historyEntity.title,
                                        cv = historyEntity.cv,
                                        circle = historyEntity.circle,
                                        genre = historyEntity.genre,
                                        ageRating = historyEntity.ageRating,
                                        coverUrl = historyEntity.coverUrl
                                    )
                                    DownloadService.enqueue(listOf(item))
                                    queueViewModel.onInputChanged(historyEntity.rjid)

                                    if (!DownloadService.isDownloading.value) {
                                        DownloadService.startDownload(this@MainActivity)
                                        Toast.makeText(this@MainActivity, "Memulai unduhan [${historyEntity.rjid}]...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@MainActivity, "[${historyEntity.rjid}] ditambahkan ke antrean unduh.", Toast.LENGTH_SHORT).show()
                                    }
                                    currentTab = NavTab.QUEUE
                                    navPrefs.edit().putString("last_tab", NavTab.QUEUE.name).apply()
                                }
                            )
                        }

                        // Tab 2: Unduhan / Queue
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (currentTab == NavTab.QUEUE) 1f else 0f)
                                .graphicsLayer {
                                    val active = (currentTab == NavTab.QUEUE)
                                    alpha = if (active) 1f else 0f
                                    translationX = if (active) 0f else 99999f
                                }
                        ) {
                            QueueScreen(viewModel = queueViewModel)
                        }

                        // Tab 3: Pengaturan / Settings
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (currentTab == NavTab.SETTINGS) 1f else 0f)
                                .graphicsLayer {
                                    val active = (currentTab == NavTab.SETTINGS)
                                    alpha = if (active) 1f else 0f
                                    translationX = if (active) 0f else 99999f
                                }
                        ) {
                            SettingsScreen(viewModel = settingsViewModel)
                        }

                        // Floating Hardware Volume HUD (Appears when Volume Up/Down is pressed in Exclusive Mode)
                        FloatingVolumeHud(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(100f)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        StorageSyncManager.syncStorageWithDatabase(this)
        playerViewModel.refreshPlaylist()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (UsbDacManager.isExclusiveActivelyRunning()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    UsbDacManager.stepHardwareVolume(up = true)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    UsbDacManager.stepHardwareVolume(up = false)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (UsbDacManager.isExclusiveActivelyRunning()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
