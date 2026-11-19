package com.auracurve.app.auto

// The ids of what the car's media browser (Android Auto) shows: the playlists folder and,
// inside it, playlists and their tracks. Playlist ids can contain colons and slashes, so the
// parts are separated by "|", which no id contains. Pure functions, free of Android.
//
//   root                       the top level
//   playlists                  folder: the playlists on the device
//   pl|<playlistId>            folder: the tracks of one playlist
//   pl|<playlistId>|<trackId>  a track of that playlist
sealed interface AutoMediaId {
    data object Root : AutoMediaId

    data object PlaylistsFolder : AutoMediaId

    data class PlaylistFolder(
        val playlistId: String,
    ) : AutoMediaId

    // The playlist the track sits in becomes the play queue.
    data class Track(
        val playlistId: String,
        val trackId: Long,
    ) : AutoMediaId

    companion object {
        const val ROOT = "root"
        const val PLAYLISTS = "playlists"
        private const val PLAYLIST_PREFIX = "pl"
        private const val SEPARATOR = '|'

        fun playlistFolder(playlistId: String): String = "$PLAYLIST_PREFIX$SEPARATOR$playlistId"

        fun playlistTrack(
            playlistId: String,
            trackId: Long,
        ): String = "$PLAYLIST_PREFIX$SEPARATOR$playlistId$SEPARATOR$trackId"

        // Anything that is not an id of ours reads as null (another app's id, garbage).
        fun parse(id: String): AutoMediaId? {
            when (id) {
                ROOT -> return Root
                PLAYLISTS -> return PlaylistsFolder
            }
            val parts = id.split(SEPARATOR)
            if (parts.firstOrNull() != PLAYLIST_PREFIX) return null
            val playlistId = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
            return when (parts.size) {
                2 -> PlaylistFolder(playlistId)
                3 -> parts[2].toLongOrNull()?.let { Track(playlistId, it) }
                else -> null
            }
        }

        // Whether [id] is a track a car can start (not a folder).
        fun isTrack(id: String): Boolean = parse(id) is Track
    }
}
