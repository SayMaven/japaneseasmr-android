package com.saymaven.downloader.japaneseasmr.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode
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
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsState()

            var currentTab by remember { mutableStateOf(NavTab.QUEUE) }

            JapaneseASMRTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
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
