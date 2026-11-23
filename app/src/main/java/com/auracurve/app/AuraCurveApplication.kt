package com.auracurve.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.StrictMode
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.auracurve.app.auto.AppAutoCatalog
import com.auracurve.app.player.PlayerText
import com.auracurve.app.player.playback.AutoCatalogHolder
import com.auracurve.app.player.playback.PlaybackFadeState
import com.auracurve.app.player.playback.PlayerDspState
import com.auracurve.app.quick.EqToggleTileService
import com.auracurve.app.quick.NextPresetTileService
import com.auracurve.app.quick.QuickControls
import com.auracurve.app.quick.QuickWidgetProvider
import com.auracurve.app.service.AppVisibility
import com.auracurve.app.service.AudioSessionForegroundService
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class AuraCurveApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var autoCatalog: AppAutoCatalog

    // Tells the session service whether a screen of this app is open (it never closes itself then).
    @Inject
    lateinit var appVisibility: AppVisibility

    // The tiles and the home-screen widget (roadmap 06, P5-1).
    @Inject
    lateinit var quickControls: QuickControls

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        // Starts reading the player's remembered settings in the background (DataStore, roadmap 2,
        // M-5), so they are in memory before the first track plays. Nothing here reads the disk on
        // the main thread.
        PlayerDspState.load(this)
        PlaybackFadeState.load(this)
        quickControls.onSurfacesChanged = {
            val context = this
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, EqToggleTileService::class.java))
                TileService.requestListeningState(context, ComponentName(context, NextPresetTileService::class.java))
                QuickWidgetProvider.updateAll(context, quickControls.state.value, quickControls.hasConsumer)
            }
        }
        PlayerText.init(this)
        // What the car (Android Auto) may browse and play; also read by the player service.
        AutoCatalogHolder.catalog = autoCatalog
        registerActivityLifecycleCallbacks(appVisibility.callbacks)
        // Starts as early as the app process exists (not only once MainActivity/
        // MainViewModel is created), so the session-open broadcast a player sends
        // is much less likely to be missed. See AudioSessionForegroundService.
        startSessionService()
    }

    // Covers in the player (lists and the large one), read from the audio files on the device:
    // kept in memory while scrolling and on disk across app starts, so they are not read again.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, COVER_MEMORY_SHARE).build() }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve(COVER_CACHE_DIR).toOkioPath())
                    .maxSizeBytes(COVER_DISK_CACHE_BYTES)
                    .build()
            }.crossfade(true)
            .build()

    // A process that was created in the background (e.g. Android Auto binding the media browser) may not start a foreground service on Android 12+: that throws an
    // IllegalStateException (ForegroundServiceStartNotAllowedException). Unguarded in onCreate it
    // would crash the process at start. MainActivity calls this again when the app comes to the
    // foreground, so the service still starts on the next visit.
    fun startSessionService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, AudioSessionForegroundService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Session service not started: the app is in the background", e)
        }
    }

    // Debug builds only: report (to the log, never crash) disk and network access on the main
    // thread and leaked closables, receivers and activities, so they show up while developing.
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }

    private companion object {
        const val TAG = "AuraCurveApplication"
        const val COVER_CACHE_DIR = "covers"
        const val COVER_DISK_CACHE_BYTES = 64L * 1024 * 1024
        const val COVER_MEMORY_SHARE = 0.15
    }
}
