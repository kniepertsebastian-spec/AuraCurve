package com.auracurve.app.integration

import com.auracurve.app.player.model.LibraryOverview
import com.auracurve.app.player.model.TrackItem

// The seam for a streaming service behind the built-in player: account, search, library and
// opening a shared link. This build ships NoOnlineSources, which offers none of them. A planned
// SoundCloud integration through the official API (OAuth 2.1 with PKCE) would implement this
// interface once SoundCloud has approved the use case; see docs/DECISIONS.md.
interface OnlineSources {
    // Opens a track or playlist link that the user pasted or shared into the app.
    suspend fun resolveLink(url: String): LinkImportResult

    // Whether the user is signed in to the service with their own account.
    fun isSoundCloudSignedIn(): Boolean

    // Plain search, without resolving streams.
    suspend fun searchSoundCloud(
        query: String,
        limit: Int,
    ): LoadResult<List<TrackItem>>

    // The signed-in user's library. Each call asks the service again.
    suspend fun loadLibrary(): LoadResult<LibraryOverview>

    suspend fun loadLikedTracks(): LoadResult<List<TrackItem>>

    suspend fun loadPlaylistTracks(playlistId: Long): LoadResult<List<TrackItem>>

    // Saves a playlist into the signed-in user's account (private); with [existingId] that one
    // is updated. Returns the service's playlist id.
    suspend fun pushPlaylistToSoundCloud(
        title: String,
        trackIds: List<Long>,
        existingId: Long?,
    ): LoadResult<Long>

    fun openSoundCloudSignIn()

    fun signOutSoundCloud()
}
