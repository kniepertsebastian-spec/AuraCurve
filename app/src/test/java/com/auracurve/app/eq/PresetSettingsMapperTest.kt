package com.auracurve.app.eq

import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.context.SoundContext
import com.auracurve.app.dsp.HeadphoneDynamicsEasing
import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.BuiltInPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetSettingsMapperTest {
    private val preset = BuiltInPresets.CleanPunch

    @Test
    fun noBoost_givesARealZeroInputGain_neverNegativeZero() {
        val gain = PresetSettingsMapper.safetyScaledInputGainDb(0f, context = null)

        assertEquals(0f.toBits(), gain.toBits())
    }

    @Test
    fun inputGain_preCancelsHalfTheBoost_andMoreOnSmallSpeakers() {
        assertEquals(-5f, PresetSettingsMapper.safetyScaledInputGainDb(10f, context = null), 0.0001f)
        assertEquals(-5f, PresetSettingsMapper.safetyScaledInputGainDb(10f, SoundContext.CAR), 0.0001f)
        assertEquals(-8f, PresetSettingsMapper.safetyScaledInputGainDb(10f, SoundContext.BLUETOOTH_SPEAKER), 0.0001f)
        assertEquals(-8f, PresetSettingsMapper.safetyScaledInputGainDb(10f, SoundContext.SOUNDBAR), 0.0001f)
    }

    @Test
    fun automaticInputGain_followsThePeakBoost_andCutsAloneNeedNone() {
        assertEquals(-3f, PresetSettingsMapper.automaticInputGainDb(mapOf(0 to 2f, 1 to 6f, 2 to -4f), context = null), 0.0001f)
        assertEquals(0f.toBits(), PresetSettingsMapper.automaticInputGainDb(mapOf(0 to -3f, 1 to -6f), context = null).toBits())
        assertEquals(0f.toBits(), PresetSettingsMapper.automaticInputGainDb(emptyMap(), context = null).toBits())
    }

    @Test
    fun withPreset_takesMacrosLimiterAndDynamicsFromThePreset() {
        val settings = PresetSettingsMapper.withPreset(ProcessingSettings(), preset, moreIntensity = false, context = null)

        assertEquals(preset.macroBassDb, settings.macroBassDb, 0f)
        assertEquals(preset.macroPunchDb, settings.macroPunchDb, 0f)
        assertEquals(preset.macroHaerteDb, settings.macroHaerteDb, 0f)
        assertEquals(preset.limiter.enabled, settings.limiterEnabled)
        assertEquals(preset.limiter.thresholdDb, settings.limiterThresholdDb, 0f)
        assertEquals(preset.mbcEnabled, settings.mbcEnabled)
        assertEquals(preset.mbcThresholdDb, settings.mbcThresholdDb, 0f)
        assertEquals(preset.mbcRatio, settings.mbcRatio, 0f)
        assertEquals(-(preset.requestedHeadroomDb * 0.5f), settings.inputGainDb, 0.0001f)
    }

    @Test
    fun withPreset_inAContext_keepsTheTighterLimiterAndTheStrongerFeatures() {
        val context = BuiltInContextPresets.defaultFor(SoundContext.CAR)

        val settings = PresetSettingsMapper.withPreset(ProcessingSettings(), preset, moreIntensity = false, SoundContext.CAR)

        assertTrue(settings.limiterEnabled)
        assertTrue(settings.limiterThresholdDb <= context.limiter.thresholdDb)
        assertTrue(settings.loudnessMaxBoostDb >= context.loudnessMaxBoostDb)
        assertTrue(settings.subsonicCutoffHz >= context.subsonicCutoffHz)
        assertTrue(settings.virtualBassMix >= context.virtualBassMix)
    }

    @Test
    fun withContextFeatures_withTheModeOff_isJustThePresetsOwnValues() {
        val settings = PresetSettingsMapper.withContextFeatures(ProcessingSettings(), preset, context = null)

        assertEquals(preset.limiter.enabled, settings.limiterEnabled)
        assertEquals(preset.limiter.thresholdDb, settings.limiterThresholdDb, 0f)
        assertEquals(preset.loudnessMaxBoostDb, settings.loudnessMaxBoostDb, 0f)
        assertEquals(preset.subsonicCutoffHz, settings.subsonicCutoffHz, 0f)
        assertEquals(preset.virtualBassMix, settings.virtualBassMix, 0f)
    }

    @Test
    fun resolveDynamics_withoutContextOrEasing_isThePresetsOwn() {
        val dynamics = PresetSettingsMapper.resolveDynamics(preset, context = null, easeDynamics = false)

        assertEquals(ResolvedDynamics(preset.mbcEnabled, preset.mbcThresholdDb, preset.mbcRatio), dynamics)
    }

    @Test
    fun resolveDynamics_aContextOnlyTightens_andSwitchesTheCompressorOn() {
        val context = BuiltInContextPresets.defaultFor(SoundContext.SOUNDBAR)

        val dynamics = PresetSettingsMapper.resolveDynamics(preset, SoundContext.SOUNDBAR, easeDynamics = false)

        assertTrue(dynamics.mbcEnabled)
        assertEquals(minOf(preset.mbcThresholdDb, context.mbcThresholdDb), dynamics.mbcThresholdDb, 0f)
        assertEquals(maxOf(preset.mbcRatio, context.mbcRatio), dynamics.mbcRatio, 0f)
    }

    @Test
    fun resolveDynamics_headphoneEasing_isAppliedOnTopOfTheBase() {
        val dynamics = PresetSettingsMapper.resolveDynamics(preset, context = null, easeDynamics = true)

        assertEquals(HeadphoneDynamicsEasing.easedThresholdDb(preset.mbcThresholdDb), dynamics.mbcThresholdDb, 0f)
        assertEquals(HeadphoneDynamicsEasing.easedRatio(preset.mbcRatio), dynamics.mbcRatio, 0f)
    }

    @Test
    fun intensified_onlyChangesThePresetWhenSwitchedOn() {
        assertEquals(preset, PresetSettingsMapper.intensified(preset, moreIntensity = false))
    }
}
