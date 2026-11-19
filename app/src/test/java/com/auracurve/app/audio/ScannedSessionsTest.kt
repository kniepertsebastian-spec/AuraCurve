package com.auracurve.app.audio

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ScannedSessionsTest {
    private val repository = AndroidAudioSessionRepository(ApplicationProvider.getApplicationContext())
    private val musicApp = AudioSession(sessionId = 11, packageName = "com.example.musicapp")
    private val browser = AudioSession(sessionId = 22, packageName = "org.example.browser")

    @Test
    fun scannedSessions_joinTheListAndBecomeActiveWhenNothingIsActive() {
        repository.updateScannedSessions(listOf(musicApp))

        assertEquals(listOf(musicApp), repository.sessions.value)
        assertEquals(musicApp, repository.activeSession.value)
    }

    @Test
    fun scannedSessions_leaveAgainWhenTheScanNoLongerFindsThem() {
        repository.updateScannedSessions(listOf(musicApp, browser))
        repository.setActiveSession(musicApp)

        repository.updateScannedSessions(listOf(browser))

        assertEquals(listOf(browser), repository.sessions.value)
        assertEquals("the active session was gone, the remaining one takes over", browser, repository.activeSession.value)
    }

    @Test
    fun anActiveSessionTheUserPickedStaysWhileItIsStillFound() {
        repository.updateScannedSessions(listOf(musicApp, browser))
        repository.setActiveSession(musicApp)

        repository.updateScannedSessions(listOf(musicApp, browser))

        assertEquals(musicApp, repository.activeSession.value)
    }

    @Test
    fun emptyScan_clearsEverythingTheScanAdded() {
        repository.updateScannedSessions(listOf(musicApp))

        repository.updateScannedSessions(emptyList())

        assertEquals(emptyList<AudioSession>(), repository.sessions.value)
        assertNull(repository.activeSession.value)
    }
}
