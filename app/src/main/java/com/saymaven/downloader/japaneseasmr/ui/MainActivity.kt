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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import com.saymaven.downloader.japaneseasmr.ui.screens.playlist.PlaylistScreen
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.saymaven.downloader.japaneseasmr.data.model.DownloadQueueItem
import com.saymaven.downloader.japaneseasmr.service.DownloadService
import com.saymaven.downloader.japaneseasmr.service.StorageSyncManager
import com.saymaven.downloader.japaneseasmr.service.UsbDacManager
import com.saymaven.downloader.japaneseasmr.ui.components.BottomNavBar
import com.saymaven.downloader.japaneseasmr.ui.components.FloatingVolumeHud
import com.saymaven.downloader.japaneseasmr.ui.components.FloatingLyricsBar
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
import com.saymaven.downloader.japaneseasmr.ui.screens.browse.AsmrOneBrowseScreen
import com.saymaven.downloader.japaneseasmr.ui.screens.browse.AsmrOneBrowseViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.util.LocaleHelper
import com.saymaven.downloader.japaneseasmr.ui.theme.JapaneseASMRTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        var activeTab: NavTab = NavTab.HOME
        var isColdLaunch: Boolean = true
    }

    private val queueViewModel: QueueViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val browseViewModel: AsmrOneBrowseViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        val initialLang = com.saymaven.downloader.japaneseasmr.data.local.PreferencesManager.getFastCachedLanguage(newBase)
        val localizedContext = LocaleHelper.applyLocale(newBase, initialLang)
        super.attachBaseContext(localizedContext)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        window.decorView.post {
            checkAndRequestPermissions()
            CoroutineScope(Dispatchers.IO).launch {
                delay(2500)
                StorageSyncManager.syncStorageWithDatabase(this@MainActivity)
            }
        }

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsState()
            val colorPalette by settingsViewModel.colorPalette.collectAsState()
            val appLanguage by settingsViewModel.appLanguage.collectAsState()

            val baseContext = LocalContext.current
            val localizedConfig = remember(appLanguage) {
                LocaleHelper.getLocalizedConfiguration(baseContext, appLanguage)
            }
            val localizedContext = remember(appLanguage) {
                LocaleHelper.applyLocale(baseContext, appLanguage)
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext
            ) {
                val initialTab = remember {
                    if (isColdLaunch) {
                        isColdLaunch = false
                        activeTab = NavTab.HOME
                        NavTab.HOME
                    } else {
                        activeTab
                    }
                }
                var currentTab by remember { mutableStateOf(initialTab) }
                var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
                val visitedTabs = remember { mutableStateListOf(initialTab) }
                LaunchedEffect(currentTab) {
                    activeTab = currentTab
                    if (!visitedTabs.contains(currentTab)) {
                        visitedTabs.add(currentTab)
                    }
                }

                // Pre-warm sisa tab di background secara bertahap untuk transisi instan 0ms
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(350)
                    listOf(NavTab.BROWSE, NavTab.QUEUE, NavTab.HISTORY, NavTab.SETTINGS).forEach { tab ->
                        if (!visitedTabs.contains(tab)) {
                            visitedTabs.add(tab)
                            kotlinx.coroutines.delay(120)
                        }
                    }
                }

                JapaneseASMRTheme(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    colorPalette = colorPalette
                ) {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val drawerScope = rememberCoroutineScope()

                BackHandler(enabled = drawerState.isOpen) {
                    drawerScope.launch { drawerState.close() }
                }

                BackHandler(enabled = !drawerState.isOpen && currentTab == NavTab.SETTINGS) {
                    currentTab = NavTab.HOME
                }

                val drawerItemShape = RoundedCornerShape(10.dp)
                val drawerItemColors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedContainerColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface
                )

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = !isPlayerExpanded,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(300.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            drawerContentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 20.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.drawer_title_app),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.drawer_subtitle_version),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.drawer_audio_engine_status),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.drawer_item_local)) },
                                selected = currentTab == NavTab.HOME,
                                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                                shape = drawerItemShape,
                                colors = drawerItemColors,
                                onClick = {
                                    currentTab = NavTab.HOME
                                    drawerScope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )

                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.drawer_item_browse)) },
                                selected = currentTab == NavTab.BROWSE,
                                icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                                shape = drawerItemShape,
                                colors = drawerItemColors,
                                onClick = {
                                    if (!visitedTabs.contains(NavTab.BROWSE)) {
                                        visitedTabs.add(NavTab.BROWSE)
                                    }
                                    currentTab = NavTab.BROWSE
                                    drawerScope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )

                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.drawer_item_queue)) },
                                selected = currentTab == NavTab.QUEUE,
                                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                                shape = drawerItemShape,
                                colors = drawerItemColors,
                                onClick = {
                                    if (!visitedTabs.contains(NavTab.QUEUE)) {
                                        visitedTabs.add(NavTab.QUEUE)
                                    }
                                    currentTab = NavTab.QUEUE
                                    drawerScope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )

                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.drawer_item_history)) },
                                selected = currentTab == NavTab.HISTORY,
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                shape = drawerItemShape,
                                colors = drawerItemColors,
                                onClick = {
                                    if (!visitedTabs.contains(NavTab.HISTORY)) {
                                        visitedTabs.add(NavTab.HISTORY)
                                    }
                                    currentTab = NavTab.HISTORY
                                    drawerScope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )

                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.drawer_item_settings)) },
                                selected = currentTab == NavTab.SETTINGS,
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                shape = drawerItemShape,
                                colors = drawerItemColors,
                                onClick = {
                                    if (!visitedTabs.contains(NavTab.SETTINGS)) {
                                        visitedTabs.add(NavTab.SETTINGS)
                                    }
                                    currentTab = NavTab.SETTINGS
                                    drawerScope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                // Floating Lyrics & Mini Player (Docked across all tabs unless full player is expanded)
                                if (!isPlayerExpanded) {
                                    FloatingLyricsBar(viewModel = playerViewModel)
                                    MiniPlayer(
                                        viewModel = playerViewModel,
                                        onExpand = { isPlayerExpanded = true }
                                    )
                                }

                                BottomNavBar(
                                    currentTab = currentTab,
                                    onTabSelected = { tab ->
                                        if (!visitedTabs.contains(tab)) {
                                            visitedTabs.add(tab)
                                        }
                                        currentTab = tab
                                    }
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
                            // Tab 0: Home / Daftar Putar Koleksi (HiBy Music Style)
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
                                PlaylistScreen(
                                    viewModel = playerViewModel,
                                    onOpenPlayer = { isPlayerExpanded = true },
                                    onOpenDrawer = { drawerScope.launch { drawerState.open() } }
                                )
                            }

                            // Tab Browse: asmr.one Explorer
                            if (visitedTabs.contains(NavTab.BROWSE)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(if (currentTab == NavTab.BROWSE) 1f else 0f)
                                        .graphicsLayer {
                                            val active = (currentTab == NavTab.BROWSE)
                                            alpha = if (active) 1f else 0f
                                            translationX = if (active) 0f else 99999f
                                        }
                                ) {
                                    AsmrOneBrowseScreen(
                                        viewModel = browseViewModel,
                                        playerViewModel = playerViewModel,
                                        onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                                        onOpenPlayer = { isPlayerExpanded = true },
                                        onSelectWorkForDownload = { rjid ->
                                            queueViewModel.onInputChanged(rjid)
                                            currentTab = NavTab.QUEUE
                                        }
                                    )
                                }
                            }

                            // Tab 1: Unduhan / Queue
                            if (visitedTabs.contains(NavTab.QUEUE)) {
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
                                    QueueScreen(
                                        viewModel = queueViewModel,
                                        onOpenDrawer = { drawerScope.launch { drawerState.open() } }
                                    )
                                }
                            }

                            // Tab 2: Riwayat / History
                            if (visitedTabs.contains(NavTab.HISTORY)) {
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
                                        onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                                        onPlayTrack = { historyEntity ->
                                            playerViewModel.playLocalTrack(historyEntity)
                                            isPlayerExpanded = true
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
                                                Toast.makeText(this@MainActivity, getString(R.string.player_toast_download_starting, historyEntity.rjid), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, getString(R.string.player_toast_download_enqueued, historyEntity.rjid), Toast.LENGTH_SHORT).show()
                                            }
                                            currentTab = NavTab.QUEUE
                                        }
                                    )
                                }
                            }

                            // Tab 3: Pengaturan / Settings
                            if (visitedTabs.contains(NavTab.SETTINGS)) {
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
                                    SettingsScreen(
                                        viewModel = settingsViewModel,
                                        onOpenDrawer = { drawerScope.launch { drawerState.open() } }
                                    )
                                }
                            }
                        }
                    }

                    // Expanded Full Player Layer (HiBy Music Style slide-up drawer)
                    AnimatedVisibility(
                        visible = isPlayerExpanded,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(300)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(250)),
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(50f)
                    ) {
                        BackHandler(enabled = isPlayerExpanded) {
                            isPlayerExpanded = false
                        }
                        PlayerScreen(
                            viewModel = playerViewModel,
                            onCollapse = { isPlayerExpanded = false }
                        )
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

    override fun onResume() {
        super.onResume()
    }
}
