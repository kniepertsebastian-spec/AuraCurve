package com.auracurve.app.auto

import com.auracurve.app.R
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.player.playback.AutoCatalog
import com.auracurve.app.player.playback.AutoNode
import com.auracurve.app.player.playback.AutoQueue
import com.auracurve.app.playlist.PlaylistRepository
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.text.TextProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// What the car (Android Auto) can browse: the playlists on the device. Lists in a car are kept
// short on purpose - scrolling them while driving is no use.
@Singleton
class AppAutoCatalog
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
        private val texts: TextProvider,
    ) : AutoCatalog {
        override suspend fun children(parentId: String): List<AutoNode> =
            when (val id = AutoMediaId.parse(parentId)) {
                AutoMediaId.Root -> rootFolders()
                AutoMediaId.PlaylistsFolder ->
                    playlistRepository.playlists
                        .first()
                        .sortedByDescending { it.createdAtMs }
                        .take(MAX_ENTRIES)
                        .map {
                            AutoNode(
                                AutoMediaId.playlistFolder(it.id),
                                it.title,
                                texts.get(R.string.tracks_count, it.tracks.size),
                                playable = false,
                            )
                        }

                is AutoMediaId.PlaylistFolder ->
                    playlistRepository.playlists
                        .first()
                        .firstOrNull { it.id == id.playlistId }
                        ?.tracks
                        .orEmpty()
                        .take(MAX_ENTRIES)
                        .map { trackNode(AutoMediaId.playlistTrack(id.playlistId, it.id), it) }

                is AutoMediaId.Track, null -> emptyList()
            }

        override suspend fun queueFor(mediaId: String): AutoQueue? {
            val id = AutoMediaId.parse(mediaId) as? AutoMediaId.Track ?: return null
            val tracks =
                playlistRepository.playlists
                    .first()
                    .firstOrNull { it.id == id.playlistId }
                    ?.tracks
                    .orEmpty()
                    .take(MAX_ENTRIES)
                    .map { it.toTrackItem() }
            val start = tracks.indexOfFirst { it.id == id.trackId }
            return if (start < 0) null else AutoQueue(tracks, start)
        }

        // Voice: a playlist whose name contains the words. Nothing said ("play music") plays the
        // newest playlist.
        override suspend fun queueForSearch(query: String): AutoQueue? {
            val words = query.trim()
            val all = playlistRepository.playlists.first().filter { it.tracks.isNotEmpty() }
            val playlist =
                if (words.isEmpty()) {
                    all.maxByOrNull { it.createdAtMs }
                } else {
                    all.firstOrNull { it.title.contains(words, ignoreCase = true) }
                } ?: return null
            return AutoQueue(playlist.tracks.take(MAX_ENTRIES).map { it.toTrackItem() }, 0)
        }

        private fun rootFolders() =
            listOf(
                AutoNode(AutoMediaId.PLAYLISTS, texts.get(R.string.auto_my_playlists), playable = false),
            )

        private fun trackNode(
            mediaId: String,
            track: SavedTrack,
        ) = AutoNode(mediaId, track.title, track.artist, track.artworkUrl, playable = true)

        // A file on the device plays from its address (see SavedTrack.address).
        private fun SavedTrack.toTrackItem() =
            TrackItem(id = id, title = title, artist = artist, artworkUrl = artworkUrl, streamUrl = address, durationMs = durationMs)

        private companion object {
            const val MAX_ENTRIES = 100
        }
    }
