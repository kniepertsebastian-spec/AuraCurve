package com.auracurve.app.playlist

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Roadmap 2, M-4: playlists restored from the backup of another phone, whose local files this
// phone has no read grant for.
class DataStorePlaylistRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private val granted = SavedTrack(id = -1L, title = "Picked here", artist = "A", address = "content://docs/document/here")
    private val restored = SavedTrack(id = -2L, title = "From the old phone", artist = "B", address = "content://docs/document/old")
    private val playlist = SavedPlaylist(id = "p", title = "Mix", tracks = listOf(restored, granted), createdAtMs = 1L)

    private fun repository(access: LocalAddressAccess): DataStorePlaylistRepository {
        val store =
            PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("playlists.preferences_pb") }
        return DataStorePlaylistRepository(store, access, Dispatchers.IO)
    }

    @Test
    fun `tracks this phone cannot open are removed on the first read and reported once`() =
        runBlocking {
            val repo = repository { addresses -> addresses - granted.address!! }
            repo.save(playlist)

            val loaded = repo.playlists.first()

            assertEquals(listOf(granted), loaded.single().tracks)
            assertEquals("Mix", loaded.single().title)
            assertEquals(1, repo.removedUnreadableTracks.value)
            // Stored without them: a second read finds the same.
            assertEquals(
                listOf(granted),
                repo.playlists
                    .first()
                    .single()
                    .tracks,
            )

            repo.acknowledgeRemovedTracks()
            assertEquals(0, repo.removedUnreadableTracks.value)
        }

    @Test
    fun `readable playlists are left untouched`() =
        runBlocking {
            val repo = repository { emptySet() }
            repo.save(playlist)

            assertEquals(listOf(playlist), repo.playlists.first())
            assertEquals(0, repo.removedUnreadableTracks.value)
        }

    @Test
    fun `a failing check keeps every track`() =
        runBlocking {
            val repo = repository { throw SecurityException("no access to the grant list") }
            repo.save(playlist)

            assertEquals(listOf(playlist), repo.playlists.first())
            assertEquals(0, repo.removedUnreadableTracks.value)
        }

    @Test
    fun `the check runs once per process`() =
        runBlocking {
            var checks = 0
            val repo =
                repository { addresses ->
                    checks++
                    addresses
                }
            repo.save(playlist)
            repo.playlists.first()
            repo.playlists.first()

            assertEquals(1, checks)
        }
}
