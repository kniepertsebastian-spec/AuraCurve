package com.auracurve.app.player.service

import android.os.Process
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ControllerAccessTest {
    private val ownUid = 10_123
    private val otherAppUid = 10_456

    private fun decide(
        uid: Int,
        platformSigned: Boolean = false,
        googleApp: Boolean = false,
        trustedForMediaControl: Boolean = false,
    ) = ControllerAccess.decide(uid, ownUid, platformSigned, googleApp, trustedForMediaControl)

    @Test
    fun theAppItself_hasFullAccess() {
        assertEquals(ControllerAccess.FULL, decide(ownUid))
    }

    @Test
    fun systemUids_haveFullAccess() {
        assertEquals(ControllerAccess.FULL, decide(Process.SYSTEM_UID))
        // Bluetooth runs as android.uid.bluetooth (1002).
        assertEquals(ControllerAccess.FULL, decide(1002))
    }

    @Test
    fun platformSignedApps_andVerifiedGoogleApps_haveFullAccess() {
        assertEquals(ControllerAccess.FULL, decide(otherAppUid, platformSigned = true))
        assertEquals(ControllerAccess.FULL, decide(otherAppUid, googleApp = true))
    }

    @Test
    fun mediaControlApps_mayOnlyPressTheTransportButtons() {
        assertEquals(ControllerAccess.TRANSPORT, decide(otherAppUid, trustedForMediaControl = true))
    }

    @Test
    fun anyOtherApp_isRefused() {
        assertEquals(ControllerAccess.NONE, decide(otherAppUid))
    }

    @Test
    fun transportCommands_controlPlayback_butCannotChangeTheQueue() {
        val commands = ControllerAccess.TRANSPORT_PLAYER_COMMANDS

        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
        assertFalse(commands.contains(Player.COMMAND_SET_MEDIA_ITEM))
        assertFalse(commands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(commands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
        assertFalse(commands.contains(Player.COMMAND_SET_VOLUME))
    }

    @Test
    fun hexToBytes_readsACertificateDigest() {
        assertArrayEquals(
            byteArrayOf(0xf0.toByte(), 0xfd.toByte(), 0x6c, 0x5b),
            ControllerAccessCheck.hexToBytes("f0fd6c5b"),
        )
    }
}
