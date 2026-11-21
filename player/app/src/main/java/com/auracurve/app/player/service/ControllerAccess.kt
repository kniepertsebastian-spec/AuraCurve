package com.auracurve.app.player.service

import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * What a controller of the player's media session may do (AuraCurve roadmap 01, step 1).
 *
 * - [FULL]: browse the app's playlists, start a new queue, every player command. The app itself,
 *   the Android system (system UID or platform-signed, e.g. System UI and Bluetooth) and Google's
 *   car, assistant and Wear apps, verified by their signing certificate, not only by package name.
 * - [TRANSPORT]: play/pause, skip and seek, and read what plays. Controllers Android itself
 *   trusts with media control (MEDIA_CONTENT_CONTROL or an enabled notification listener, e.g. a
 *   smartwatch companion app) - they can already press these buttons on the media notification.
 * - [NONE]: any other app. The connection is refused.
 */
@OptIn(UnstableApi::class)
enum class ControllerAccess {
    FULL,
    TRANSPORT,
    NONE,
    ;

    companion object {
        /** The decision, without Android in the way (see [ControllerAccessCheck] for the inputs). */
        fun decide(
            uid: Int,
            ownUid: Int,
            isPlatformSigned: Boolean,
            isVerifiedGoogleApp: Boolean,
            isTrustedForMediaControl: Boolean,
        ): ControllerAccess =
            when {
                uid == ownUid -> FULL
                uid in 0 until Process.FIRST_APPLICATION_UID -> FULL
                isPlatformSigned || isVerifiedGoogleApp -> FULL
                isTrustedForMediaControl -> TRANSPORT
                else -> NONE
            }

        /**
         * Player commands of a [TRANSPORT] controller: the buttons of the media notification and
         * reading what plays. No new or changed queue, no shuffle/repeat, volume or speed.
         */
        val TRANSPORT_PLAYER_COMMANDS: Player.Commands =
            Player.Commands
                .Builder()
                .addAllReadOnlyCommands()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                ).build()
    }
}

/** Looks a connecting controller up in the package manager and decides its [ControllerAccess]. */
@OptIn(UnstableApi::class)
internal class ControllerAccessCheck(
    private val packageManager: PackageManager,
) {
    fun of(controller: MediaSession.ControllerInfo): ControllerAccess {
        val uid = controller.uid
        val ownUid = Process.myUid()
        // Cheap cases first: no package manager query for the app itself or the system.
        if (uid == ownUid || uid in 0 until Process.FIRST_APPLICATION_UID) {
            return ControllerAccess.decide(uid, ownUid, false, false, false)
        }
        val packageName = controller.packageName
        val ownsPackage = belongsToUid(packageName, uid)
        return ControllerAccess.decide(
            uid = uid,
            ownUid = ownUid,
            isPlatformSigned = ownsPackage && isPlatformSigned(packageName),
            isVerifiedGoogleApp = ownsPackage && isVerifiedGoogleApp(packageName, uid),
            isTrustedForMediaControl = controller.isTrusted,
        )
    }

    // The package name a controller reports must really be one of its UID's packages.
    private fun belongsToUid(
        packageName: String,
        uid: Int,
    ): Boolean = packageManager.getPackagesForUid(uid)?.contains(packageName) == true

    // Signed with the same key as the framework ("android"): System UI and other platform apps.
    private fun isPlatformSigned(packageName: String): Boolean =
        runCatching { packageManager.checkSignatures(PLATFORM_PACKAGE, packageName) == PackageManager.SIGNATURE_MATCH }
            .getOrDefault(false)

    // One of Google's car, assistant or Wear apps, signed with Google's release key (including a
    // rotated one: hasSigningCertificate looks at the whole signing history). Below Android 9 there
    // is no such check; the app's minSdk is 28.
    private fun isVerifiedGoogleApp(
        packageName: String,
        uid: Int,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val digests = TRUSTED_GOOGLE_APPS[packageName] ?: return false
        return digests.any { digest ->
            runCatching { packageManager.hasSigningCertificate(uid, hexToBytes(digest), PackageManager.CERT_INPUT_SHA256) }
                .getOrDefault(false)
        }
    }

    companion object {
        private const val PLATFORM_PACKAGE = "android"

        // SHA-256 of the release signing certificates, as published in Google's media sample
        // (android/uamp, allowed_media_browser_callers.xml). Debug and simulator keys are left out.
        private const val GOOGLE_RELEASE_KEY = "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
        private val TRUSTED_GOOGLE_APPS: Map<String, List<String>> =
            mapOf(
                // Android Auto: release key and its rotated successor.
                "com.google.android.projection.gearhead" to
                    listOf(
                        "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
                        "1ca8dcc0bed3cbd872d2cb791200c0292ca9975768a82d676b8b424fb65b5295",
                    ),
                // Google app (Assistant, "play X on AuraCurve") and Play services share Google's key.
                "com.google.android.googlequicksearchbox" to listOf(GOOGLE_RELEASE_KEY),
                "com.google.android.gms" to listOf(GOOGLE_RELEASE_KEY),
                // Google Assistant on Android Automotive OS.
                "com.google.android.carassistant" to
                    listOf("74b6fbf710e8d90d44d340125889b42306a62c4379d0e5a66220e3a68abf90e2"),
                // Wear OS companion app.
                "com.google.android.wearable.app" to
                    listOf("85cd5973541be6f477d847a0bcc6aa2527684b819cd5968529664cb07157b6fe"),
            )

        internal fun hexToBytes(hex: String): ByteArray =
            ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }
}
