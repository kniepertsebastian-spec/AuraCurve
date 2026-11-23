package com.auracurve.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10

private const val TAG = "SystemVolumeRepository"

interface SystemVolumeRepository {
    val currentLevelDb: StateFlow<Float>

    fun startMonitoring()

    fun stopMonitoring()
}

// Chat feature: "quality changes for headphones" (item not chosen back when
// gentler dynamics was picked instead) - a rough Fletcher-Munson-shaped bass/
// treble boost that grows the quieter the user listens, see
// dsp/LoudnessCompensationCurve.kt. That function only needs the *relative*
// gap to a reference level, not calibrated SPL, which this app has no way to
// measure - the media stream's current/max volume index ratio, expressed as
// a dB-ish quantity via 20*log10(fraction), is a reasonable proxy without any
// device- or headphone-specific calibration.
@Singleton
class AndroidSystemVolumeRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SystemVolumeRepository {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val _currentLevelDb = MutableStateFlow(levelDbFromCurrentVolume())
        override val currentLevelDb: StateFlow<Float> = _currentLevelDb.asStateFlow()

        private val isMonitoring = AtomicBoolean(false)

        private val volumeReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receivedContext: Context?,
                    intent: Intent?,
                ) {
                    refresh()
                }
            }

        // The documented second source: AudioService writes each stream's volume index to
        // Settings.System (volume_music_speaker, volume_music_bt_a2dp, ...), so a change there follows
        // every volume change, also where the broadcast below never arrives. Any other system setting
        // changing triggers it too; refresh() only re-reads the volume and the StateFlow drops repeats.
        // No handler: onChange runs on a binder thread, not the main thread.
        private val volumeObserver =
            object : ContentObserver(null as Handler?) {
                override fun onChange(
                    selfChange: Boolean,
                    uri: Uri?,
                ) {
                    refresh()
                }
            }

        override fun startMonitoring() {
            if (!isMonitoring.compareAndSet(false, true)) return
            // "android.media.VOLUME_CHANGED_ACTION" has no public SDK constant but is
            // a protected system broadcast (fired by AudioService itself, including
            // for hardware volume-key presses) - same registration pattern as
            // AudioSessionRepository's own receiver. Undocumented, so it is only the fast path;
            // the settings observer is the fallback that does not depend on it.
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    volumeReceiver,
                    IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }.onFailure { e -> Log.w(TAG, "Volume broadcast not available", e) }
            runCatching {
                context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
            }.onFailure { e -> Log.w(TAG, "Volume settings observer not available", e) }
            refresh()
        }

        override fun stopMonitoring() {
            if (!isMonitoring.get()) return
            try {
                // Either may not be registered (a failed start, or the system dropped it):
                // unregistering then throws, which must not stop the rest of the teardown.
                runCatching { context.unregisterReceiver(volumeReceiver) }
                    .onFailure { e -> Log.w(TAG, "Volume receiver was not registered", e) }
                runCatching { context.contentResolver.unregisterContentObserver(volumeObserver) }
                    .onFailure { e -> Log.w(TAG, "Volume settings observer was not registered", e) }
            } finally {
                isMonitoring.set(false)
            }
        }

        private fun refresh() {
            _currentLevelDb.value = levelDbFromCurrentVolume()
        }

        private fun levelDbFromCurrentVolume(): Float {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (maxVolume <= 0 || currentVolume <= 0) return MUTE_LEVEL_DB
            val fraction = currentVolume.toFloat() / maxVolume.toFloat()
            return (20f * log10(fraction)).coerceAtLeast(MUTE_LEVEL_DB)
        }

        companion object {
            // Silence isn't -infinity dB in a bounded model - clamped to comfortably
            // below LoudnessCompensationCurve's own 40 dB drop-to-max-boost range, so
            // it always reads as "fully compensated" rather than a huge but finite
            // number.
            private const val MUTE_LEVEL_DB = -60f
        }
    }
