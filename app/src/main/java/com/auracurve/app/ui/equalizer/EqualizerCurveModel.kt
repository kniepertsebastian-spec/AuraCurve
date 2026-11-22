package com.auracurve.app.ui.equalizer

import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.dsp.CurveComposer
import com.auracurve.app.dsp.HeadphoneComfortCurve
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.HeadroomCalculator
import com.auracurve.app.dsp.HeadroomInfo
import com.auracurve.app.dsp.HeadroomWarningLevel
import com.auracurve.app.dsp.HeadroomWarningLevelCalculator
import com.auracurve.app.dsp.LoudnessCompensationCurve
import com.auracurve.app.dsp.SubsonicFilterCurve
import com.auracurve.app.dsp.VirtualBassApproxCurve
import com.auracurve.app.dsp.VolumeLevelMapper
import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.preset.TargetPoint

/**
 * What the equalizer screen derives from its inputs before it draws anything: the combined target
 * curve, the headroom and its warning level, the curve the hero shows and the graph's bands.
 *
 * Pure, so it can be tested without a composition, and the screen computes it with `remember`
 * keyed on the inputs: a recomposition caused by something else (a tab change, a scroll) no
 * longer rebuilds the curves.
 */
internal data class EqualizerCurveModel(
    val headroom: HeadroomInfo,
    val warningLevel: HeadroomWarningLevel,
    /** The curve the hero draws: the applied band gains, or the combined target curve until bands are known. */
    val curvePoints: List<TargetPoint>,
    val graphBands: List<GraphBand>,
) {
    companion object {
        // M3/M4: headroom from the combined correction+voicing curve, not just the highest of the
        // discrete post-mapping band gains - see HeadroomCalculator. Mirrors
        // EqController's own combination (including the HeadphoneComfortCurve and
        // LoudnessCompensationCurve fold-ins while effectiveHeadphoneAcoustics is on) so the
        // banner on the screen always agrees with the inputGainDb the engine actually applied.
        @Suppress("LongParameterList")
        fun of(
            settings: ProcessingSettings,
            bands: List<EqualizerBandCapabilities>,
            activePreset: Preset,
            activeCorrectionProfile: CorrectionProfile,
            activeGoal: SoundGoal,
            activeContext: SoundContext?,
            headphonePower: HeadphonePower,
            effectiveHeadphoneAcoustics: Boolean,
            currentLevelDb: Float,
            isOwnPlayerSession: Boolean,
            preEqActive: Boolean,
        ): EqualizerCurveModel {
            val headphoneCurve = if (effectiveHeadphoneAcoustics) HeadphoneComfortCurve.curve(headphonePower.bassDb) else emptyList()
            val loudnessCurve =
                if (effectiveHeadphoneAcoustics) {
                    LoudnessCompensationCurve.forLevel(currentLevelDb = currentLevelDb, maxBoostDb = headphonePower.loudnessMaxDb)
                } else {
                    emptyList()
                }
            // Subsonic high-pass + preset loudness compensation, same as the controller's context curve.
            val contextCurve =
                CurveComposer.combine(
                    listOf(
                        SubsonicFilterCurve.forCutoff(settings.subsonicCutoffHz),
                        if (effectiveHeadphoneAcoustics) {
                            emptyList()
                        } else {
                            VolumeLevelMapper.compensationCurve(currentLevelDb, settings.loudnessMaxBoostDb)
                        },
                    ),
                )
            val combinedCurve =
                CurveComposer.combine(
                    listOf(
                        activeCorrectionProfile.curve,
                        activePreset.targetCurve,
                        headphoneCurve,
                        loudnessCurve,
                        activeGoal.curve,
                        activeContext?.let { BuiltInContextPresets.defaultFor(it).targetCurve }.orEmpty(),
                        if (!isOwnPlayerSession && preEqActive) VirtualBassApproxCurve.forMix(settings.virtualBassMix) else emptyList(),
                        contextCurve,
                    ),
                )
            val headroom =
                HeadroomCalculator.fromCombinedCurve(
                    combinedCurve = combinedCurve,
                    macroBassDb = settings.macroBassDb,
                    macroPunchDb = settings.macroPunchDb,
                    macroHaerteDb = settings.macroHaerteDb,
                )
            // The hero draws what the EQ is actually set to: the applied band gains (preset, macros
            // and manual band edits included). Before the device has reported its bands there are
            // none, so it falls back to the combined target curve.
            val bandPoints =
                bands
                    .sortedBy { it.centerFreqHz }
                    .map { band -> TargetPoint(band.centerFreqHz.toFloat(), settings.bandGainsDb[band.index] ?: 0f) }
            return EqualizerCurveModel(
                headroom = headroom,
                warningLevel = HeadroomWarningLevelCalculator.fromHeadroom(headroom),
                curvePoints = bandPoints.ifEmpty { combinedCurve },
                graphBands =
                    bands.map { band ->
                        GraphBand(
                            index = band.index,
                            frequencyHz = band.centerFreqHz.toFloat(),
                            gainDb = settings.bandGainsDb[band.index] ?: 0f,
                            minGainDb = band.minGainDb,
                            maxGainDb = band.maxGainDb,
                        )
                    },
            )
        }
    }
}
