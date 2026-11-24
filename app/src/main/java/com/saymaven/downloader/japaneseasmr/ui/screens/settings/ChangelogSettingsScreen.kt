package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSettingsScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_changelog_title),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // v2.0.0
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v200_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v200_item1),
                    stringResource(R.string.settings_changelog_v200_item2),
                    stringResource(R.string.settings_changelog_v200_item3),
                    stringResource(R.string.settings_changelog_v200_item4),
                    stringResource(R.string.settings_changelog_v200_item5),
                    stringResource(R.string.settings_changelog_v200_item6),
                    stringResource(R.string.settings_changelog_v200_item7),
                    stringResource(R.string.settings_changelog_v200_item8)
                ),
                isLatest = true
            )

            // v1.4.3
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v143_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v143_item1),
                    stringResource(R.string.settings_changelog_v143_item2),
                    stringResource(R.string.settings_changelog_v143_item3),
                    stringResource(R.string.settings_changelog_v143_item4)
                )
            )

            // v1.4.2
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v142_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v142_item1),
                    stringResource(R.string.settings_changelog_v142_item2),
                    stringResource(R.string.settings_changelog_v142_item3),
                    stringResource(R.string.settings_changelog_v142_item4)
                )
            )

            // v1.4.1
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v141_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v141_item1),
                    stringResource(R.string.settings_changelog_v141_item2),
                    stringResource(R.string.settings_changelog_v141_item3),
                    stringResource(R.string.settings_changelog_v141_item4),
                    stringResource(R.string.settings_changelog_v141_item5)
                )
            )

            // v1.4.0
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v140_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v140_item1),
                    stringResource(R.string.settings_changelog_v140_item2),
                    stringResource(R.string.settings_changelog_v140_item3),
                    stringResource(R.string.settings_changelog_v140_item4),
                    stringResource(R.string.settings_changelog_v140_item5),
                    stringResource(R.string.settings_changelog_v140_item6),
                    stringResource(R.string.settings_changelog_v140_item7)
                )
            )

            // v1.3.2
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v132_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v132_item1),
                    stringResource(R.string.settings_changelog_v132_item2),
                    stringResource(R.string.settings_changelog_v132_item3),
                    stringResource(R.string.settings_changelog_v132_item4),
                    stringResource(R.string.settings_changelog_v132_item5)
                )
            )

            // v1.3.1
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v131_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v131_item1),
                    stringResource(R.string.settings_changelog_v131_item2),
                    stringResource(R.string.settings_changelog_v131_item3),
                    stringResource(R.string.settings_changelog_v131_item4)
                )
            )

            // v1.3.0
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v130_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v130_item1),
                    stringResource(R.string.settings_changelog_v130_item2),
                    stringResource(R.string.settings_changelog_v130_item3),
                    stringResource(R.string.settings_changelog_v130_item4),
                    stringResource(R.string.settings_changelog_v130_item5),
                    stringResource(R.string.settings_changelog_v130_item6),
                    stringResource(R.string.settings_changelog_v130_item7)
                )
            )

            // v1.2.1
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v121_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v121_item1),
                    stringResource(R.string.settings_changelog_v121_item2),
                    stringResource(R.string.settings_changelog_v121_item3),
                    stringResource(R.string.settings_changelog_v121_item4)
                )
            )

            // v1.2.0
            ChangelogVersionCard(
                versionTitle = stringResource(R.string.settings_changelog_v120_title),
                items = listOf(
                    stringResource(R.string.settings_changelog_v120_item1),
                    stringResource(R.string.settings_changelog_v120_item2),
                    stringResource(R.string.settings_changelog_v120_item3),
                    stringResource(R.string.settings_changelog_v120_item4)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChangelogVersionCard(
    versionTitle: String,
    items: List<String>,
    isLatest: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = versionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isLatest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (isLatest) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "LATEST",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
