package com.auracurve.app.ui.main

import com.auracurve.app.audio.AudioCapabilities
import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.context.SoundContext
import com.auracurve.app.dsp.CurveComposer
import com.auracurve.app.dsp.EqualizerInterpolator
import com.auracurve.app.dsp.HeadphoneComfortCurve
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.HeadroomCalculator
import com.auracurve.app.dsp.LoudnessCompensationCurve
import com.auracurve.app.dsp.LoudnessMatch
import com.auracurve.app.dsp.PreEqMacros
import com.auracurve.app.dsp.PreEqMapper
import com.auracurve.app.dsp.PreEqResponseMode
import com.auracurve.app.dsp.SubsonicFilterCurve
import com.auracurve.app.dsp.VirtualBassApproxCurve
import com.auracurve.app.dsp.VolumeLevelMapper
import com.auracurve.app.eq.PresetSettingsMapper
import com.auracurve.app.eq.ResolvedDynamics
import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.TargetPoint
import kotlin.math.abs

// The player's curve is handed over as points on this log grid (about 6 per octave).
private const val PLAYER_CURVE_MIN_HZ = 20f
private const val PLAYER_CURVE_MAX_HZ = 20_000f
private const val PLAYER_CURVE_STEP = 1.12f

// Smaller than this, a classic band counts as sitting on the curve (no manual edit).
private const val MANUAL_DELTA_EPSILON_DB = 0.05f

/**
 * Everything that goes into the target curve, as plain values. Taken out of `MainViewModel`
 * (roadmap 06, P6-1) so the curve arithmetic can be tested without a view model, a repository or
 * a coroutine.
 */
data class CurveInputs(
    // M3 "My headphones": the correction curve of the active profile.
    val correctionCurve: List<TargetPoint>,
    // The active preset ("Klangstil").
    val presetCurve: List<TargetPoint>,
    // Whether headphone acoustics apply (comfort curve and loudness compensation ride on it).
    val headphoneAcoustics: Boolean,
    val headphonePower: HeadphonePower,
    val currentLevelDb: Float,
    val goalCurve: List<TargetPoint>,
    val context: SoundContext?,
    // Virtual bass runs for real in the own player only; for other apps it is approximated.
    val ownPlayerSession: Boolean,
    val preEqAvailable: Boolean,
    val settings: ProcessingSettings,
    // P5-3: the parametric bands, already evaluated (ParametricCurve.bandsToCurve).
    val parametricCurve: List<TargetPoint> = emptyList(),
)

object CurveCalculator {
    /**
     * M3 "combine target curves": correction and voicing are combined into one curve before
     * anything else - band mapping, headroom - happens.
     *
     * HeadphoneComfortCurve and the loudness compensation only join in while
     * [CurveInputs.headphoneAcoustics] is true: that is what makes the headphone mode audible
     * through the system Equalizer path, where true crossfeed cannot run.
     */
    fun combinedCurve(
        inputs: CurveInputs,
        includeSubsonic: Boolean = true,
    ): List<TargetPoint> {
        val power = inputs.headphonePower
        val headphoneCurve = if (inputs.headphoneAcoustics) HeadphoneComfortCurve.curve(power.bassDb) else emptyList()
        val loudnessCurve =
            if (inputs.headphoneAcoustics) {
                LoudnessCompensationCurve.forLevel(currentLevelDb = inputs.currentLevelDb, maxBoostDb = power.loudnessMaxDb)
            } else {
                emptyList()
            }
        return CurveComposer.combine(
            listOf(
                inputs.correctionCurve,
                inputs.presetCurve,
                headphoneCurve,
                loudnessCurve,
                inputs.goalCurve,
                inputs.parametricCurve,
                inputs.context?.let { BuiltInContextPresets.defaultFor(it).targetCurve }.orEmpty(),
                virtualBassApproxCurve(inputs),
                contextCurve(inputs, includeSubsonic),
            ),
        )
    }

    // Virtual Bass cannot run on other apps' audio; with the high-resolution pre-EQ an EQ
    // approximation of it is possible. Never for the own player, which has the real thing.
    private fun virtualBassApproxCurve(inputs: CurveInputs): List<TargetPoint> =
        if (!inputs.ownPlayerSession && inputs.preEqAvailable) {
            VirtualBassApproxCurve.forMix(inputs.settings.virtualBassMix)
        } else {
            emptyList()
        }

    // Subsonic high-pass + the active sound's own volume-dependent loudness compensation. Headphone
    // mode already has its own loudness curve, so the preset's one is skipped there rather than
    // stacking two boosts. Empty while both are off.
    private fun contextCurve(
        inputs: CurveInputs,
        includeSubsonic: Boolean,
    ): List<TargetPoint> {
        val settings = inputs.settings
        val presetLoudness =
            if (inputs.headphoneAcoustics) {
                emptyList()
            } else {
                VolumeLevelMapper.compensationCurve(inputs.currentLevelDb, settings.loudnessMaxBoostDb)
            }
        return CurveComposer.combine(
            listOf(
                if (includeSubsonic) SubsonicFilterCurve.forCutoff(settings.subsonicCutoffHz) else emptyList(),
                presetLoudness,
            ),
        )
    }

    /**
     * Gains for the 34-band pre-EQ (empty while the classic equalizer is in use). The classic band
     * sliders stay the editing surface: how far each one sits from the curve is carried over as a
     * manual delta that fades out towards its neighbours.
     */
    fun preEqGains(
        inputs: CurveInputs,
        capabilities: AudioCapabilities,
        classicBandGainsDb: Map<Int, Float>,
    ): List<Float> {
        if (capabilities.preEqCutoffsHz.isEmpty()) return emptyList()
        val settings = inputs.settings
        val macros = PreEqMacros(settings.macroBassDb, settings.macroPunchDb, settings.macroHaerteDb)
        val curve = combinedCurve(inputs)
        val fromCurve =
            EqualizerInterpolator.interpolateCurveToBands(
                curve,
                capabilities.bands,
                settings.macroBassDb,
                settings.macroPunchDb,
                settings.macroHaerteDb,
            )
        val deltas =
            capabilities.bands.mapNotNull { band ->
                val delta = (classicBandGainsDb[band.index] ?: 0f) - (fromCurve[band.index] ?: 0f)
                if (abs(delta) > MANUAL_DELTA_EPSILON_DB) TargetPoint(band.centerFreqHz.toFloat(), delta) else null
            }
        val responseMode =
            try {
                PreEqResponseMode.valueOf(settings.preEqResponseMode)
            } catch (e: IllegalArgumentException) {
                PreEqResponseMode.DEFAULT
            }
        return PreEqMapper.map(curve, macros, capabilities.preEqCutoffsHz, deltas, responseMode)
    }

    /**
     * The live settings mapped onto the hardware (moved here from EqController, roadmap 3): the
     * band gains from the combined curve, the input gain that keeps its peak from clipping, the
     * pre-EQ gains and the given dynamics.
     *
     * roadmap-2026.md M4: the headroom comes from the combined *continuous* curve, not just the
     * highest of the discrete post-mapping band gains - see HeadroomCalculator for why that
     * undercounts a peak that falls between two hardware band centers.
     */
    fun mapToBands(
        inputs: CurveInputs,
        bands: List<EqualizerBandCapabilities>,
        capabilities: AudioCapabilities,
        dynamics: ResolvedDynamics,
    ): ProcessingSettings {
        val settings = inputs.settings
        val curve = combinedCurve(inputs)
        val calculatedGains =
            EqualizerInterpolator.interpolateCurveToBands(
                curve = curve,
                bands = bands,
                macroBassDb = settings.macroBassDb,
                macroPunchDb = settings.macroPunchDb,
                macroHaerteDb = settings.macroHaerteDb,
            )
        val headroom =
            HeadroomCalculator.fromCombinedCurve(
                combinedCurve = curve,
                macroBassDb = settings.macroBassDb,
                macroPunchDb = settings.macroPunchDb,
                macroHaerteDb = settings.macroHaerteDb,
            )
        return settings.copy(
            bandGainsDb = calculatedGains,
            inputGainDb = PresetSettingsMapper.safetyScaledInputGainDb(headroom.maxPositiveGainDb, inputs.context),
            mbcEnabled = dynamics.mbcEnabled,
            mbcThresholdDb = dynamics.mbcThresholdDb,
            mbcRatio = dynamics.mbcRatio,
            preEqGainsDb = preEqGains(inputs, capabilities, calculatedGains),
        )
    }

    /**
     * The curve the built-in player's own PCM equalizer gets: points on a log grid (about 6 per
     * octave) with the macros sampled in, since they are not part of the curve. The subsonic cut
     * is a real high-pass there and travels separately, so the curve must not contain it twice.
     */
    fun playerCurve(inputs: CurveInputs): List<TargetPoint> {
        val settings = inputs.settings
        val curve = combinedCurve(inputs, includeSubsonic = false).sortedBy { it.frequencyHz }
        return generateSequence(PLAYER_CURVE_MIN_HZ) { it * PLAYER_CURVE_STEP }
            .takeWhile { it <= PLAYER_CURVE_MAX_HZ }
            .map { hz ->
                TargetPoint(
                    hz,
                    EqualizerInterpolator.resolveGainAtFrequency(
                        hz,
                        curve,
                        settings.macroBassDb,
                        settings.macroPunchDb,
                        settings.macroHaerteDb,
                    ),
                )
            }.toList()
    }

    /**
     * The Original/EQ comparison: how much louder (+) or quieter (-) the EQ side is estimated to be,
     * from the band gains and the input gain. Used to match the loudness of the two sides.
     */
    fun eqLevelDeltaDb(
        settings: ProcessingSettings,
        bands: List<EqualizerBandCapabilities>,
    ): Float =
        LoudnessMatch.eqLevelDeltaDb(
            bands.map { it.centerFreqHz.toFloat() to (settings.bandGainsDb[it.index] ?: 0f) },
            settings.inputGainDb,
        )
}
