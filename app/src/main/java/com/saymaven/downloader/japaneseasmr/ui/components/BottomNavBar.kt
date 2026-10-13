package com.saymaven.downloader.japaneseasmr.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.res.stringResource
import com.saymaven.downloader.japaneseasmr.R

enum class NavTab(val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_player, Icons.Default.Home),
    QUEUE(R.string.tab_queue, Icons.Default.Download),
    HISTORY(R.string.tab_history, Icons.Default.History),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun BottomNavBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavTab.values().forEach { tab ->
            val labelText = stringResource(tab.titleRes)
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = labelText) },
                label = { Text(labelText) }
            )
        }
    }
}
