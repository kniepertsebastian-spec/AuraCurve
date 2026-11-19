package com.auracurve.app.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMediaIdTest {
    @Test
    fun `folders round trip`() {
        assertEquals(AutoMediaId.Root, AutoMediaId.parse(AutoMediaId.ROOT))
        assertEquals(AutoMediaId.PlaylistsFolder, AutoMediaId.parse(AutoMediaId.PLAYLISTS))
    }

    @Test
    fun `a playlist id with colons and slashes survives`() {
        val playlistId = "content://media/a/sets/mix?x=1"

        assertEquals(AutoMediaId.PlaylistFolder(playlistId), AutoMediaId.parse(AutoMediaId.playlistFolder(playlistId)))
        assertEquals(
            AutoMediaId.Track(playlistId, 42L),
            AutoMediaId.parse(AutoMediaId.playlistTrack(playlistId, 42L)),
        )
    }

    @Test
    fun `only tracks are playable`() {
        assertTrue(AutoMediaId.isTrack(AutoMediaId.playlistTrack("x", 1L)))
        assertFalse(AutoMediaId.isTrack(AutoMediaId.PLAYLISTS))
        assertFalse(AutoMediaId.isTrack(AutoMediaId.playlistFolder("x")))
    }

    @Test
    fun `foreign or broken ids read as null`() {
        assertNull(AutoMediaId.parse(""))
        assertNull(AutoMediaId.parse("other:track:123"))
        assertNull(AutoMediaId.parse("pl|"))
        assertNull(AutoMediaId.parse("pl|x|notanumber"))
        assertNull(AutoMediaId.parse("disc||1"))
        assertNull(AutoMediaId.parse("likes||1"))
        assertNull(AutoMediaId.parse("discovery"))
        assertNull(AutoMediaId.parse("pl|a|b|c"))
    }
}
