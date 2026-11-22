package com.auracurve.app.ui.equalizer

import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.correction.BuiltInCorrectionProfiles
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.SoundGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerCurveModelTest {
    private val bands =
        listOf(1_000, 62, 250).mapIndexed { index, hz ->
            EqualizerBandCapabilities(index = index, centerFreqHz = hz, minGainDb = -15f, maxGainDb = 15f)
        }

    private fun model(
        settings: ProcessingSettings = ProcessingSettings(),
        bands: List<EqualizerBandCapabilities> = this.bands,
    ) = EqualizerCurveModel.of(
        settings = settings,
        bands = bands,
        activePreset = BuiltInPresets.all.first(),
        activeCorrectionProfile = BuiltInCorrectionProfiles.None,
        activeGoal = SoundGoal.entries.first(),
        activeContext = null,
        headphonePower = HeadphonePower(),
        effectiveHeadphoneAcoustics = false,
        currentLevelDb = -12f,
        isOwnPlayerSession = true,
        preEqActive = false,
    )

    @Test
    fun curvePoints_areTheAppliedBandGains_sortedByFrequency() {
        val result = model(ProcessingSettings(bandGainsDb = mapOf(0 to 3f, 1 to 6f, 2 to -2f)))

        assertEquals(listOf(62f, 250f, 1_000f), result.curvePoints.map { it.frequencyHz })
        assertEquals(listOf(6f, -2f, 3f), result.curvePoints.map { it.gainDb })
    }

    @Test
    fun withoutBands_theCurveFallsBackToTheCombinedTargetCurve() {
        val result = model(bands = emptyList())

        assertTrue(result.graphBands.isEmpty())
        assertTrue("the preset's target curve is drawn until the device reports its bands", result.curvePoints.isNotEmpty())
    }

    @Test
    fun graphBands_keepTheDeviceOrder_andTheirRange() {
        val result = model(ProcessingSettings(bandGainsDb = mapOf(0 to 3f)))

        assertEquals(listOf(0, 1, 2), result.graphBands.map { it.index })
        assertEquals(3f, result.graphBands[0].gainDb, 0f)
        assertEquals(0f, result.graphBands[1].gainDb, 0f)
        assertEquals(-15f, result.graphBands[0].minGainDb, 0f)
        assertEquals(15f, result.graphBands[0].maxGainDb, 0f)
    }

    @Test
    fun theSameInputsGiveAnEqualModel_soRememberCanSkipTheWork() {
        assertEquals(model(), model())
    }
}
