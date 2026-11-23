package com.auracurve.app.eq

import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.dsp.LoudnessMatch
import com.auracurve.app.ui.main.CurveCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// The Original/EQ comparison: which side is heard and how much louder (+) or quieter (-) the EQ
// side is estimated to be before the match.
enum class CompareSide { ORIGINAL, EQ }

data class CompareState(
    val side: CompareSide,
    val eqLevelDeltaDb: Float,
)

/**
 * The Original/EQ comparison with matched loudness (roadmap 3, split out of [EqController]): both
 * sides run through the effect, the louder one turned down by the estimated difference. It only
 * holds which side is heard and turns the live settings into what the engine and the built-in
 * player get while it runs; nothing is saved. [EqController] pushes the result after each change.
 */
@Singleton
class CompareSessionController
    @Inject
    constructor() {
        private val _state = MutableStateFlow<CompareState?>(null)
        val state: StateFlow<CompareState?> = _state.asStateFlow()

        /** Starts the comparison, or switches to [side] while it runs. */
        fun setSide(
            side: CompareSide,
            settings: ProcessingSettings,
            bands: List<EqualizerBandCapabilities>,
        ) {
            _state.value = CompareState(side = side, eqLevelDeltaDb = CurveCalculator.eqLevelDeltaDb(settings, bands))
        }

        /** Ends the comparison; false when none was running (nothing to push again then). */
        fun end(): Boolean {
            if (_state.value == null) return false
            _state.value = null
            return true
        }

        /** The live settings, or - while the comparison runs - the loudness-matched version of the chosen side. */
        fun forEngine(
            settings: ProcessingSettings,
            bands: List<EqualizerBandCapabilities>,
        ): ProcessingSettings {
            val compare = _state.value ?: return settings
            val plan = LoudnessMatch.plan(CurveCalculator.eqLevelDeltaDb(settings, bands))
            return when (compare.side) {
                CompareSide.EQ -> settings.copy(inputGainDb = settings.inputGainDb + plan.eqExtraGainDb)
                CompareSide.ORIGINAL ->
                    settings.copy(
                        bypass = false,
                        bandGainsDb = bands.associate { it.index to 0f },
                        preEqGainsDb = settings.preEqGainsDb.map { 0f },
                        inputGainDb = plan.originalGainDb,
                        limiterEnabled = false,
                        mbcEnabled = false,
                    )
            }
        }

        /** The built-in player hears the original side as a bypass of its own processing. */
        fun forPlayer(settings: ProcessingSettings): ProcessingSettings =
            if (_state.value?.side == CompareSide.ORIGINAL) settings.copy(bypass = true) else settings
    }
