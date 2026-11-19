package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyModeSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val enabled by viewModel.privacyEnabled.collectAsState()
    val blurNotif by viewModel.privacyBlurNotif.collectAsState()
    val blurInApp by viewModel.privacyBlurInApp.collectAsState()
    val maskTitle by viewModel.privacyMaskTitle.collectAsState()
    val customMask by viewModel.privacyCustomMask.collectAsState()

    var maskInput by remember(customMask) { mutableStateOf(customMask) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_cat_privacy),
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.settings_privacy_enable),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_privacy_enable_desc),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.setPrivacyEnabled(it) }
                        )
                    }
                )
            }

            if (enabled) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column {
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_privacy_blur_notif),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.settings_privacy_blur_notif_desc),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = blurNotif,
                                    onCheckedChange = { viewModel.setPrivacyBlurNotif(it) }
                                )
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_privacy_blur_inapp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.settings_privacy_blur_inapp_desc),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = blurInApp,
                                    onCheckedChange = { viewModel.setPrivacyBlurInApp(it) }
                                )
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_privacy_mask_title),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.settings_privacy_mask_title_desc),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = maskTitle,
                                    onCheckedChange = { viewModel.setPrivacyMaskTitle(it) }
                                )
                            }
                        )
                    }
                }

                if (maskTitle) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_privacy_custom_mask),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Default: " + stringResource(R.string.settings_privacy_custom_mask_default),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = maskInput,
                                onValueChange = {
                                    maskInput = it
                                    viewModel.setPrivacyCustomMask(it)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.settings_privacy_custom_mask_default)) }
                            )
                        }
                    }
                }
            }
        }
    }
}
