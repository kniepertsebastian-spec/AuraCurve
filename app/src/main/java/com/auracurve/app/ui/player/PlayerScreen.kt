package com.auracurve.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auracurve.app.R
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.ui.eq.EqStatus
import com.auracurve.app.ui.eq.EqStatusLine
import com.auracurve.app.ui.player.library.MergePlaylistsDialog
import com.auracurve.app.ui.player.library.PlaylistDialog
import com.auracurve.app.ui.player.library.PlaylistDialogRequest
import com.auracurve.app.ui.player.library.playlistsTabItems
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.spacing

// The player tab: files from the device and the playlists saved on it. What plays now lives in
// the shell: the mini player above the navigation bar and "Now playing" it expands into
// (ui/player/shell, docs/PLAYER_REDESIGN.md).
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    eqState: AudioEngineState,
    processingSettings: ProcessingSettings,
    onOpenEqualizer: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val playedIds by viewModel.playedIds.collectAsStateWithLifecycle()
    val currentTrackId = queue.getOrNull(nowPlaying?.queueIndex ?: -1)?.id
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val eqStatusKind = EqStatus.kind(eqState, processingSettings)
    // What the playlist dialog is for: null = closed; a track = add it to a playlist;
    // no track = just make a new, empty playlist.
    var playlistDialog by remember { mutableStateOf<PlaylistDialogRequest?>(null) }
    var mergeDialogOpen by remember { mutableStateOf(false) }

    // The screen draws edge to edge: keep its content clear of the status and
    // navigation bars. And outside a Card the default text color is near-black, which
    // is unreadable on the dark background - so the default is light here, with the
    // song title and artist colored explicitly below.
    val bars = WindowInsets.systemBars.asPaddingValues()
    playlistDialog?.let { request ->
        PlaylistDialog(
            track = request.track,
            playlists = playlists,
            onAddTo = { playlist ->
                request.track?.let { viewModel.addToPlaylist(playlist, it) }
                playlistDialog = null
            },
            onCreate = { name ->
                viewModel.createPlaylist(name, request.track)
                playlistDialog = null
            },
            onDismiss = { playlistDialog = null },
        )
    }
    if (mergeDialogOpen) {
        MergePlaylistsDialog(
            playlists = playlists,
            onMerge = { chosen, name ->
                viewModel.mergePlaylists(chosen, name)
                mergeDialogOpen = false
            },
            onDismiss = { mergeDialogOpen = false },
        )
    }
    CompositionLocalProvider(LocalContentColor provides SonicPaper) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = spacing.medium,
                    end = spacing.medium,
                    top = bars.calculateTopPadding() + spacing.medium,
                    // The shell below (mini player, navigation bar) already keeps the bottom clear.
                    bottom = spacing.medium,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                Text(
                    text = stringResource(R.string.nav_player),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                EqStatusLine(
                    kind = eqStatusKind,
                    pathLabel = if (nowPlaying != null) stringResource(R.string.source_builtin_player) else null,
                    onClick = onOpenEqualizer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            playlistsTabItems(
                viewModel = viewModel,
                playlists = playlists,
                importState = importState,
                currentTrackId = currentTrackId,
                playedIds = playedIds,
                onNewPlaylist = { playlistDialog = PlaylistDialogRequest(null) },
                onMerge = { mergeDialogOpen = true },
            )
        }
    }
}
