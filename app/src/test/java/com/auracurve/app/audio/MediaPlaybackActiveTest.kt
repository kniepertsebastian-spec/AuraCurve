package com.auracurve.app.audio

import android.media.AudioAttributes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Roadmap TR1, B-2: playback is detected from public SDK signals only, no hidden isActive().
class MediaPlaybackActiveTest {
    @Test
    fun mediaPlayer_withTheMusicStreamSounding_isPlaying() {
        assertTrue(isMediaPlaybackActive(listOf(AudioAttributes.USAGE_MEDIA), musicActive = true))
        assertTrue(isMediaPlaybackActive(listOf(AudioAttributes.USAGE_GAME), musicActive = true))
    }

    @Test
    fun pausedMediaPlayer_isNotPlaying() {
        // The configurations still list a paused player; the music stream is silent then.
        assertFalse(isMediaPlaybackActive(listOf(AudioAttributes.USAGE_MEDIA), musicActive = false))
    }

    @Test
    fun soundsThatAreNotMedia_doNotCount() {
        val others =
            listOf(
                AudioAttributes.USAGE_UNKNOWN,
                AudioAttributes.USAGE_NOTIFICATION,
                AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
                AudioAttributes.USAGE_VOICE_COMMUNICATION,
            )
        assertFalse(isMediaPlaybackActive(others, musicActive = true))
    }

    @Test
    fun noPlayers_isNotPlaying() {
        assertFalse(isMediaPlaybackActive(emptyList(), musicActive = true))
    }
}
