package com.auracurve.app.eq

import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.context.SoundContext
import com.auracurve.app.dsp.HeadphoneDynamicsEasing
import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetIntensityBoost

// How much of the peak EQ boost the automatic input-gain cut pre-cancels before the signal ever
// reaches dynamics processing. The history of this value (it was 1.0, then 0.3, 0.2, 0.1) is in
// roadmap.md, sessions 16, 17 and 24: at 0.1 a +12 dB bass preset left the limiter over 10 dB of
// reduction on bass-heavy tracks and flattened exactly the kicks it was meant to make hit harder.
// At 0.5 the limiter works a few dB at most; the punch comes from the MBC makeup gain instead.
private const val INPUT_GAIN_SAFETY_RATIO = 0.5f

// Small speaker drivers distort mechanically before anything clips digitally, and the limiter does
// not protect the membrane - so the speaker contexts pull the input gain down much further.
private const val SPEAKER_INPUT_GAIN_SAFETY_RATIO = 0.8f

/** The dynamics (multiband compressor) settings that result from a preset, a context and headphone easing. */
data class ResolvedDynamics(
    val mbcEnabled: Boolean,
    val mbcThresholdDb: Float,
    val mbcRatio: Float,
)

/**
 * How a preset, the active listening context and the intensity switch turn into
 * [ProcessingSettings]. Pure functions of their arguments, extracted from [EqController] so they
 * can be tested without a controller, an engine or any flow; the controller only supplies its
 * current state.
 */
object PresetSettingsMapper {
    /** The preset as it is played: with "more intensity" switched on it is boosted. */
    fun intensified(
        preset: Preset,
        moreIntensity: Boolean,
    ): Preset = if (moreIntensity) PresetIntensityBoost.apply(preset) else preset

    // Small speaker drivers and a soundbar's long box (the 40-80 Hz fundament of its mode pushes
    // them hardest) get the stronger protection.
    fun inputGainSafetyRatio(context: SoundContext?): Float =
        when (context) {
            SoundContext.BLUETOOTH_SPEAKER, SoundContext.SOUNDBAR -> SPEAKER_INPUT_GAIN_SAFETY_RATIO
            else -> INPUT_GAIN_SAFETY_RATIO
        }

    /** The input gain (always <= 0) that pre-cancels a share of [boostDb]; exactly 0f, never -0.0f, for no boost. */
    fun safetyScaledInputGainDb(
        boostDb: Float,
        context: SoundContext?,
    ): Float {
        // Boxed Float.equals() treats -0.0f and 0.0f as unequal although == does not, so a
        // zero boost must return a real zero.
        if (boostDb == 0f) return 0f
        return -(boostDb * inputGainSafetyRatio(context))
    }

    /** The input gain for the strongest boost among [bandGainsDb] (cuts alone need none). */
    fun automaticInputGainDb(
        bandGainsDb: Map<Int, Float>,
        context: SoundContext?,
    ): Float {
        val peakBoostDb = bandGainsDb.values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        return safetyScaledInputGainDb(peakBoostDb, context)
    }

    /**
     * The settings after [preset] is selected: its macros, limiter and dynamics, an input gain for
     * its requested headroom, and the context features on top. The context features take the
     * original [preset]; the rest takes it as played (intensified).
     */
    fun withPreset(
        settings: ProcessingSettings,
        preset: Preset,
        moreIntensity: Boolean,
        context: SoundContext?,
    ): ProcessingSettings {
        val played = intensified(preset, moreIntensity)
        return withContextFeatures(
            settings.copy(
                inputGainDb = safetyScaledInputGainDb(played.requestedHeadroomDb, context),
                macroBassDb = played.macroBassDb,
                macroPunchDb = played.macroPunchDb,
                macroHaerteDb = played.macroHaerteDb,
                limiterEnabled = played.limiter.enabled,
                limiterThresholdDb = played.limiter.thresholdDb,
                mbcEnabled = played.mbcEnabled,
                mbcThresholdDb = played.mbcThresholdDb,
                mbcRatio = played.mbcRatio,
            ),
            preset,
            context,
        )
    }

    // The preset's own context features, merged with what the active context mode adds (the
    // stronger of the two each, and a limiter at least as tight). With the mode off this is just
    // the preset's values - so switching it off restores them, and switching genres keeps the
    // mode's protection.
    fun withContextFeatures(
        settings: ProcessingSettings,
        basePreset: Preset,
        context: SoundContext?,
    ): ProcessingSettings {
        val contextPreset = context?.let { BuiltInContextPresets.defaultFor(it) }
        val baseLimiterThresholdDb = if (basePreset.limiter.enabled) basePreset.limiter.thresholdDb else 0f
        return settings.copy(
            limiterEnabled = basePreset.limiter.enabled || contextPreset != null,
            limiterThresholdDb =
                if (contextPreset == null) {
                    basePreset.limiter.thresholdDb
                } else {
                    minOf(baseLimiterThresholdDb, contextPreset.limiter.thresholdDb)
                },
            loudnessMaxBoostDb = maxOf(basePreset.loudnessMaxBoostDb, contextPreset?.loudnessMaxBoostDb ?: 0f),
            subsonicCutoffHz = maxOf(basePreset.subsonicCutoffHz, contextPreset?.subsonicCutoffHz ?: 0f),
            virtualBassMix = maxOf(basePreset.virtualBassMix, contextPreset?.virtualBassMix ?: 0f),
        )
    }

    /**
     * The dynamics for [played] (already intensified): an active context only ever tightens the
     * genre's dynamics (lower threshold, higher ratio) and switches the compressor on; the
     * headphone easing, if [easeDynamics], is applied on top of that.
     */
    fun resolveDynamics(
        played: Preset,
        context: SoundContext?,
        easeDynamics: Boolean,
    ): ResolvedDynamics {
        val contextPreset = context?.let { BuiltInContextPresets.defaultFor(it) }
        val baseThresholdDb = contextPreset?.let { minOf(played.mbcThresholdDb, it.mbcThresholdDb) } ?: played.mbcThresholdDb
        val baseRatio = contextPreset?.let { maxOf(played.mbcRatio, it.mbcRatio) } ?: played.mbcRatio
        return ResolvedDynamics(
            mbcEnabled = played.mbcEnabled || contextPreset != null,
            mbcThresholdDb = if (easeDynamics) HeadphoneDynamicsEasing.easedThresholdDb(baseThresholdDb) else baseThresholdDb,
            mbcRatio = if (easeDynamics) HeadphoneDynamicsEasing.easedRatio(baseRatio) else baseRatio,
        )
    }
}
