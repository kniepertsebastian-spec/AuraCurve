package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint

// Feature idea from the Nothing/Dirac Opteo comparison (chat, not part of roadmap-2026.md):
// a loudness compensation *inspired* by ISO 226/Fletcher-Munson. At low levels the ear
// becomes relatively less sensitive to bass and treble (the equal-loudness contours flatten
// there) - so an EQ curve tuned at one listening level sounds thin when played quietly and
// boomy/shrill when played loud. This is not a real ISO 226 lookup table (that would need SPL
// calibration per device/headphone, which this app does not have); it only approximates the
// *shape* of the effect: a shelf-like bass and treble boost that grows linearly with the
// distance from a reference listening level and is capped from REFERENCE_DROP_DB on.
//
// The result is an ordinary TargetPoint list - composed via CurveComposer.combine() just like
// CorrectionProfile and VoicingPreset curves, no special handling needed in the rest of the
// DSP path.
object LoudnessCompensationCurve {
    // Equal-loudness contours flatten much more in the bass than in the treble - so the
    // bass gets the full boost, the treble only a fraction of it.
    private const val TREBLE_TO_BASS_RATIO = 0.4f

    // Level distance from the reference level at which the compensation reaches its
    // maximum (maxBoostDb) - beyond that it is clamped, not extrapolated further.
    private const val REFERENCE_DROP_DB = 40f

    private const val MIN_MAX_BOOST_DB = 0f
    private const val MAX_MAX_BOOST_DB = 15f

    // currentLevelDb/referenceLevelDb are relative dB values on the same scale as the
    // caller's volume source (e.g. system media volume in dB or measured signal RMS in
    // dBFS) - this function only needs their *difference*, no absolute SPL calibration.
    fun forLevel(
        currentLevelDb: Float,
        referenceLevelDb: Float = 0f,
        maxBoostDb: Float = 9f,
    ): List<TargetPoint> {
        val clampedMaxBoost = maxBoostDb.coerceIn(MIN_MAX_BOOST_DB, MAX_MAX_BOOST_DB)
        val levelDropDb = (referenceLevelDb - currentLevelDb).coerceIn(0f, REFERENCE_DROP_DB)
        if (levelDropDb <= 0f || clampedMaxBoost <= 0f) return emptyList()

        val compensationFraction = levelDropDb / REFERENCE_DROP_DB
        val bassBoostDb = clampedMaxBoost * compensationFraction
        val trebleBoostDb = clampedMaxBoost * TREBLE_TO_BASS_RATIO * compensationFraction

        return listOf(
            TargetPoint(frequencyHz = 20f, gainDb = bassBoostDb),
            TargetPoint(frequencyHz = 60f, gainDb = bassBoostDb),
            TargetPoint(frequencyHz = 200f, gainDb = bassBoostDb * 0.3f),
            TargetPoint(frequencyHz = 1000f, gainDb = 0f),
            TargetPoint(frequencyHz = 8000f, gainDb = trebleBoostDb * 0.5f),
            TargetPoint(frequencyHz = 16000f, gainDb = trebleBoostDb),
            TargetPoint(frequencyHz = 20000f, gainDb = trebleBoostDb),
        )
    }
}
