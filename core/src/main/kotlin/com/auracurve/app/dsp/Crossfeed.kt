package com.auracurve.app.dsp

// STATUS: finished and tested (CrossfeedTest), but not connected anywhere. Whether the class
// is used through the player's own playback path or deleted is still open; until then it is
// deliberately kept.
//
// Prioritised implementation list from the Nothing/Dirac Opteo chat, item 5:
// Through speakers each ear hears both channels - delayed, attenuated and filtered by the
// path around the head ("acoustic crosstalk"). With headphones this is missing entirely:
// each ear gets only its own channel, which can feel unnatural and tiring over time.
// Crossfeed simulates the missing crosstalk by mixing in a low-pass filtered part of the
// other channel (because of diffraction around the head, natural crosstalk matters mainly
// for low and mid frequencies, not for treble - hence the low-pass, similar to the bs2b
// algorithm with a typical cutoff of ~700 Hz).
//
// Deliberately *not* a replacement for BassMonoSummer (item 2): that one sums the bass below
// a very low cutoff to true mono (solving phase problems), while crossfeed here only blends in
// a *part* of the other channel over a much wider frequency range (it changes the stereo image
// rather than replacing it) - different purposes, each usable independently.
//
// Like BassMonoSummer a reference implementation, only checked offline against synthetic
// test signals (see CrossfeedTest), not verified on real hardware. According to the app logic
// it should only be active when MainViewModel.effectiveHeadphoneAcoustics (item 1) is true -
// but this pure DSP class does not know AudioRoute (that would pull an Android dependency
// into :core) and simply relies on `enabled`, which the caller sets accordingly.
data class CrossfeedSettings(
    val enabled: Boolean = false,
    val cutoffHz: Float = 700f,
    // 0..1: how strongly the other channel is mixed in.
    val amount: Float = 0.5f,
) {
    fun clamped(): CrossfeedSettings =
        copy(
            cutoffHz = cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ),
            amount = amount.coerceIn(0f, 1f),
        )

    companion object {
        const val MIN_CUTOFF_HZ = 300f
        const val MAX_CUTOFF_HZ = 1200f
    }
}

class Crossfeed(
    initialSettings: CrossfeedSettings = CrossfeedSettings(),
) {
    private var settings = initialSettings.clamped()

    private val leftLowpassState = BiquadFilterState()
    private val rightLowpassState = BiquadFilterState()

    private var configuredSampleRateHz = -1f
    private var configuredCutoffHz = -1f
    private var lowpassCoefficients = SILENT_COEFFICIENTS

    fun updateSettings(newSettings: CrossfeedSettings) {
        settings = newSettings.clamped()
    }

    // Must be called with exactly one stereo sample pair per call, in stream
    // order - see BiquadFilterState.
    fun process(
        left: Float,
        right: Float,
        sampleRateHz: Float,
    ): StereoSample {
        val currentSettings = settings
        if (!currentSettings.enabled || currentSettings.amount <= 0f) return StereoSample(left, right)

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)

        val leftLow = leftLowpassState.process(left, lowpassCoefficients)
        val rightLow = rightLowpassState.process(right, lowpassCoefficients)

        val crossGain = currentSettings.amount * MAX_CROSS_GAIN
        // Normalizes so a mono (left == right) signal comes back out at
        // roughly the same level it went in - without this, mixing in a
        // correlated cross-channel component would make already-mono content
        // louder purely as a side effect of crossfeed being on.
        val normalization = 1f / (1f + crossGain)

        val outputLeft = (left + crossGain * rightLow) * normalization
        val outputRight = (right + crossGain * leftLow) * normalization

        return StereoSample(
            left = outputLeft.coerceIn(-1f, 1f),
            right = outputRight.coerceIn(-1f, 1f),
        )
    }

    fun reset() {
        leftLowpassState.reset()
        rightLowpassState.reset()
    }

    private fun ensureCoefficients(
        cutoffHz: Float,
        sampleRateHz: Float,
    ) {
        if (configuredSampleRateHz == sampleRateHz && configuredCutoffHz == cutoffHz) return

        lowpassCoefficients =
            BiquadFilterDesigner.design(
                ParametricFilter(ParametricFilterType.LOW_PASS, cutoffHz, 0f, BUTTERWORTH_Q),
                sampleRateHz,
            )
        configuredSampleRateHz = sampleRateHz
        configuredCutoffHz = cutoffHz
    }

    companion object {
        private const val BUTTERWORTH_Q = 0.70710678f

        // Even at amount=1.0, the cross-channel component never fully
        // overtakes the direct signal - bs2b-style crossfeed blends the
        // acoustic-crosstalk illusion in, it doesn't collapse the stereo
        // image to mono.
        private const val MAX_CROSS_GAIN = 0.6f
        private val SILENT_COEFFICIENTS = BiquadCoefficients(0f, 0f, 0f, 0f, 0f)
    }
}
