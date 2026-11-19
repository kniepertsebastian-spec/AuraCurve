package com.auracurve.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auracurve.app.R
import com.auracurve.app.integration.LocalTrackReader
import com.auracurve.app.integration.NoLocalTrackReader
import com.auracurve.app.integration.PlayerController
import com.auracurve.app.player.model.TrackItem
import com.auracurve.app.player.playback.NowPlaying
import com.auracurve.app.player.playback.PlayedTracksState
import com.auracurve.app.player.playback.RepeatSetting
import com.auracurve.app.playlist.LocalTrackId
import com.auracurve.app.playlist.PlaylistEditing
import com.auracurve.app.playlist.PlaylistRepository
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.text.TextProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Feedback line on the player screen: what the last action did (or why it could not).
data class ImportUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val controller: PlayerController,
        private val playlistRepository: PlaylistRepository,
        private val texts: TextProvider,
        private val localTracks: LocalTrackReader = NoLocalTrackReader,
    ) : ViewModel() {
        val nowPlaying: StateFlow<NowPlaying?> = controller.nowPlaying
        val queue: StateFlow<List<TrackItem>> = controller.queue

        // Ids of the tracks that were started in the player (this app session).
        val playedIds: StateFlow<Set<Long>> = PlayedTracksState.ids

        // Newest first.
        val playlists: StateFlow<List<SavedPlaylist>> =
            playlistRepository.playlists
                .map { list -> list.sortedByDescending { it.createdAtMs } }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private val _importState = MutableStateFlow(ImportUiState())
        val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

        // Set when the playback notification was tapped, so the UI can bring the player screen
        // forward; cleared by consumeShowPlayerRequest().
        private val _showPlayerRequest = MutableStateFlow(false)
        val showPlayerRequest: StateFlow<Boolean> = _showPlayerRequest.asStateFlow()

        // "Now playing" should open full screen (notification, lock screen), once.
        private val _expandPlayerRequest = MutableStateFlow(false)
        val expandPlayerRequest: StateFlow<Boolean> = _expandPlayerRequest.asStateFlow()

        fun consumeExpandPlayerRequest() {
            _expandPlayerRequest.value = false
        }

        fun consumeShowPlayerRequest() {
            _showPlayerRequest.value = false
        }

        /**
         * The playback notification, the lock screen or the car was tapped (see PlayerLaunch):
         * bring the player screen forward. The text of [showPlayerRequest]'s comment applies,
         * only the trigger differs.
         */
        fun requestShowPlayer() {
            _showPlayerRequest.value = true
        }

        /** Like [requestShowPlayer], and "Now playing" opens full screen on top. */
        fun requestNowPlaying() {
            _showPlayerRequest.value = true
            _expandPlayerRequest.value = true
        }

        fun dismissImportMessage() {
            _importState.value = ImportUiState()
        }

        /** Merges the chosen playlists (in the given order) into one new playlist; the originals stay. */
        fun mergePlaylists(
            sources: List<SavedPlaylist>,
            title: String,
        ) {
            viewModelScope.launch {
                val existing = playlistRepository.playlists.first()
                val merged = PlaylistEditing.merge(title, sources, existing, System.currentTimeMillis())
                if (merged == null) {
                    fail(texts.get(R.string.merge_needs_input))
                    return@launch
                }
                playlistRepository.save(merged)
                _importState.value =
                    ImportUiState(message = texts.get(R.string.merge_done, merged.title, merged.tracks.size, sources.size))
            }
        }

        // P5-7: audio files picked on the device play as the queue, through the same chain as every
        // track (curve, virtual bass, mono bass, limiter) - nothing is looked up, the file is the stream.
        fun playLocalFiles(addresses: List<String>) {
            if (addresses.isEmpty()) return
            viewModelScope.launch {
                val tracks = localTracks.read(addresses)
                if (tracks.isEmpty()) {
                    fail(texts.get(R.string.local_files_unreadable))
                    return@launch
                }
                controller.playQueue(tracks, 0)
                if (tracks.size < addresses.size) {
                    _importState.value = ImportUiState(message = texts.get(R.string.local_files_some_skipped, addresses.size - tracks.size))
                }
            }
        }

        // Plays the playlist as the queue, from [startIndex] (a track picked in the opened list).
        fun playPlaylist(
            playlist: SavedPlaylist,
            startIndex: Int = 0,
        ) {
            if (playlist.tracks.isEmpty()) return
            controller.playQueue(playlist.tracks.map { it.toTrackItem() }, startIndex.coerceIn(0, playlist.tracks.lastIndex))
        }

        // The track that is playing right now (from the queue), null when nothing plays.
        fun currentTrack(): TrackItem? = queue.value.getOrNull(nowPlaying.value?.queueIndex ?: -1)

        /** Makes a playlist on the device, optionally starting with [firstTrack]. Blank names are refused. */
        fun createPlaylist(
            title: String,
            firstTrack: TrackItem? = null,
        ) {
            viewModelScope.launch {
                val existing = playlistRepository.playlists.first()
                val playlist = PlaylistEditing.create(title, existing, System.currentTimeMillis(), firstTrack?.toSaved())
                if (playlist == null) {
                    fail(texts.get(R.string.playlist_name_needed))
                    return@launch
                }
                playlistRepository.save(playlist)
                _importState.value = ImportUiState(message = texts.get(R.string.playlist_created, playlist.title))
            }
        }

        fun addToPlaylist(
            playlist: SavedPlaylist,
            track: TrackItem,
        ) {
            viewModelScope.launch {
                // Read again: the list shown may be a moment old.
                val latest = playlistRepository.playlists.first().firstOrNull { it.id == playlist.id } ?: return@launch
                if (PlaylistEditing.contains(latest, track.id)) {
                    _importState.value = ImportUiState(message = texts.get(R.string.playlist_already_in, track.title, latest.title))
                    return@launch
                }
                playlistRepository.save(PlaylistEditing.addTrack(latest, track.toSaved()))
                _importState.value = ImportUiState(message = texts.get(R.string.playlist_added_to, latest.title))
            }
        }

        fun removeFromPlaylist(
            playlist: SavedPlaylist,
            trackId: Long,
        ) {
            if (!PlaylistEditing.isLocal(playlist)) return
            viewModelScope.launch {
                val latest = playlistRepository.playlists.first().firstOrNull { it.id == playlist.id } ?: return@launch
                playlistRepository.save(PlaylistEditing.removeTrack(latest, trackId))
            }
        }

        fun deletePlaylist(playlist: SavedPlaylist) {
            viewModelScope.launch { playlistRepository.delete(playlist.id) }
        }

        fun playQueueIndex(index: Int) = controller.skipToIndex(index)

        /** Puts [track] right after the one that plays now (or starts it when nothing is loaded). */
        fun playNext(track: TrackItem) = controller.playNext(listOf(track))

        /** Puts [track] at the end of the queue (or starts it when nothing is loaded). */
        fun addToQueue(track: TrackItem) = controller.addToQueue(listOf(track))

        fun playNextFromPlaylist(track: SavedTrack) = controller.playNext(listOf(track.toTrackItem()))

        fun addToQueueFromPlaylist(track: SavedTrack) = controller.addToQueue(listOf(track.toTrackItem()))

        /** Moves the queue entry at [from] to [to]; positions outside the queue are ignored. */
        fun moveQueueItem(
            from: Int,
            to: Int,
        ) {
            val size = queue.value.size
            if (from == to || from !in 0 until size || to !in 0 until size) return
            controller.moveQueueItem(from, to)
        }

        fun removeQueueItem(index: Int) {
            if (index in queue.value.indices) controller.removeQueueItem(index)
        }

        /**
         * Undo of a removal: [track] goes back to [index]. The player has no "insert at", so it
         * is appended and then moved; the service handles both requests in the order they are sent.
         */
        fun reinsertQueueItem(
            track: TrackItem,
            index: Int,
        ) {
            val appendedAt = queue.value.size
            controller.addToQueue(listOf(track))
            if (index in 0 until appendedAt) controller.moveQueueItem(appendedAt, index)
        }

        /** Removes everything after the track that plays now; returns what was removed (for Undo). */
        fun clearUpcoming(): List<TrackItem> {
            val current = nowPlaying.value?.queueIndex ?: return emptyList()
            val all = queue.value
            if (current !in all.indices || current == all.lastIndex) return emptyList()
            // One command for all of them: one per entry meant hundreds of commands (and as many
            // redraws of the list) for a long queue.
            controller.removeQueueItemsAfter(current)
            return all.subList(current + 1, all.size).toList()
        }

        /** Undo of [clearUpcoming]: the tracks go back to the end of the queue, in their order. */
        fun restoreUpcoming(tracks: List<TrackItem>) {
            if (tracks.isNotEmpty()) controller.addToQueue(tracks)
        }

        fun toggleShuffle() = controller.setShuffle(nowPlaying.value?.shuffle != true)

        /** Repeat steps through off -> whole queue -> this track -> off. */
        fun cycleRepeat() = controller.setRepeat(nextRepeat(nowPlaying.value?.repeat ?: RepeatSetting.OFF))

        fun next() = controller.next()

        fun previous() = controller.previous()

        fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

        fun togglePlayback() = controller.togglePlayback()

        private fun fail(message: String) {
            _importState.value = ImportUiState(message = message, isError = true)
        }

        // A file on the device keeps its content address: that is what plays.
        private fun TrackItem.toSaved() =
            SavedTrack(
                id = id,
                title = title,
                artist = artist,
                artworkUrl = artworkUrl,
                durationMs = durationMs,
                address = streamUrl?.takeIf { LocalTrackId.isLocal(id) },
            )

        // A file on the device plays from its address; anything else would need a lookup by id.
        private fun SavedTrack.toTrackItem() =
            TrackItem(id = id, title = title, artist = artist, artworkUrl = artworkUrl, streamUrl = address, durationMs = durationMs)
    }

internal fun nextRepeat(current: RepeatSetting): RepeatSetting =
    when (current) {
        RepeatSetting.OFF -> RepeatSetting.ALL
        RepeatSetting.ALL -> RepeatSetting.ONE
        RepeatSetting.ONE -> RepeatSetting.OFF
    }
