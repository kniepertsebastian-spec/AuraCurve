package com.auracurve.app.settings

import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.ParametricBand
import kotlinx.serialization.Serializable

// The full live editing state - which preset it's based on, whether it's been
// manually edited since (M2: "mark a manually changed preset as custom
// automatically"), and the actual processing values (including any manual
// per-band tweaks that were never explicitly saved as their own preset).
// Persisted as a whole so a restart never silently drops in-progress edits -
// roadmap-2026.md M2's acceptance criterion is "all settings survive an app,
// process and device restart", not just saved/named presets.
@Serializable
data class LiveSettings(
    val activePresetId: String,
    val isDirty: Boolean,
    val processingSettings: ProcessingSettings,
    // M3: which CorrectionProfile ("My headphones") is active, alongside the
    // voicing (activePresetId, "sound style") this was already tracking. Defaulted
    // rather than made nullable/required so JSON saved before M3 (missing this
    // field) still decodes - kotlinx.serialization applies the default for a
    // field absent from the source JSON.
    val activeCorrectionProfileId: String = "correction_none",
    // Listening-context mode ("car" / "Bluetooth speaker", SoundContext.name) layered
    // over the active preset, null = off. Defaulted so settings saved before it
    // existed still decode.
    val activeContext: String? = null,
    // True when route detection switched the mode on (so it switches off again when
    // that route goes away), false when the user chose it.
    val activeContextAutomatic: Boolean = false,
    // "Headphone power": bass/dynamics/loudness in headphone mode. The defaults are
    // the old fixed behaviour, so older JSON stays readable.
    val headphonePower: HeadphonePower = HeadphonePower(),
    // Chosen sound goal (SoundGoal.id), null = "Balanced". Defaulted for older saved settings.
    val activeGoal: String? = null,
    // P5-3: the free bands of the parametric mode. Defaulted for older saved settings.
    val parametricBands: List<ParametricBand> = emptyList(),
    // "More intensity" switch (replaces the five intensity steps). Defaulted for older saved settings.
    val moreIntensity: Boolean = false,
)
