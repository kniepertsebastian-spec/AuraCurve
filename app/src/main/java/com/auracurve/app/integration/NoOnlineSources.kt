package com.auracurve.app.integration

import android.content.Context
import com.auracurve.app.R
import com.auracurve.app.player.model.LibraryOverview
import com.auracurve.app.player.model.TrackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// This build has no streaming service. The screens hide what needs one
// (BuildConfig.ONLINE_SOURCES); a call that still arrives, e.g. a link shared into the app,
// gets a plain "not available" instead of reaching a service.
@Singleton
class NoOnlineSources
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : OnlineSources {
        private val unavailable: String get() = context.getString(R.string.online_sources_unavailable)

        private fun <T> notAvailable(): LoadResult<T> = LoadResult.Error(unavailable)

        override suspend fun resolveLink(url: String): LinkImportResult = LinkImportResult.Failed(unavailable)

        override fun isSoundCloudSignedIn(): Boolean = false

        override suspend fun searchSoundCloud(
            query: String,
            limit: Int,
        ): LoadResult<List<TrackItem>> = notAvailable()

        override suspend fun loadLibrary(): LoadResult<LibraryOverview> = notAvailable()

        override suspend fun loadLikedTracks(): LoadResult<List<TrackItem>> = notAvailable()

        override suspend fun loadPlaylistTracks(playlistId: Long): LoadResult<List<TrackItem>> = notAvailable()

        override suspend fun pushPlaylistToSoundCloud(
            title: String,
            trackIds: List<Long>,
            existingId: Long?,
        ): LoadResult<Long> = notAvailable()

        override fun openSoundCloudSignIn() = Unit

        override fun signOutSoundCloud() = Unit
    }
