package com.auracurve.app.dsp

// Prioritised implementation list from the Nothing/Dirac Opteo chat, item 2:
// Sub-bass below a cutoff frequency is hardly perceived spatially, but level/phase
// differences between L/R there often make the bass "wobbly" instead of punchy on in-ears
// (no acoustic crosstalk as with speakers). This class sums the bass of both channels into
// one common mono signal, while everything above the cutoff stays untouched in stereo.
//
// Signal flow (mid/side):
// 1. Mid M = (L+R)/2 and side S = (L-R)/2.
// 2. Only the side is high-pass filtered (24 dB/octave, two Butterworth stages) - so no L/R
//    difference is left below the cutoff.
// 3. L = M + HP(S), R = M - HP(S).
//
// The mid is never filtered: mono material (the normal case for bass) comes out unchanged.
// The earlier version (low-pass + high-pass per channel, then summed) cancelled everything
// exactly at the cutoff - two 2nd-order Butterworth filters are 180° apart there, so at
// 120 Hz there was a deep hole right in the kick.
//
// Like BassExciter/BiquadFilterDesigner a reference implementation, only checked offline
// against synthetic test signals (see BassMonoSummerTest), not verified on real hardware.
// Works on normalised float samples (-1..1), not on PCM16 short values - :core stays free of
// Android.
data class BassMonoSummerSettings(
    val enabled: Boolean = false,
    // Only content below this frequency is summed to mono.
    val cutoffHz: Float = 120f,
) {
    fun clamped(): BassMonoSummerSettings = copy(cutoffHz = cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ))

    companion object {
        const val MIN_CUTOFF_HZ = 40f
        const val MAX_CUTOFF_HZ = 200f
    }
}

data class StereoSample(
    val left: Float,
    val right: Float,
)

class BassMonoSummer(
    initialSettings: BassMonoSummerSettings = BassMonoSummerSettings(),
) {
    private var settings = initialSettings.clamped()

    private val sideHighpassStates = listOf(BiquadFilterState(), BiquadFilterState())

    private var coefficientsSampleRateHz = -1f
    private var coefficientsCutoffHz = -1f
    private var highpassCoefficients = SILENT_COEFFICIENTS

    // Working array of processBlock(); it only grows, so the audio thread allocates nothing.
    private var blockSide = DoubleArray(0)

    fun updateSettings(newSettings: BassMonoSummerSettings) {
        settings = newSettings.clamped()
    }

    // Must be called with exactly one stereo sample pair per call, in stream
    // order - the filter states carry IIR state from one call to the next (see
    // BiquadFilterState).
    fun process(
        left: Float,
        right: Float,
        sampleRateHz: Float,
    ): StereoSample {
        val currentSettings = settings
        if (!currentSettings.enabled) return StereoSample(left, right)

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)

        val mid = (left + right) * 0.5f
        var side = (left - right) * 0.5f
        for (state in sideHighpassStates) side = state.process(side, highpassCoefficients)

        return StereoSample(left = mid + side, right = mid - side)
    }

    /**
     * The first [count] frames of [left] and [right] at once, in place: the block form of
     * [process], with the same result and the same state - and without an object per frame, which
     * [process] needs for its return value. What the player's audio thread uses.
     */
    fun processBlock(
        left: DoubleArray,
        right: DoubleArray,
        count: Int,
        sampleRateHz: Float,
    ) {
        val currentSettings = settings
        if (!currentSettings.enabled || count <= 0) return

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)
        if (blockSide.size < count) blockSide = DoubleArray(count)
        val side = blockSide

        // The mid stays in `left` until both are put together again.
        for (n in 0 until count) {
            val l = left[n]
            val r = right[n]
            left[n] = (l + r) * 0.5
            side[n] = (l - r) * 0.5
        }
        sideHighpassStates[0].processBlock(side, count, highpassCoefficients)
        sideHighpassStates[1].processBlock(side, count, highpassCoefficients)
        for (n in 0 until count) {
            val mid = left[n]
            val s = side[n]
            left[n] = mid + s
            right[n] = mid - s
        }
    }

    fun reset() {
        sideHighpassStates.forEach { it.reset() }
    }

    private fun ensureCoefficients(
        cutoffHz: Float,
        sampleRateHz: Float,
    ) {
        if (coefficientsSampleRateHz == sampleRateHz && coefficientsCutoffHz == cutoffHz) return

        highpassCoefficients =
            BiquadFilterDesigner.design(
                ParametricFilter(ParametricFilterType.HIGH_PASS, cutoffHz, 0f, BUTTERWORTH_Q),
                sampleRateHz,
            )
        coefficientsSampleRateHz = sampleRateHz
        coefficientsCutoffHz = cutoffHz
    }

    companion object {
        private const val BUTTERWORTH_Q = 0.70710678f
        private val SILENT_COEFFICIENTS = BiquadCoefficients(0f, 0f, 0f, 0f, 0f)
    }
}
