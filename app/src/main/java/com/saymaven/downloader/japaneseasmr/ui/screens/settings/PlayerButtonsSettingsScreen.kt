package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerButtonsSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val rawOrder by viewModel.playerButtonOrder.collectAsState()
    val buttonList = remember(rawOrder) {
        val allValidKeys = listOf("repeat", "sleep_timer", "chapters", "translate", "shuffle", "speed", "volume", "bookmark")
        val parsed = rawOrder.split(",").map { it.trim() }.filter { it in allValidKeys }.distinct().toMutableList()
        allValidKeys.forEach { key ->
            if (!parsed.contains(key)) parsed.add(key)
        }
        parsed
    }

    @Composable
    fun getButtonLabel(btnKey: String): String {
        return when (btnKey) {
            "repeat" -> stringResource(R.string.player_action_repeat)
            "sleep_timer" -> stringResource(R.string.player_timer_dialog_title)
            "chapters" -> stringResource(R.string.player_action_chapters)
            "translate" -> stringResource(R.string.player_action_translate)
            "shuffle" -> stringResource(R.string.player_action_shuffle)
            "speed" -> stringResource(R.string.player_speed_dialog_title)
            "volume" -> stringResource(R.string.settings_audio_gain)
            "bookmark" -> stringResource(R.string.browse_action_add_to_playlist)
            else -> btnKey.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in buttonList.indices && toIndex in buttonList.indices && fromIndex != toIndex) {
            val item = buttonList.removeAt(fromIndex)
            buttonList.add(toIndex, item)
            viewModel.setPlayerButtonOrder(buttonList.joinToString(","))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_player_buttons),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.setPlayerButtonOrder("repeat,sleep_timer,chapters,translate,shuffle,speed,volume,bookmark")
                    }) {
                        Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.settings_restore_defaults))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_player_buttons_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(buttonList) { index, btnKey ->
                    val isMainBar = index < 5
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMainBar) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isMainBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMainBar) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = getButtonLabel(btnKey),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isMainBar) stringResource(R.string.settings_player_buttons_row1) else stringResource(R.string.settings_player_buttons_backup),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMainBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { moveItem(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                            }

                            IconButton(
                                onClick = { moveItem(index, index + 1) },
                                enabled = index < buttonList.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                            }
                        }
                    }
                }
            }
        }
    }
}
