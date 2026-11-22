package com.saymaven.downloader.japaneseasmr.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.service.AudioStorageHelper
import com.saymaven.downloader.japaneseasmr.service.DownloadService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadStorageSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val downloadDir by viewModel.downloadDir.collectAsState()
    val parallelConn by viewModel.parallelConnections.collectAsState()
    val autoClipboard by viewModel.autoClipboard.collectAsState()
    val useDetailedFilename by viewModel.useDetailedFilename.collectAsState()
    val createCueSheet by viewModel.createCueSheet.collectAsState()

    var showManageStorageDialog by remember { mutableStateOf(false) }
    var pendingPickerAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun checkStoragePermissionAndLaunch(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            pendingPickerAction = action
            showManageStorageDialog = true
        } else {
            action()
        }
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
            }
            val resolved = AudioStorageHelper.resolvePhysicalPathFromUri(context, uri.toString()) ?: uri.toString()
            viewModel.setDownloadDir(resolved)
            Toast.makeText(context, context.getString(R.string.settings_folder_toast_changed), Toast.LENGTH_SHORT).show()
        }
    }

    val threadOptions = listOf(
        4 to stringResource(R.string.settings_threads_opt_4),
        8 to stringResource(R.string.settings_threads_opt_8),
        16 to stringResource(R.string.settings_threads_opt_16),
        24 to stringResource(R.string.settings_threads_opt_24),
        32 to stringResource(R.string.settings_threads_opt_32)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.settings_download_storage_title),
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Download Directory
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_download_dir_title),
                icon = Icons.Default.Folder
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                ListItem(
                    headlineContent = {
                        val physicalPath = AudioStorageHelper.resolvePhysicalPathFromUri(context, downloadDir)
                            ?: DownloadService.getDefaultDownloadDirectory().absolutePath
                        val cleanDisplayPath = AudioStorageHelper.formatPathForDisplay(physicalPath)
                        Text(
                            text = cleanDisplayPath,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.settings_download_dir_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        FilledTonalButton(onClick = { checkStoragePermissionAndLaunch { dirPickerLauncher.launch(null) } }) {
                            Text(stringResource(R.string.action_change))
                        }
                    }
                )
            }

            // 2. Parallel Connection Threads
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_threads_dialog_title),
                icon = Icons.Default.Bolt
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_threads_desc, parallelConn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    threadOptions.forEachIndexed { index, (opt, label) ->
                        val isSelected = (parallelConn == opt)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setParallelConnections(opt) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setParallelConnections(opt) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (index < threadOptions.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            }

            // 3. Download Options
            SettingsCategoryHeader(
                title = stringResource(R.string.settings_cat_download),
                icon = Icons.Default.Settings
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_clipboard_title), fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(stringResource(R.string.settings_clipboard_desc), style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = autoClipboard,
                                onCheckedChange = { viewModel.setAutoClipboard(it) }
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_detailed_filename_title), fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(stringResource(R.string.settings_detailed_filename_desc), style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = useDetailedFilename,
                                onCheckedChange = { viewModel.setUseDetailedFilename(it) }
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_cue_sheet_title), fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(stringResource(R.string.settings_cue_sheet_detailed_desc), style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = createCueSheet,
                                onCheckedChange = { viewModel.setCreateCueSheet(it) }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showManageStorageDialog) {
        AlertDialog(
            onDismissRequest = { showManageStorageDialog = false },
            icon = { Icon(Icons.Default.SdCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.settings_manage_storage_dialog_title)) },
            text = { Text(stringResource(R.string.settings_manage_storage_dialog_desc)) },
            confirmButton = {
                Button(onClick = {
                    showManageStorageDialog = false
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                }) {
                    Text(stringResource(R.string.settings_manage_storage_open_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showManageStorageDialog = false
                    pendingPickerAction?.invoke()
                    pendingPickerAction = null
                }) {
                    Text(stringResource(R.string.settings_manage_storage_cancel_btn))
                }
            }
        )
    }
}
