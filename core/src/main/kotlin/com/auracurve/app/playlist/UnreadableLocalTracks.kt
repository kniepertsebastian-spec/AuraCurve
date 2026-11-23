package com.auracurve.app.playlist

/**
 * Tracks of files on the device whose address can no longer be opened (roadmap 2, M-4).
 *
 * A file picked with the system file picker is remembered by its `content://` address and played
 * with the read grant the picker gave. Auto Backup restores the playlists onto another phone, but
 * not the grants - and the addresses name documents of the old phone anyway. Such tracks are taken
 * out of their playlists instead of failing one by one when played; the playlists themselves stay.
 */
object UnreadableLocalTracks {
    data class Result(
        val playlists: List<SavedPlaylist>,
        val removedTracks: Int,
    )

    /** Every address of a file on the device in [playlists]. */
    fun localAddresses(playlists: List<SavedPlaylist>): Set<String> =
        playlists
            .flatMap { it.tracks }
            .mapNotNull { track -> track.address?.takeIf { LocalTrackId.isLocalAddress(it) } }
            .toSet()

    /** [playlists] without the tracks whose address is in [unreadable]; order and everything else unchanged. */
    fun remove(
        playlists: List<SavedPlaylist>,
        unreadable: Set<String>,
    ): Result {
        if (unreadable.isEmpty()) return Result(playlists, 0)
        var removed = 0
        val cleaned =
            playlists.map { playlist ->
                val kept = playlist.tracks.filterNot { it.address != null && it.address in unreadable }
                removed += playlist.tracks.size - kept.size
                if (kept.size == playlist.tracks.size) playlist else playlist.copy(tracks = kept)
            }
        return Result(cleaned, removed)
    }
}
