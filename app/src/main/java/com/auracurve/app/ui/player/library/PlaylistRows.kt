package com.auracurve.app.ui.player.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.playlist.PlaylistSearch
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.ui.player.CoverThumb
import com.auracurve.app.ui.player.PillTextField
import com.auracurve.app.ui.player.PlayKey
import com.auracurve.app.ui.player.TrackQueueMenu
import com.auracurve.app.ui.player.TrackStateIcon
import com.auracurve.app.ui.player.markWhenCurrent
import com.auracurve.app.ui.player.textAlpha
import com.auracurve.app.ui.player.trackPlayState
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicSteel

// A saved playlist: tap the name to show its tracks. Tracks of playlists made on the
// device can be removed there.
@Composable
internal fun PlaylistRow(
    playlist: SavedPlaylist,
    onPlay: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onDelete: () -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onPlayNext: (SavedTrack) -> Unit,
    onAddToQueue: (SavedTrack) -> Unit,
    currentTrackId: Long?,
    playedIds: Set<Long>,
) {
    // Saveable: a rotation or process death keeps the open playlist and its search text.
    var expanded by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumb(url = playlist.tracks.firstOrNull()?.artworkUrl, seed = playlist.title, size = 48.dp)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SonicPaper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.playlist_row_count, playlist.tracks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.playlist_delete), tint = SonicSteel)
            }
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                PlayKey(onClick = onPlay, enabled = playlist.tracks.isNotEmpty(), size = 40.dp, glow = false) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.playlist_play))
                }
            }
        }
        if (expanded) {
            if (playlist.tracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.playlist_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
            }
            // A search box once the list is long enough to need one.
            if (playlist.tracks.size >= SEARCH_MIN_TRACKS) {
                PillTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = stringResource(R.string.playlist_search_hint),
                    leadingIcon = Icons.Default.Search,
                )
            }
            val hits = PlaylistSearch.filter(playlist.tracks, search)
            if (hits.isEmpty() && playlist.tracks.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.playlist_search_none, search),
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicSteel,
                )
            }
            hits.forEach { hit ->
                val track = hit.track
                val playState = trackPlayState(track.id, currentTrackId, playedIds)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp)
                            .markWhenCurrent(playState)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPlayTrack(hit.index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { TrackStateIcon(playState) }
                    CoverThumb(url = track.artworkUrl, seed = track.title)
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = SonicPaper.copy(alpha = playState.textAlpha()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = SonicSteel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TrackQueueMenu(
                        trackTitle = track.title,
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onPlayFromHere = { onPlayTrack(hit.index) },
                        onRemoveFromPlaylist = if (playlist.sourceUrl == null) ({ onRemoveTrack(track.id) }) else null,
                    )
                }
            }
        }
    }
}

// From this many tracks on, an opened playlist shows a search box.
private const val SEARCH_MIN_TRACKS = 6
