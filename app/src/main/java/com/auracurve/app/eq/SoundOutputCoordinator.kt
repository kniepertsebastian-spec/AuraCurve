package com.auracurve.app.eq

import com.auracurve.app.BuildConfig
import com.auracurve.app.audio.AudioEngine
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.integration.PlayerBridge
import com.auracurve.app.preset.TargetPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the finished settings go (roadmap 3, split out of [EqController]): the system effect chain
 * ([AudioEngine]) and the built-in player's own processing ([PlayerBridge]). It decides which of
 * the two applies the curve - the player's PCM equalizer while the EQ is bound to this app's own
 * player, the system equalizer otherwise - and applies the Original/EQ comparison on the way.
 */
@Singleton
class SoundOutputCoordinator
    @Inject
    constructor(
        private val audioEngine: AudioEngine,
        private val playerBridge: PlayerBridge,
        private val routeProfiles: RouteProfileCoordinator,
        private val compareSession: CompareSessionController,
        @param:EqControllerScope private val scope: CoroutineScope,
    ) {
        /**
         * Sends [settings] to the engine and the player: while the comparison runs, the
         * loudness-matched version of the chosen side. [playerCurve] is evaluated at once, and
         * only while the player processes at all.
         */
        fun push(
            settings: ProcessingSettings,
            playerCurve: () -> List<TargetPoint>,
        ) {
            val forEngine = compareSession.forEngine(settings, audioEngine.capabilities.value.bands)
            // Only the dynamics (MBC, limiter) stay on the system path for the own player.
            val forSystem =
                if (pcmEqualizerOwnsTheCurve(settings)) {
                    forEngine.copy(
                        bandGainsDb = forEngine.bandGainsDb.mapValues { 0f },
                        preEqGainsDb = forEngine.preEqGainsDb.map { 0f },
                        inputGainDb = 0f,
                    )
                } else {
                    forEngine
                }
            scope.launch {
                audioEngine.apply(forSystem)
            }
            val forPlayer = compareSession.forPlayer(settings)
            pushVirtualBass(forPlayer)
            pushPlayerCurve(forPlayer, playerCurve)
        }

        /** The restored settings, applied before anything else runs (no comparison can be active yet). */
        suspend fun applyRestored(settings: ProcessingSettings) {
            audioEngine.apply(settings)
            pushVirtualBass(settings)
        }

        /** Rebuilds the effects, e.g. after the band layout changed. */
        fun retryEngine() {
            scope.launch { audioEngine.retry() }
        }

        fun stopPlayer() = playerBridge.stopPlayer()

        // The player's own PCM equalizer follows the exact curve (see CurveCalculator.playerCurve).
        private fun pushPlayerCurve(
            settings: ProcessingSettings,
            playerCurve: () -> List<TargetPoint>,
        ) {
            val active = settings.masterEnabled && !settings.bypass
            if (!active) {
                playerBridge.setPlayerCurve(emptyList(), 0f)
                return
            }
            playerBridge.setPlayerCurve(playerCurve(), settings.subsonicCutoffHz)
        }

        // Virtual bass is not part of the system effect chain (AudioEngine) - it runs
        // in the built-in player's own audio pipeline, so it goes through the player
        // bridge instead. Follows master/bypass like the rest of the processing.
        private fun pushVirtualBass(settings: ProcessingSettings) {
            playerBridge.setVirtualBassMix(if (settings.masterEnabled && !settings.bypass) settings.virtualBassMix else 0f)
        }

        // True while the EQ is bound to this app's own player: its PCM equalizer already
        // applies the curve, so the system equalizer must not apply it a second time.
        private fun pcmEqualizerOwnsTheCurve(settings: ProcessingSettings): Boolean =
            settings.masterEnabled && !settings.bypass && routeProfiles.activeSession.value?.packageName == BuildConfig.APPLICATION_ID
    }
