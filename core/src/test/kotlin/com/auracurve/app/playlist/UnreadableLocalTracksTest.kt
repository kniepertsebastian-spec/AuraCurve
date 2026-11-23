package com.auracurve.app.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

// Roadmap 2, M-4: playlists restored onto another phone keep addresses the app cannot open there.
class UnreadableLocalTracksTest {
    private val readable = SavedTrack(id = -1L, title = "Kept", artist = "A", address = "content://docs/document/kept")
    private val gone = SavedTrack(id = -2L, title = "Gone", artist = "B", address = "content://docs/document/gone")
    private val noAddress = SavedTrack(id = 7L, title = "Linked", artist = "C")

    @Test
    fun `only addresses of files on the device are collected`() {
        val playlists = listOf(SavedPlaylist(id = "p", title = "P", tracks = listOf(readable, gone, noAddress)))
        assertEquals(setOf(readable.address, gone.address), UnreadableLocalTracks.localAddresses(playlists))
    }

    @Test
    fun `unreadable tracks are removed, the playlists and the other tracks stay`() {
        val mixed = SavedPlaylist(id = "mixed", title = "Mixed", tracks = listOf(gone, readable, noAddress, gone))
        val onlyGone = SavedPlaylist(id = "gone", title = "Gone", tracks = listOf(gone))

        val result = UnreadableLocalTracks.remove(listOf(mixed, onlyGone), setOf(gone.address!!))

        assertEquals(3, result.removedTracks)
        assertEquals(listOf("mixed", "gone"), result.playlists.map { it.id })
        assertEquals(listOf(readable, noAddress), result.playlists[0].tracks)
        assertEquals(emptyList<SavedTrack>(), result.playlists[1].tracks)
        assertEquals("Gone", result.playlists[1].title)
    }

    @Test
    fun `nothing unreadable leaves the list as it is`() {
        val playlists = listOf(SavedPlaylist(id = "p", title = "P", tracks = listOf(readable)))
        val result = UnreadableLocalTracks.remove(playlists, emptySet())
        assertSame(playlists, result.playlists)
        assertEquals(0, result.removedTracks)
    }
}
