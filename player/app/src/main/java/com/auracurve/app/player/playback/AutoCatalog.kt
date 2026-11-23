package com.auracurve.app.player.playback

import com.auracurve.app.player.model.TrackItem

// One entry of the list a car (Android Auto) shows: a folder to open or a track to play.
data class AutoNode(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val playable: Boolean,
)

// What starting a track in the car plays: the queue (the folder the track is in) and
// where in it to begin.
data class AutoQueue(
    val tracks: List<TrackItem>,
    val startIndex: Int,
)

// What the car can browse and play. The app (which owns the playlists) provides it; the
// player service only asks. Ids are described in com.auracurve.app.auto.AutoMediaId.
interface AutoCatalog {
    suspend fun children(parentId: String): List<AutoNode>

    suspend fun queueFor(mediaId: String): AutoQueue?

    // "Play X" by voice: a queue for what the words ask for, or null when nothing fits.
    suspend fun queueForSearch(query: String): AutoQueue?
}

/**
 * Implemented by the app's Application class: how the player service gets the app's [AutoCatalog]
 * (interface injection, roadmap 3). Replaces the former global AutoCatalogHolder that the app had
 * to fill in at start-up; the app's dependency injection provides the catalog behind it.
 */
interface AutoCatalogProvider {
    val autoCatalog: AutoCatalog
}
