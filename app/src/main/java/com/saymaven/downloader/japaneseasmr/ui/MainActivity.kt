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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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
import com.saymaven.downloader.japaneseasmr.ui.components.MiniPlayer
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

    companion object {
        var activeTab: NavTab = NavTab.HOME
        var isColdLaunch: Boolean = true
    }

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

            var currentTab by remember {
                val initial = if (isColdLaunch) {
                    isColdLaunch = false
                    activeTab = NavTab.HOME
                    NavTab.HOME
                } else {
                    activeTab
                }
                mutableStateOf(initial)
            }

            LaunchedEffect(currentTab) {
                activeTab = currentTab
            }

            JapaneseASMRTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                colorPalette = colorPalette
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                // Floating Mini Player (Hanya muncul saat berada di luar tab Home / Pemutar)
                                if (currentTab != NavTab.HOME) {
                                    MiniPlayer(
                                        viewModel = playerViewModel,
                                        onExpand = { currentTab = NavTab.HOME }
                                    )
                                }

                                BottomNavBar(
                                    currentTab = currentTab,
                                    onTabSelected = { tab -> currentTab = tab }
                                )
                            }
                        }
                    ) { innerPadding ->
                        // Mempertahankan semua tab di memori dengan GPU RenderNode switching
                        // Menghilangkan 100% delay perpindahan tab (0.0ms instant tab switching)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            // Tab 0: Home / Pemutar Audio Penuh
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (currentTab == NavTab.HOME) 1f else 0f)
                                    .graphicsLayer {
                                        val active = (currentTab == NavTab.HOME)
                                        alpha = if (active) 1f else 0f
                                        translationX = if (active) 0f else 99999f
                                    }
                            ) {
                                PlayerScreen(viewModel = playerViewModel)
                            }

                            // Tab 1: Unduhan / Queue
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

                            // Tab 2: Riwayat / History
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
                                        currentTab = NavTab.HOME
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
                                    }
                                )
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
                        }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (UsbDacManager.dacState.value.isExclusiveActive) {
            val action = event.action
            val keyCode = event.keyCode
            if (action == KeyEvent.ACTION_DOWN) {
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
        }
        return super.dispatchKeyEvent(event)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
