package com.saymaven.downloader.japaneseasmr.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.ui.components.BottomNavBar
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

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsState()
            val colorPalette by settingsViewModel.colorPalette.collectAsState()

            var currentTab by remember { mutableStateOf(NavTab.QUEUE) }

            JapaneseASMRTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                colorPalette = colorPalette
            ) {
                Scaffold(
                    bottomBar = {
                        BottomNavBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            NavTab.QUEUE -> QueueScreen(viewModel = queueViewModel)
                            NavTab.HISTORY -> HistoryScreen(
                                viewModel = historyViewModel,
                                onPlayTrack = { historyEntity ->
                                    playerViewModel.playLocalTrack(historyEntity)
                                    currentTab = NavTab.PLAYER
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
                            NavTab.PLAYER -> PlayerScreen(viewModel = playerViewModel)
                            NavTab.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                        }
                    }
                }
            }
        }
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
