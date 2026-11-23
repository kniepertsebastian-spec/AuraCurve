package com.auracurve.app.ui.equalizer

import androidx.compose.runtime.Immutable
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.audio.DeviceKind
import com.auracurve.app.autoeq.AutoEqCatalogEntry
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.dsp.PreEqResponseMode
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.ui.main.CompareSide

/**
 * Everything the equalizer screen can ask for, in one object (roadmap 3). The caller creates it
 * once (MainScreen remembers it), so the sections that take it are skipped when nothing they show
 * has changed, instead of being handed dozens of single lambdas.
 *
 * A null callback hides the control it belongs to.
 */
@Immutable
class EqualizerActions(
    val onMasterToggled: (Boolean) -> Unit = {},
    val onOpenSourcePicker: () -> Unit = {},
    val onBypassToggled: (Boolean) -> Unit = {},
    val onRetryAttach: () -> Unit = {},
    // Presets
    val onPresetSelected: (Preset) -> Unit = {},
    val onMoreIntensityChanged: (Boolean) -> Unit = {},
    val onResetToActivePreset: () -> Unit = {},
    val onSaveAsNewRequest: () -> Unit = {},
    val onDuplicatePreset: (Preset) -> Unit = {},
    val onRenamePresetRequest: (Preset) -> Unit = {},
    val onDeletePresetRequest: (Preset) -> Unit = {},
    val onSharePreset: (Preset) -> Unit = {},
    val onImportPresetLinkRequested: () -> Unit = {},
    // Correction and headphones
    val onCorrectionProfileSelected: (CorrectionProfile) -> Unit = {},
    val onImportCorrectionProfileRequested: () -> Unit = {},
    val onExportCorrectionProfile: (CorrectionProfile) -> Unit = {},
    val onAcceptSuggestedCorrectionProfile: () -> Unit = {},
    val onDismissSuggestedCorrectionProfile: () -> Unit = {},
    val onHeadphoneAcousticsChanged: (Boolean) -> Unit = {},
    val onHeadphonePowerChanged: (HeadphonePower) -> Unit = {},
    // The bundled AutoEq catalogue: searching it by name and picking a model. Null hides the search.
    val onSearchHeadphones: (suspend (String) -> List<AutoEqCatalogEntry>)? = null,
    val onHeadphoneChosen: (AutoEqCatalogEntry) -> Unit = {},
    // Shaping: goal, context, modules, comparison
    val onGoalSelected: (SoundGoal) -> Unit = {},
    val onContextModeChanged: (SoundContext?) -> Unit = {},
    val onLoudnessCompensationChanged: (Boolean) -> Unit = {},
    val onSubsonicFilterChanged: (Boolean) -> Unit = {},
    val onVirtualBassChanged: (Boolean) -> Unit = {},
    val onStartCompare: () -> Unit = {},
    val onCompareSide: (CompareSide) -> Unit = {},
    val onEndCompare: () -> Unit = {},
    // Fine tuning
    val onMacroBassChanged: (Float) -> Unit = {},
    val onMacroPunchChanged: (Float) -> Unit = {},
    val onMacroHaerteChanged: (Float) -> Unit = {},
    val onBandGainChanged: (bandIndex: Int, gainDb: Float) -> Unit = { _, _ -> },
    val onAddParametricBand: () -> Unit = {},
    val onChangeParametricBand: (Int, ParametricBand) -> Unit = { _, _ -> },
    val onRemoveParametricBand: (Int) -> Unit = {},
    val onHighResolutionEqChanged: ((Boolean) -> Unit)? = null,
    val onPreEqResponseModeChanged: ((PreEqResponseMode) -> Unit)? = null,
    // Output and sessions
    val onRenameRoute: (String) -> Unit = {},
    val onSelectAudioSession: (AudioSession) -> Unit = {},
    val onAppProfileBoundChanged: ((Boolean) -> Unit)? = null,
    val onAnswerDeviceKind: (DeviceKind) -> Unit = {},
    val onDismissDeviceKindPrompt: () -> Unit = {},
)
