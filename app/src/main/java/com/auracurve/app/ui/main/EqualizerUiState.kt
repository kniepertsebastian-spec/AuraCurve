package com.auracurve.app.ui.main

import androidx.compose.runtime.Immutable
import com.auracurve.app.audio.AudioEffectDescriptor
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.autoeq.AutoEqCatalogEntry
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.SoundGoal

// Roadmap 3: the equalizer screen's state, grouped by topic. MainScreen collects one flow per group
// instead of thirty single ones, and each group changes rarely. What changes many times a second -
// the processing settings while a slider moves, the system volume, the parametric bands while one
// is dragged - is not in here: the screen reads those deep down in the composables that draw them,
// so a slider does not recompose the whole screen.

/** The output, the engine and the apps the EQ can attach to. */
@Immutable
data class EqDeviceUiState(
    val engineState: AudioEngineState,
    /** The route as shown to the user (with the name they gave it). */
    val route: AudioRoute,
    val bands: List<EqualizerBandCapabilities>,
    /** The high-resolution pre-EQ is running (the device reported its band layout). */
    val preEqActive: Boolean,
    val audioSessions: List<AudioSession>,
    val activeAudioSession: AudioSession?,
    val appProfileBound: Boolean,
    val isOwnPlayerSession: Boolean,
    val deviceKindPrompt: AudioRoute?,
)

/** Which sound is chosen. */
@Immutable
data class EqPresetUiState(
    val activePreset: Preset,
    val customPresets: List<Preset>,
    val moreIntensity: Boolean,
    val isDirty: Boolean,
)

/** Headphone correction and headphone mode. */
@Immutable
data class EqCorrectionUiState(
    val activeProfile: CorrectionProfile,
    val allProfiles: List<CorrectionProfile>,
    val suggested: AutoEqCatalogEntry?,
    val effectiveHeadphoneAcoustics: Boolean,
    val headphonePower: HeadphonePower,
)

/** The overlays on top of the preset: sound goal, listening context and the Original/EQ comparison. */
@Immutable
data class EqShapingUiState(
    val activeGoal: SoundGoal,
    val activeContext: SoundContext?,
    val compare: CompareState?,
)

/** The dialogs the screen may have open. */
@Immutable
data class MainDialogsUiState(
    val showSourcePicker: Boolean,
    val pendingImportPreview: CorrectionProfileImportPreview?,
    val importError: String?,
    val pendingDeletePreset: Preset?,
)

/** The debug list of the device's audio effects. */
@Immutable
data class DebugToolsUiState(
    val showDebugEffects: Boolean,
    val descriptors: List<AudioEffectDescriptor>,
)
