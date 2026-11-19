package com.auracurve.app.ui.player.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.ui.equalizer.SonicStyle
import com.auracurve.app.ui.player.EmptyState
import com.auracurve.app.ui.player.ImportUiState
import com.auracurve.app.ui.player.PlayerViewModel
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.spacing

// The player screen's content: files from the device and the playlists saved on the device.
fun LazyListScope.playlistsTabItems(
    viewModel: PlayerViewModel,
    playlists: List<SavedPlaylist>,
    importState: ImportUiState,
    currentTrackId: Long?,
    playedIds: Set<Long>,
    onNewPlaylist: () -> Unit,
    onMerge: () -> Unit,
) {
    item {
        val spacing = MaterialTheme.spacing
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = SonicStyle.surface),
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Text(
                    stringResource(R.string.playlists_local_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.playlists_local_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
                Spacer(modifier = Modifier.height(spacing.small))
                // P5-7: files on the device.
                val context = LocalContext.current
                val pickFiles =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                        // Kept readable for as long as the queue holds them (a restart of the player service, the car).
                        uris.forEach { uri ->
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        viewModel.playLocalFiles(uris.map { it.toString() })
                    }
                OutlinedButton(onClick = { pickFiles.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.local_files_button))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (importState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    importState.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (importState.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    SonicSteel
                                },
                            modifier = Modifier.weight(1f).padding(horizontal = spacing.small),
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onNewPlaylist() }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                        Spacer(modifier = Modifier.size(spacing.small))
                        Text(stringResource(R.string.playlists_new_button))
                    }
                    if (playlists.size >= 2) {
                        TextButton(onClick = { onMerge() }) { Text(stringResource(R.string.playlists_merge)) }
                    }
                }
                if (playlists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(spacing.small))
                    playlists.forEach { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onPlay = { viewModel.playPlaylist(playlist) },
                            onPlayTrack = { viewModel.playPlaylist(playlist, it) },
                            onDelete = { viewModel.deletePlaylist(playlist) },
                            onRemoveTrack = { viewModel.removeFromPlaylist(playlist, it) },
                            onPlayNext = viewModel::playNextFromPlaylist,
                            onAddToQueue = viewModel::addToQueueFromPlaylist,
                            currentTrackId = currentTrackId,
                            playedIds = playedIds,
                        )
                    }
                }
            }
        }
    }
    if (playlists.isEmpty()) {
        item {
            val spacing = MaterialTheme.spacing
            EmptyState(
                title = stringResource(R.string.playlists_empty_title),
                text = stringResource(R.string.playlists_empty_text),
                actionLabel = stringResource(R.string.playlists_new_button),
                onAction = { onNewPlaylist() },
            )
        }
    }
}
