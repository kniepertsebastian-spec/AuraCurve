package com.auracurve.app.player.playback

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auracurve.app.playback.PlaybackFade
import com.auracurve.app.playback.SleepTimerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

// What the sleep timer is doing; written only by AudioPlayerService (same-process singleton,
// like NowPlayingState). Null = no timer running.
object SleepTimerState {
    private val _status = MutableStateFlow<SleepTimerStatus?>(null)
    val status: StateFlow<SleepTimerStatus?> = _status.asStateFlow()

    fun publish(status: SleepTimerStatus?) {
        _status.value = status
    }
}

private const val PLAYBACK_FADE_PREFS = "playback_fade"

// One DataStore per file and process; the old SharedPreferences file of the same name is moved
// over once on the first read.
private val Context.playbackFadeStore: DataStore<Preferences> by preferencesDataStore(
    name = PLAYBACK_FADE_PREFS,
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, PLAYBACK_FADE_PREFS)) },
)

// Length of the fade out/in at the edges of every track in ms, 0 = off. Remembered in a
// DataStore, read and written off the main thread (roadmap 2, M-5); read by AudioPlayerService,
// set by the sound sheet. Until the saved length is read, fading is off.
//
// This is a dip, not an overlap: the old track fades out, the next fades in. A real crossfade
// plays the end of one track while the next already starts, which needs a second decoder and
// output path running at once - not what the single ExoPlayer here does.
object PlaybackFadeState {
    private const val TAG = "PlaybackFadeState"
    private val KEY_FADE_MS = longPreferencesKey("track_fade_ms")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persisted = PersistedState(0L)
    val trackFadeMs: StateFlow<Long> = persisted.value

    // Starts reading the saved length in the background; safe to call from several places.
    fun load(context: Context) {
        val store = context.applicationContext.playbackFadeStore
        persisted.start(
            scope = scope,
            load = { clamp(store.data.first()[KEY_FADE_MS] ?: 0L) },
            save = { value -> store.edit { it[KEY_FADE_MS] = value } },
            onError = { Log.w(TAG, "Track fade not read or saved", it) },
        )
    }

    fun set(
        context: Context,
        fadeMs: Long,
    ) {
        load(context)
        persisted.update { clamp(fadeMs) }
    }

    private fun clamp(fadeMs: Long): Long = fadeMs.coerceIn(0L, PlaybackFade.MAX_TRACK_FADE_MS)
}
