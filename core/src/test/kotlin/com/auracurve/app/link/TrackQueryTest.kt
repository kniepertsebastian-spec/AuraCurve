package com.auracurve.app.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackQueryTest {
    @Test
    fun `an exact match scores higher than an unrelated or longer result`() {
        val query = TrackQuery("Katy Perry", "Roar")
        val exact = TrackMatcher.score(query, "Katy Perry - Roar", "KatyPerryVEVO")
        val mix = TrackMatcher.score(query, "Top 100 Pop Hits 2013 full mix Roar Royals Wrecking Ball Blank Space", "PopMix")
        val unrelated = TrackMatcher.score(query, "Completely different song", "Someone")
        assertTrue("exact=$exact", exact > 0.8)
        assertTrue("exact=$exact mix=$mix", exact > mix)
        assertTrue(unrelated < 0.2)
    }

    @Test
    fun `an artist may match through the uploader name`() {
        val query = TrackQuery("Angerfist", "Criminally Insane")
        val viaUploader = TrackMatcher.score(query, "Criminally Insane", "Angerfist")
        val wrongUploader = TrackMatcher.score(query, "Criminally Insane", "Random Uploader")
        assertTrue(viaUploader > wrongUploader)
    }

    @Test
    fun `accents and case do not matter`() {
        val query = TrackQuery("Beyoncé", "Halo")
        assertTrue(TrackMatcher.score(query, "BEYONCE - halo", "x") > 0.7)
    }

    @Test
    fun `rank orders best first and confidence needs an artist`() {
        val query = TrackQuery("Katy Perry", "Roar")
        val ranked = TrackMatcher.rank(query, listOf("Other Song", "Katy Perry - Roar"), { it }, { "" })
        assertEquals("Katy Perry - Roar", ranked.first().first)
        assertTrue(TrackMatcher.isConfident(query, ranked.first().second))
        assertFalse(TrackMatcher.isConfident(TrackQuery(null, "Roar"), 1.0))
        assertFalse(TrackMatcher.isConfident(query, 0.5))
    }

    @Test
    fun `no title tokens means no match`() {
        assertEquals(0.0, TrackMatcher.score(TrackQuery(null, "the"), "anything", "x"), 0.0)
    }

    @Test
    fun `an artist counts as mentioned when all its words appear in title or uploader`() {
        assertTrue(TrackMatcher.mentionsArtist("Miss K8", "Miss K8 - Hardcore Bomb", "x"))
        assertTrue(TrackMatcher.mentionsArtist("Angerfist", "Drum Go Bang", "ANGERFIST"))
        assertFalse(TrackMatcher.mentionsArtist("Miss K8", "K8 alone", "x"))
        assertFalse(TrackMatcher.mentionsArtist("", "anything", "x"))
    }
}
