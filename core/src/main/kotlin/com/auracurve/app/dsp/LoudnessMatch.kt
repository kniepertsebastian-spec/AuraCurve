package com.auracurve.app.dsp

import kotlin.math.log10
import kotlin.math.pow

// Fair original/EQ comparison: an EQ that sounds louder overall quickly seems "better".
// This estimate determines how much louder (positive) or quieter (negative) the EQ side is
// compared with the original, and splits the matching so that the louder side always gets
// quieter (the effect can only attenuate, never amplify). An estimate from the curve, not a
// measurement.
object LoudnessMatch {
    private const val MAX_ATTENUATION_DB = 15f

    // Weighting by hearing sensitivity (A-weighting, as a power ratio). Music has roughly the
    // same energy per octave, and a graphic EQ consists of ~octave-wide bands - so each band
    // counts with the weight of its centre frequency.
    fun aWeightPower(frequencyHz: Float): Double {
        val f2 = frequencyHz.toDouble().pow(2)
        val ra =
            (12194.0.pow(2) * f2 * f2) /
                ((f2 + 20.6.pow(2)) * kotlin.math.sqrt((f2 + 107.7.pow(2)) * (f2 + 737.9.pow(2))) * (f2 + 12194.0.pow(2)))
        return ra * ra
    }

    /** Power-weighted mean of the band gains in dB (0 for an empty list). */
    fun meanGainDb(bands: List<Pair<Float, Float>>): Float {
        if (bands.isEmpty()) return 0f
        var weightSum = 0.0
        var powerSum = 0.0
        bands.forEach { (frequencyHz, gainDb) ->
            val weight = aWeightPower(frequencyHz)
            weightSum += weight
            powerSum += weight * 10.0.pow(gainDb / 10.0)
        }
        if (weightSum <= 0.0) return 0f
        return (10.0 * log10(powerSum / weightSum)).toFloat()
    }

    /** Estimated level difference of EQ versus original: band mean plus input gain. */
    fun eqLevelDeltaDb(
        bands: List<Pair<Float, Float>>,
        inputGainDb: Float,
    ): Float = meanGainDb(bands) + inputGainDb

    /** How much each side is lowered so both are equally loud (both values <= 0). */
    data class Plan(
        val originalGainDb: Float,
        val eqExtraGainDb: Float,
    )

    fun plan(eqLevelDeltaDb: Float): Plan {
        val delta = eqLevelDeltaDb.coerceIn(-MAX_ATTENUATION_DB, MAX_ATTENUATION_DB)
        return if (delta >= 0f) Plan(originalGainDb = 0f, eqExtraGainDb = -delta) else Plan(originalGainDb = delta, eqExtraGainDb = 0f)
    }
}
