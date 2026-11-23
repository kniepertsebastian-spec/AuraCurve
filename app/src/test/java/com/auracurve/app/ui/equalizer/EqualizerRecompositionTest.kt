package com.auracurve.app.ui.equalizer

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.correction.BuiltInCorrectionProfiles
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.ui.main.EqCorrectionUiState
import com.auracurve.app.ui.main.EqDeviceUiState
import com.auracurve.app.ui.main.EqPresetUiState
import com.auracurve.app.ui.main.EqShapingUiState
import com.auracurve.app.ui.theme.AuraCurveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Roadmap 3: what changes many times a second (the settings while a slider moves, the system
// volume) is handed to EqualizerContent as a function and read only where it is drawn. The caller
// - MainScreen in the app - must not recompose when it changes, while the curve still follows.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EqualizerRecompositionTest {
    @get:Rule
    val compose = createComposeRule()

    private val bands =
        listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000).mapIndexed { index, hz ->
            EqualizerBandCapabilities(index = index, centerFreqHz = hz, minGainDb = -15f, maxGainDb = 15f)
        }

    private fun settingsPeakingAt(gainDb: Float) =
        ProcessingSettings(bandGainsDb = bands.associate { it.index to if (it.index == 0) gainDb else 0f })

    @Test
    fun sliderAndVolumeChangesDoNotRecomposeTheCaller() {
        val settings = mutableStateOf(settingsPeakingAt(6f))
        val level = mutableFloatStateOf(-12f)
        var callerCompositions = 0
        compose.setContent {
            AuraCurveTheme {
                SideEffect { callerCompositions++ }
                EqualizerContent(
                    device =
                        EqDeviceUiState(
                            engineState = AudioEngineState.Active(sessionId = 42),
                            route = AudioRoute(AudioDeviceType.BLUETOOTH, "Headphones", "bt_1"),
                            bands = bands,
                            preEqActive = false,
                            audioSessions = emptyList(),
                            activeAudioSession = null,
                            appProfileBound = false,
                            isOwnPlayerSession = true,
                            deviceKindPrompt = null,
                        ),
                    presets = EqPresetUiState(BuiltInPresets.all.first(), emptyList(), moreIntensity = false, isDirty = false),
                    correction =
                        EqCorrectionUiState(
                            activeProfile = BuiltInCorrectionProfiles.None,
                            allProfiles = listOf(BuiltInCorrectionProfiles.None),
                            suggested = null,
                            effectiveHeadphoneAcoustics = true,
                            headphonePower = HeadphonePower(),
                        ),
                    shaping = EqShapingUiState(activeGoal = SoundGoal.entries.first(), activeContext = null, compare = null),
                    genrePresets = BuiltInGenrePresets.all,
                    pathLabel = null,
                    settings = { settings.value },
                    currentLevelDb = { level.floatValue },
                    parametricBands = { emptyList() },
                    actions = EqualizerActions(),
                )
            }
        }
        compose.onNodeWithContentDescription("+6.0", substring = true).assertIsDisplayed()
        val before = callerCompositions

        // A slider move and a few volume steps, as they arrive from the view model.
        repeat(5) { step ->
            settings.value = settingsPeakingAt(7f + step)
            level.floatValue = -12f - step * 3f
            compose.waitForIdle()
        }

        assertEquals("the caller recomposed for a slider or volume change", before, callerCompositions)
        // ... and the curve still follows the newest settings.
        compose.onNodeWithContentDescription("+11.0", substring = true).assertIsDisplayed()
    }
}
