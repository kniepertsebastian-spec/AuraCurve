package com.auracurve.app.player.playback

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auracurve.app.dsp.BassMonoSummerSettings
import com.auracurve.app.dsp.PlayerDspSettings
import com.auracurve.app.preset.TargetPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

// The exact target curve and subsonic cutoff the player's equalizer follows (see
// CurveEqualizer). Set by HardBass EQ's main screen, read on ExoPlayer's playback
// thread; a new object per update, so the reader can tell a change by identity.
class PlayerCurve(
    val curve: List<TargetPoint>,
    val subsonicCutoffHz: Float,
)

object PlayerCurveState {
    @Volatile
    var current: PlayerCurve = PlayerCurve(emptyList(), 0f)
}

private const val PLAYER_DSP_PREFS = "player_dsp"

// One DataStore per file and process. The values that used to be in the SharedPreferences file of
// the same name are moved over once (and that file deleted) on the first read.
private val Context.playerDspStore: DataStore<Preferences> by preferencesDataStore(
    name = PLAYER_DSP_PREFS,
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, PLAYER_DSP_PREFS)) },
)

// The player's output-stage switches (mono bass, limiter), shared between the UI that
// sets them and ExoPlayer's playback thread that reads them - same-process singleton
// for the same reason as VirtualBassState. Remembered in a DataStore so the choice
// survives restarts; both parts default to on. Read and written off the main thread
// (roadmap 2, M-5); until the saved choice is read, the defaults apply.
object PlayerDspState {
    private const val TAG = "PlayerDspState"
    private val KEY_MONO_BASS = booleanPreferencesKey("mono_bass")
    private val KEY_CUTOFF = floatPreferencesKey("mono_bass_cutoff_hz")
    private val KEY_LIMITER = booleanPreferencesKey("limiter")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persisted = PersistedState(PlayerDspSettings())
    val settings: StateFlow<PlayerDspSettings> = persisted.value

    // Starts reading the saved choice in the background; safe to call from several places.
    fun load(context: Context) {
        val store = context.applicationContext.playerDspStore
        persisted.start(
            scope = scope,
            load = { decode(store.data.first()) },
            save = { next -> store.edit { encode(it, next) } },
            onError = { Log.w(TAG, "Player DSP settings not read or saved", it) },
        )
    }

    fun update(
        context: Context,
        transform: (PlayerDspSettings) -> PlayerDspSettings,
    ) {
        load(context)
        persisted.update(transform)
    }

    private fun decode(prefs: Preferences): PlayerDspSettings {
        val defaults = PlayerDspSettings()
        return PlayerDspSettings(
            monoBassEnabled = prefs[KEY_MONO_BASS] ?: defaults.monoBassEnabled,
            monoBassCutoffHz =
                (prefs[KEY_CUTOFF] ?: defaults.monoBassCutoffHz)
                    .coerceIn(BassMonoSummerSettings.MIN_CUTOFF_HZ, BassMonoSummerSettings.MAX_CUTOFF_HZ),
            limiterEnabled = prefs[KEY_LIMITER] ?: defaults.limiterEnabled,
        )
    }

    private fun encode(
        prefs: MutablePreferences,
        settings: PlayerDspSettings,
    ) {
        prefs[KEY_MONO_BASS] = settings.monoBassEnabled
        prefs[KEY_CUTOFF] = settings.monoBassCutoffHz
        prefs[KEY_LIMITER] = settings.limiterEnabled
    }
}
