package com.auracurve.app.player.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * How the app's own screens, timers and widgets command the built-in player (AuraCurve roadmap
 * 01, step 1).
 *
 * AudioPlayerService has to be exported (the system, Bluetooth and Android Auto connect to its
 * media session), so any app could send it a start-service intent. Its onStartCommand therefore
 * no longer reads ACTION_* at all: the commands travel inside this process instead, straight to
 * the running service. Another app cannot reach this path; an ACTION_STOP it sends with
 * `adb shell am start-service` or startService() only reaches Media3's own handling and does
 * nothing.
 *
 * The intent here is only the carrier of the action and its extras, as before.
 */
object PlayerCommands {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Sends [action] (one of AudioPlayerService.ACTION_*) with the extras set in [configure].
     *
     * Starts the service first, like the start-service intents did, so it is created if needed and
     * stays started. Throws the same IllegalStateException as startService() when Android refuses
     * the start from the background; the command is then dropped (see PlayerServiceStart).
     */
    fun send(
        context: Context,
        action: String,
        configure: Intent.() -> Unit = {},
    ) {
        val app = context.applicationContext
        app.startService(Intent(app, AudioPlayerService::class.java))
        val command = Intent(action).apply(configure)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            AudioPlayerService.deliver(command)
        } else {
            mainHandler.post { AudioPlayerService.deliver(command) }
        }
    }
}
