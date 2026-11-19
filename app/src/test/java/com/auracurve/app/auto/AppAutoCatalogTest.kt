package com.auracurve.app.auto

import com.auracurve.app.R
import com.auracurve.app.playlist.PlaylistRepository
import com.auracurve.app.playlist.SavedPlaylist
import com.auracurve.app.playlist.SavedTrack
import com.auracurve.app.text.FakeTextProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAutoCatalogTest {
    private val playlists = FakePlaylists()
    private val texts = FakeTextProvider()
    private val catalog = AppAutoCatalog(playlists, texts)

    private fun saved(id: Long) = SavedTrack(id, "Track $id", "Artist")

    private fun trackIdOf(node: com.auracurve.app.player.playback.AutoNode) = (AutoMediaId.parse(node.id) as AutoMediaId.Track).trackId

    @Test
    fun `the root has only the playlists folder`() =
        runTest {
            val root = catalog.children(AutoMediaId.ROOT)

            assertEquals(listOf(texts.get(R.string.auto_my_playlists)), root.map { it.title })
            assertTrue(root.none { it.playable })
        }

    @Test
    fun `playlists are folders and their tracks are playable`() =
        runTest {
            playlists.flow.value =
                listOf(SavedPlaylist("content://a?x=1", "Mix", tracks = listOf(saved(1), saved(2)), createdAtMs = 5L))

            val folders = catalog.children(AutoMediaId.PLAYLISTS)
            assertEquals(listOf("Mix"), folders.map { it.title })
            assertFalse(folders.single().playable)

            val tracks = catalog.children(folders.single().id)
            assertEquals(listOf(1L, 2L), tracks.map { trackIdOf(it) })
            assertTrue(tracks.all { it.playable })
        }

    @Test
    fun `starting a playlist track queues the whole playlist from that track`() =
        runTest {
            playlists.flow.value = listOf(SavedPlaylist("p", "Mix", tracks = listOf(saved(1), saved(2), saved(3))))

            val queue = catalog.queueFor(AutoMediaId.playlistTrack("p", 2L))!!

            assertEquals(listOf(1L, 2L, 3L), queue.tracks.map { it.id })
            assertEquals(1, queue.startIndex)
        }

    @Test
    fun `unknown ids give nothing`() =
        runTest {
            assertTrue(catalog.children("garbage").isEmpty())
            assertTrue(catalog.children(AutoMediaId.playlistFolder("missing")).isEmpty())
            assertNull(catalog.queueFor("garbage"))
            assertNull(catalog.queueFor(AutoMediaId.playlistTrack("missing", 1L)))
        }

    @Test
    fun `a spoken playlist name starts that playlist`() =
        runTest {
            playlists.flow.value =
                listOf(
                    SavedPlaylist("a", "Auto Mix", tracks = listOf(saved(1), saved(2))),
                    SavedPlaylist("b", "Sport", tracks = listOf(saved(3))),
                )

            val queue = catalog.queueForSearch("auto mix")!!

            assertEquals(listOf(1L, 2L), queue.tracks.map { it.id })
            assertEquals(0, queue.startIndex)
            assertNull(catalog.queueForSearch("nothing"))
        }

    @Test
    fun `saying nothing plays the newest playlist`() =
        runTest {
            assertNull(catalog.queueForSearch(""))

            playlists.flow.value =
                listOf(
                    SavedPlaylist("old", "Old", tracks = listOf(saved(1)), createdAtMs = 1L),
                    SavedPlaylist("new", "New", tracks = listOf(saved(2)), createdAtMs = 9L),
                )
            assertEquals(listOf(2L), catalog.queueForSearch("  ")!!.tracks.map { it.id })
        }

    private class FakePlaylists : PlaylistRepository {
        val flow = MutableStateFlow<List<SavedPlaylist>>(emptyList())
        override val playlists: Flow<List<SavedPlaylist>> = flow

        override suspend fun save(playlist: SavedPlaylist) {
            flow.value = flow.value.filterNot { it.id == playlist.id } + playlist
        }

        override suspend fun delete(id: String) {
            flow.value = flow.value.filterNot { it.id == id }
        }
    }
}
