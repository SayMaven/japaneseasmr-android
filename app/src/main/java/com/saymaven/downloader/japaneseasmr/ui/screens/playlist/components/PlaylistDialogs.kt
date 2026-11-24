package com.saymaven.downloader.japaneseasmr.ui.screens.playlist.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saymaven.downloader.japaneseasmr.R
import com.saymaven.downloader.japaneseasmr.data.local.entity.HistoryEntity
import com.saymaven.downloader.japaneseasmr.data.local.entity.UserPlaylistEntity
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionItem
import com.saymaven.downloader.japaneseasmr.ui.components.SortOptionsSheet
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlayerViewModel
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlaylistSortState
import com.saymaven.downloader.japaneseasmr.ui.screens.player.PlaylistSortType

@Composable
fun PlaylistNewFolderDialog(
    initialName: String,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_folder_create_title)) },
        text = {
            Column {
                Text(stringResource(R.string.player_folder_create_desc), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text(stringResource(R.string.player_folder_create_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (folderName.isNotBlank()) {
                    onCreate(folderName.trim())
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun PlaylistMoveToFolderDialog(
    item: HistoryEntity?,
    availableFolders: List<String>,
    onMove: (HistoryEntity, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_folder_move_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val allFolderLabel = stringResource(R.string.player_folder_all)
                val targetFolders = availableFolders.filter { it != "Semua" && it != allFolderLabel }
                val workTitle = "[${item?.rjid ?: ""}] ${item?.title ?: ""}"
                Text(stringResource(R.string.player_folder_move_desc, workTitle), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(10.dp))
                targetFolders.forEach { folder ->
                    Surface(
                        onClick = {
                            if (item != null) onMove(item, folder)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val folderDisplayName = if (folder == "Utama") stringResource(R.string.player_folder_main) else folder
                            Text(folderDisplayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun PlaylistAudioInfoDialog(
    info: HistoryEntity,
    onDismiss: () -> Unit
) {
    val detectedFormat = detectAudioFormat(info.localFilePath)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_audio_info_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                Text(text = info.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.player_audio_info_format, detectedFormat), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(R.string.player_audio_info_channels, "Stereo 2.0 (Binaural)"), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(R.string.player_audio_info_size, info.fileSize), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "CV: ${info.cv}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Circle: ${info.circle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.player_audio_info_path, info.localFilePath), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
fun PlaylistLinkRjDialog(
    target: HistoryEntity,
    initialRj: String,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rjLinkInput by remember { mutableStateOf(initialRj) }
    var isLinkingRj by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLinkingRj) onDismiss() },
        title = { Text(stringResource(R.string.player_link_rj_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.player_link_rj_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = rjLinkInput,
                    onValueChange = { rjLinkInput = it },
                    label = { Text(stringResource(R.string.player_link_rj_input_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLinkingRj
                )
                if (isLinkingRj) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Menghubungkan ke DLsite...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rjLinkInput.isNotBlank() && !isLinkingRj) {
                        isLinkingRj = true
                        viewModel.linkRjCode(target.rjid, rjLinkInput) { success, rjOrMsg ->
                            isLinkingRj = false
                            if (success) {
                                onDismiss()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.player_link_rj_success_toast, rjOrMsg),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    rjOrMsg ?: context.getString(R.string.player_link_rj_error_toast, rjLinkInput),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                enabled = !isLinkingRj && rjLinkInput.isNotBlank()
            ) {
                Text(stringResource(R.string.player_link_rj_btn_link))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLinkingRj) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun PlaylistDeleteWorkDialog(
    target: HistoryEntity,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_delete_confirm_title)) },
        text = {
            Text(
                stringResource(R.string.player_delete_confirm_desc, target.title),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun PlaylistSortBottomSheet(
    sortState: PlaylistSortState,
    onSetSort: (PlaylistSortType, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val fieldOptions = remember {
        listOf(
            SortOptionItem(value = PlaylistSortType.DATE, labelRes = R.string.playlist_sort_date),
            SortOptionItem(value = PlaylistSortType.ALL_AGES, labelRes = R.string.browse_sort_all_ages),
            SortOptionItem(value = PlaylistSortType.TITLE, labelRes = R.string.playlist_sort_title),
            SortOptionItem(value = PlaylistSortType.DURATION, labelRes = R.string.playlist_sort_duration),
            SortOptionItem(value = PlaylistSortType.RJID, labelRes = R.string.playlist_sort_rj),
            SortOptionItem(value = PlaylistSortType.CUSTOM, labelRes = R.string.playlist_sort_custom)
        )
    }
    val directionOptions = remember {
        listOf(
            SortOptionItem(value = true, labelRes = R.string.browse_sort_asc),
            SortOptionItem(value = false, labelRes = R.string.browse_sort_desc)
        )
    }

    SortOptionsSheet(
        title = stringResource(R.string.browse_sort_title),
        fieldOptions = fieldOptions,
        selectedField = sortState.type,
        onFieldSelected = { field -> onSetSort(field, sortState.isAscending) },
        directionOptions = directionOptions,
        selectedDirection = sortState.isAscending,
        onDirectionSelected = { isAsc -> onSetSort(sortState.type, isAsc) },
        onDismissRequest = onDismiss
    )
}

@Composable
fun PlaylistCreateDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_dialog_new)) },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = { Text(stringResource(R.string.playlist_dialog_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (nameInput.isNotBlank()) {
                    onCreate(nameInput.trim())
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.playlist_dialog_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun PlaylistRenameDialog(
    target: UserPlaylistEntity,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var renameInput by remember(target.id) { mutableStateOf(target.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_dialog_rename)) },
        text = {
            OutlinedTextField(
                value = renameInput,
                onValueChange = { renameInput = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (renameInput.isNotBlank()) {
                    onRename(renameInput.trim())
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun PlaylistDeleteDialog(
    target: UserPlaylistEntity,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_dialog_delete)) },
        text = {
            Text(
                text = "${stringResource(R.string.playlist_dialog_delete)}: \"${target.name}\"?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
