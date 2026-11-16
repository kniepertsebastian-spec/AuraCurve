package com.auracurve.app.ui.player.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.ui.player.EmptyState
import com.auracurve.app.ui.player.ImportUiState
import com.auracurve.app.ui.player.LibraryUiState
import com.auracurve.app.ui.player.PlayerViewModel
import com.auracurve.app.ui.theme.SonicRule
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.spacing

// The "Playlists" tab of the player screen: files from the device, the playlists saved on the
// device and, with a streaming service, a link field and the signed-in user's library.
fun LazyListScope.playlistsTabItems(
    viewModel: PlayerViewModel,
    playlists: List<SavedPlaylist>,
    importState: ImportUiState,
    signedIn: Boolean,
    libraryState: LibraryUiState,
    currentTrackId: Long?,
    playedIds: Set<Long>,
    onNewPlaylist: () -> Unit,
    onMerge: () -> Unit,
    // False in this build: no streaming service, so no link field and no account actions.
    onlineSources: Boolean = true,
) {
    item {
        val spacing = MaterialTheme.spacing
        var linkText by rememberSaveable { mutableStateOf("") }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, SonicRule),
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                Text(
                    stringResource(if (onlineSources) R.string.playlists_import_title else R.string.playlists_local_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        stringResource(if (onlineSources) R.string.playlists_import_hint else R.string.playlists_local_hint),
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
                if (onlineSources) {
                    Spacer(modifier = Modifier.height(spacing.small))
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.playlists_link_placeholder)) },
                        singleLine = true,
                    )
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
                    if (onlineSources) {
                        Button(
                            onClick = {
                                viewModel.importFromText(linkText)
                                linkText = ""
                            },
                            enabled = linkText.isNotBlank() && !importState.isLoading,
                        ) {
                            Text(stringResource(R.string.playlists_add_button))
                        }
                    }
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
                    if (onlineSources && playlists.isNotEmpty()) {
                        TextButton(onClick = { viewModel.syncToSoundCloud(playlists) }, enabled = !importState.isLoading) {
                            Text(stringResource(R.string.playlists_all_to_soundcloud))
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = spacing.small))
                    playlists.forEach { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onPlay = { viewModel.playPlaylist(playlist) },
                            onPlayTrack = { viewModel.playPlaylist(playlist, it) },
                            onDelete = { viewModel.deletePlaylist(playlist) },
                            onSendToSoundCloud = if (onlineSources) ({ viewModel.syncToSoundCloud(listOf(playlist)) }) else null,
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
    if (signedIn) {
        item {
            val spacing = MaterialTheme.spacing
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, SonicRule),
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.library_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (libraryState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = viewModel::loadLibrary) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.library_reload))
                            }
                        }
                    }
                    libraryState.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (libraryState.isError) MaterialTheme.colorScheme.error else SonicSteel,
                        )
                    }
                    LibraryRow(
                        title = stringResource(R.string.library_likes_title),
                        subtitle = stringResource(R.string.library_likes_desc),
                        onPlay = viewModel::playLikedTracks,
                    )
                }
            }
        }
        libraryState.library?.let { library ->
            if (library.own.isNotEmpty()) {
                item {
                    val spacing = MaterialTheme.spacing
                    Text(
                        stringResource(R.string.library_my_playlists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(library.own, key = { "own-${it.id}" }) { playlist ->
                    LibraryRow(
                        title = playlist.title,
                        subtitle = stringResource(R.string.tracks_count, playlist.trackCount),
                        onPlay = { viewModel.playLibraryPlaylist(playlist) },
                        showCover = true,
                        artworkUrl = playlist.artworkUrl,
                    )
                }
            }
            if (library.liked.isNotEmpty()) {
                item {
                    val spacing = MaterialTheme.spacing
                    Text(
                        stringResource(R.string.library_liked_playlists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(library.liked, key = { "liked-${it.id}" }) { playlist ->
                    LibraryRow(
                        title = playlist.title,
                        subtitle = stringResource(R.string.tracks_count, playlist.trackCount),
                        onPlay = { viewModel.playLibraryPlaylist(playlist) },
                        showCover = true,
                        artworkUrl = playlist.artworkUrl,
                    )
                }
            }
        }
    }
}
