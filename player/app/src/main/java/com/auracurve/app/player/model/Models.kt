package com.auracurve.app.player.model

data class TrackItem(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val streamUrl: String?,
    val durationMs: Long,
    // True when the resolved stream is only a short preview, because the service offers no
    // full length for this track to this user. Only known once a stream was resolved.
    val isPreview: Boolean = false,
    // True when [streamUrl] is an HLS playlist rather than a plain file: ExoPlayer needs a
    // different source for it. Only known once a stream was resolved.
    val isHlsStream: Boolean = false,
    // Upload time in epoch milliseconds, 0 when the source did not say.
    val createdAtMs: Long = 0L,
    // What the uploader set as genre ("" if none) and the raw tag list (space separated,
    // multi-word tags in quotes).
    val genre: String = "",
    val tagList: String = ""
)

data class PlaylistItem(
    val id: Long,
    val title: String,
    val trackCount: Int,
    val artworkUrl: String?,
    val tracks: List<TrackItem> = emptyList()
)

// The signed-in user's playlists: ones they created and ones they liked. `tracks` of
// each PlaylistItem is empty here - the tracks are loaded when one is opened.
data class LibraryOverview(
    val own: List<PlaylistItem>,
    val liked: List<PlaylistItem>,
)
