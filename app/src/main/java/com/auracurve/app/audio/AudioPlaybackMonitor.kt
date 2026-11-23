package com.auracurve.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioPlaybackMonitor"

// The playback callback fires on a player's state change, which can come a moment before the
// music stream counts as active (start) or after it stopped counting (stop). One more look this
// long after the last callback settles it.
private const val RECHECK_DELAY_MS = 1_000L

/**
 * Monitors system-wide audio playback using AudioManager.AudioPlaybackCallback.
 * This allows detecting when ANY app (any music or video app) starts or stops playing audio,
 * even when our service isn't running. Used to automatically restart the EQ service when
 * music starts playing.
 */
interface AudioPlaybackMonitor {
    /** True if any app is currently playing audio on the system. */
    val isAnyAppPlaying: StateFlow<Boolean>

    /** Start monitoring for audio playback changes across all apps. */
    fun startMonitoring()

    /** Stop monitoring. */
    fun stopMonitoring()
}

/**
 * Whether media is playing, from public SDK information only. `AudioPlaybackConfiguration.isActive()`
 * would answer it per player but is a hidden system API (non-SDK, flagged and possibly blocked by
 * the platform), so this combines the two public signals instead: a player for media or a game
 * exists ([usages] of the current playback configurations, which also list paused players), and
 * the music stream is sounding ([musicActive], `AudioManager.isMusicActive`). Neither alone is
 * enough: the configurations include idle players, and the music stream also carries sounds of
 * players that are not media.
 */
internal fun isMediaPlaybackActive(
    usages: List<Int>,
    musicActive: Boolean,
): Boolean = musicActive && usages.any { it == AudioAttributes.USAGE_MEDIA || it == AudioAttributes.USAGE_GAME }

@Singleton
class AndroidAudioPlaybackMonitor
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AudioPlaybackMonitor {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val _isAnyAppPlaying = MutableStateFlow(false)
        override val isAnyAppPlaying: StateFlow<Boolean> = _isAnyAppPlaying.asStateFlow()

        // The service's onCreate/onDestroy and the callback's looper may differ; start and stop
        // must each happen once.
        private val isMonitoring = AtomicBoolean(false)

        private val handler = Handler(Looper.getMainLooper())

        private val recheck = Runnable { evaluate(audioManager.activePlaybackConfigurations.orEmpty()) }

        private val playbackCallback =
            object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                    evaluate(configs)
                    handler.removeCallbacks(recheck)
                    if (isMonitoring.get()) handler.postDelayed(recheck, RECHECK_DELAY_MS)
                }
            }

        private fun evaluate(configs: List<AudioPlaybackConfiguration>) {
            val hasActivePlayback =
                runCatching {
                    isMediaPlaybackActive(configs.map { it.audioAttributes.usage }, audioManager.isMusicActive)
                }.onFailure { e -> Log.w(TAG, "Could not read the playback state", e) }
                    .getOrDefault(_isAnyAppPlaying.value)

            if (_isAnyAppPlaying.value != hasActivePlayback) {
                Log.d(TAG, "Audio playback state changed: $hasActivePlayback (${configs.size} configs)")
                _isAnyAppPlaying.value = hasActivePlayback
            }
        }

        override fun startMonitoring() {
            if (!isMonitoring.compareAndSet(false, true)) return
            Log.d(TAG, "Starting audio playback monitoring")
            try {
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register playback callback", e)
                isMonitoring.set(false)
                return
            }
            // Initial state check
            evaluate(audioManager.activePlaybackConfigurations.orEmpty())
        }

        override fun stopMonitoring() {
            if (!isMonitoring.compareAndSet(true, false)) return
            Log.d(TAG, "Stopping audio playback monitoring")
            handler.removeCallbacks(recheck)
            try {
                audioManager.unregisterAudioPlaybackCallback(playbackCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister playback callback", e)
            }
        }
    }
