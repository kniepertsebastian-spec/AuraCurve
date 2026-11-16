package com.auracurve.app.dsp

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

// Virtual Bass: psychoacoustic bass extension (compare Waves MaxxBass). A small driver often
// cannot radiate 40 Hz at all, however much it is boosted there. This class generates
// harmonics (octave and twelfth) from the bass content that the driver can reproduce; the ear
// fills in the missing fundamental ("missing fundamental"). The original is kept in full, the
// harmonics are mixed in.
//
// Rebuilt in roadmap 03, P4-3.
// Signal flow per sample:
// 1. Isolate the bass: 20 Hz high-pass (2nd order) and a 4th-order Butterworth low-pass at
//    cutoffHz. The steep slope keeps voices and guitars out of the generator.
// 2. Hilbert pair (HilbertAllpassPair): I and Q are 90° apart. The envelope
//    A = sqrt(I² + Q²) is available immediately and ripple-free; I/A and Q/A are the cos and sin
//    of the phase.
// 3. Transfer curve: Chebyshev polynomials on the normalised phase,
//        A · (w2 · cos 2θ + w3 · cos 3θ)  with  cos 2θ = c² - s²,  cos 3θ = c · (c² - 3s²).
//    A tone yields exactly one 2nd and one 3rd harmonic, nothing else and no DC.
//    thirdHarmonicShare splits the harmonic power: w2 = sqrt(1 - share), w3 = sqrt(share); the
//    total power stays the same when the share is moved.
//    Because the curve scales with A, the harmonic share is the same at every level - the old
//    tanh saturation made it up to 8 dB stronger in quiet passages.
// 4. An envelope follower (attack 10 ms, release 150 ms) on A drives a soft gate: no harmonics
//    below a bass level of -66 dBFS, full strength from -54 dBFS. This way noise, reverb tails
//    and decays do not get a harmonic carpet of their own.
// 5. Band-pass for the harmonics: 65 Hz high-pass (2nd order) against residue below the useful
//    band, low-pass at 3 · cutoffHz (2nd order) so the 3rd harmonic of the highest isolated
//    fundamental just passes but nothing leaks into the mids.
// 6. Mix in with mix · OUTPUT_TRIM.
//
// Reference implementation, checked offline against synthetic test signals (BassExciterTest,
// HilbertAllpassPairTest), not listened to on real hardware. Works on normalised float samples
// (-1..1); :core stays free of Android, the PCM16 conversion is done by PlayerDspPcm16.
data class BassExciterSettings(
    val enabled: Boolean = false,
    // Only bass content below this frequency is used to generate harmonics.
    val cutoffHz: Float = 150f,
    // 0..1: how strongly the generated harmonics are mixed into the original.
    val mix: Float = 0.5f,
    // 0..1: share of the 3rd harmonic in the harmonic power. 0 = octave only (soft),
    // 1 = twelfth only (harder, more "punch"). Default: mostly octave.
    val thirdHarmonicShare: Float = DEFAULT_THIRD_HARMONIC_SHARE,
) {
    fun clamped(): BassExciterSettings =
        copy(
            cutoffHz = cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ),
            mix = mix.coerceIn(0f, 1f),
            thirdHarmonicShare = thirdHarmonicShare.coerceIn(0f, 1f),
        )

    companion object {
        const val MIN_CUTOFF_HZ = 60f
        const val MAX_CUTOFF_HZ = 300f
        const val DEFAULT_THIRD_HARMONIC_SHARE = 0.25f
    }
}

class BassExciter(
    initialSettings: BassExciterSettings = BassExciterSettings(),
) {
    private var settings = initialSettings.clamped()
    private var secondHarmonicWeight = 0.0
    private var thirdHarmonicWeight = 0.0

    private val isolationHighpassState = BiquadFilterState()
    private val isolationLowpassStateA = BiquadFilterState()
    private val isolationLowpassStateB = BiquadFilterState()
    private val hilbert = HilbertAllpassPair()
    private val harmonicHighpassState = BiquadFilterState()
    private val harmonicLowpassState = BiquadFilterState()

    private var coefficientsSampleRateHz = -1f
    private var coefficientsCutoffHz = -1f
    private var isolationHighpass = SILENT_COEFFICIENTS
    private var isolationLowpassA = SILENT_COEFFICIENTS
    private var isolationLowpassB = SILENT_COEFFICIENTS
    private var harmonicHighpass = SILENT_COEFFICIENTS
    private var harmonicLowpass = SILENT_COEFFICIENTS

    private var envelopeAttack = 0.0
    private var envelopeRelease = 0.0
    private var smoothedEnvelope = 0.0

    // Working arrays of processBlock(); they only grow, so the audio thread allocates nothing.
    private var blockBass = DoubleArray(0)
    private var blockInPhase = DoubleArray(0)
    private var blockQuadrature = DoubleArray(0)

    init {
        updateWeights()
    }

    fun updateSettings(newSettings: BassExciterSettings) {
        settings = newSettings.clamped()
        updateWeights()
    }

    // Must be called with exactly one sample per call, in stream order - the filters carry
    // their state from one call to the next.
    fun process(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float = processUnclamped(inputSample, sampleRateHz).coerceIn(-1f, 1f)

    // Same as process(), but may go past full scale: for a float chain with a limiter behind it,
    // where clipping here would only add distortion the limiter cannot undo.
    fun processUnclamped(
        inputSample: Float,
        sampleRateHz: Float,
    ): Float {
        val currentSettings = settings
        if (!currentSettings.enabled || currentSettings.mix <= 0f) return inputSample

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)

        var bass = isolationHighpassState.process(inputSample, isolationHighpass)
        bass = isolationLowpassStateA.process(bass, isolationLowpassA)
        bass = isolationLowpassStateB.process(bass, isolationLowpassB)

        val generated = generateHarmonics(bass.toDouble())
        val harmonics =
            harmonicLowpassState.process(
                harmonicHighpassState.process(generated.toFloat(), harmonicHighpass),
                harmonicLowpass,
            )

        return inputSample + harmonics * OUTPUT_TRIM * currentSettings.mix
    }

    /**
     * The first [count] samples of [samples] at once, in place: the block form of
     * [processUnclamped], with the same result and the same state. What the player's audio thread
     * uses - stage by stage over the block instead of sample by sample through every stage.
     */
    fun processBlock(
        samples: DoubleArray,
        count: Int,
        sampleRateHz: Float,
    ) {
        val currentSettings = settings
        if (!currentSettings.enabled || currentSettings.mix <= 0f || count <= 0) return

        ensureCoefficients(currentSettings.cutoffHz, sampleRateHz)
        if (blockBass.size < count) {
            blockBass = DoubleArray(count)
            blockInPhase = DoubleArray(count)
            blockQuadrature = DoubleArray(count)
        }
        val bass = blockBass
        val inPhase = blockInPhase
        val quadrature = blockQuadrature

        System.arraycopy(samples, 0, bass, 0, count)
        isolationHighpassState.processBlock(bass, count, isolationHighpass)
        isolationLowpassStateA.processBlock(bass, count, isolationLowpassA)
        isolationLowpassStateB.processBlock(bass, count, isolationLowpassB)

        hilbert.processBlock(bass, count, inPhase, quadrature)

        // The harmonics replace the isolated bass in its array: generateHarmonics() per sample.
        val attack = envelopeAttack
        val release = envelopeRelease
        val secondWeight = secondHarmonicWeight
        val thirdWeight = thirdHarmonicWeight
        var smoothed = smoothedEnvelope
        for (n in 0 until count) {
            val i = inPhase[n]
            val q = quadrature[n]
            val envelope = sqrt(i * i + q * q)
            val coefficient = if (envelope > smoothed) attack else release
            smoothed = coefficient * smoothed + (1.0 - coefficient) * envelope
            var generated = 0.0
            if (envelope >= MIN_ENVELOPE) {
                val gate = gateGain(smoothed)
                if (gate > 0.0) {
                    val c = i / envelope
                    val s = q / envelope
                    val cos2 = c * c - s * s
                    val cos3 = c * (c * c - 3.0 * s * s)
                    generated = envelope * gate * (secondWeight * cos2 + thirdWeight * cos3)
                }
            }
            bass[n] = generated
        }
        smoothedEnvelope = smoothed

        harmonicHighpassState.processBlock(bass, count, harmonicHighpass)
        harmonicLowpassState.processBlock(bass, count, harmonicLowpass)

        val amount = (OUTPUT_TRIM * currentSettings.mix).toDouble()
        for (n in 0 until count) samples[n] += bass[n] * amount
    }

    fun reset() {
        isolationHighpassState.reset()
        isolationLowpassStateA.reset()
        isolationLowpassStateB.reset()
        hilbert.reset()
        harmonicHighpassState.reset()
        harmonicLowpassState.reset()
        smoothedEnvelope = 0.0
    }

    private fun generateHarmonics(bass: Double): Double {
        hilbert.process(bass)
        val i = hilbert.inPhase
        val q = hilbert.quadrature
        val envelope = sqrt(i * i + q * q)

        val coefficient = if (envelope > smoothedEnvelope) envelopeAttack else envelopeRelease
        smoothedEnvelope = coefficient * smoothedEnvelope + (1.0 - coefficient) * envelope

        if (envelope < MIN_ENVELOPE) return 0.0
        val gate = gateGain(smoothedEnvelope)
        if (gate <= 0.0) return 0.0

        val c = i / envelope
        val s = q / envelope
        val cos2 = c * c - s * s
        val cos3 = c * (c * c - 3.0 * s * s)
        return envelope * gate * (secondHarmonicWeight * cos2 + thirdHarmonicWeight * cos3)
    }

    private fun updateWeights() {
        val share = settings.thirdHarmonicShare.toDouble()
        secondHarmonicWeight = sqrt(1.0 - share)
        thirdHarmonicWeight = sqrt(share)
    }

    private fun ensureCoefficients(
        cutoffHz: Float,
        sampleRateHz: Float,
    ) {
        if (coefficientsSampleRateHz == sampleRateHz && coefficientsCutoffHz == cutoffHz) return

        isolationHighpass = design(ParametricFilterType.HIGH_PASS, ISOLATION_HIGHPASS_HZ, BUTTERWORTH_Q, sampleRateHz)
        isolationLowpassA = design(ParametricFilterType.LOW_PASS, cutoffHz, BUTTERWORTH4_Q1, sampleRateHz)
        isolationLowpassB = design(ParametricFilterType.LOW_PASS, cutoffHz, BUTTERWORTH4_Q2, sampleRateHz)
        harmonicHighpass = design(ParametricFilterType.HIGH_PASS, HARMONIC_HIGHPASS_HZ, BUTTERWORTH_Q, sampleRateHz)
        harmonicLowpass =
            design(ParametricFilterType.LOW_PASS, cutoffHz * HARMONIC_LOWPASS_RATIO, BUTTERWORTH_Q, sampleRateHz)
        hilbert.configure(sampleRateHz)

        envelopeAttack = exp(-1.0 / (ENVELOPE_ATTACK_SECONDS * sampleRateHz))
        envelopeRelease = exp(-1.0 / (ENVELOPE_RELEASE_SECONDS * sampleRateHz))

        coefficientsSampleRateHz = sampleRateHz
        coefficientsCutoffHz = cutoffHz
    }

    private fun design(
        type: ParametricFilterType,
        frequencyHz: Float,
        q: Float,
        sampleRateHz: Float,
    ): BiquadCoefficients = BiquadFilterDesigner.design(ParametricFilter(type, frequencyHz, 0f, q), sampleRateHz)

    companion object {
        // Q = 1/sqrt(2): maximally flat, no resonant peak at the cutoff.
        private const val BUTTERWORTH_Q = 0.70710678f

        // The two stages of a 4th-order Butterworth low-pass (24 dB/octave).
        private const val BUTTERWORTH4_Q1 = 0.54119610f
        private const val BUTTERWORTH4_Q2 = 1.30656296f

        // Below this, nothing a driver or the ear would turn into bass; keeps rumble and DC out of
        // the generator.
        const val ISOLATION_HIGHPASS_HZ = 20f

        // Harmonic band-pass: 65 Hz passes the octave above a 40 Hz kick (80 Hz) at -1.6 dB.
        const val HARMONIC_HIGHPASS_HZ = 65f

        // ... and 3 x cutoff passes the 3rd harmonic of the highest isolated fundamental at -3 dB.
        const val HARMONIC_LOWPASS_RATIO = 3f

        // Gate on the smoothed bass envelope: closed below -66 dBFS, fully open from -54 dBFS.
        const val GATE_CLOSED_DBFS = -66f
        const val GATE_OPEN_DBFS = -54f
        private val GATE_CLOSED = 10.0.pow(GATE_CLOSED_DBFS / 20.0)
        private val GATE_OPEN = 10.0.pow(GATE_OPEN_DBFS / 20.0)

        const val ENVELOPE_ATTACK_SECONDS = 0.010
        const val ENVELOPE_RELEASE_SECONDS = 0.150

        // Below this the phase of I/Q is noise; nothing to generate from.
        private const val MIN_ENVELOPE = 1e-9

        // Calibrated: the total harmonic level (2nd + 3rd) of a 100 Hz tone (amplitude 0.3) at
        // mix = 1 and the default share matches the 2nd harmonic of the P0-5 exciter (-9.7 dB re
        // input), so existing presets do not get louder (BassExciterTest.totalHarmonicLevel_*).
        private const val OUTPUT_TRIM = 0.35f

        private val SILENT_COEFFICIENTS = BiquadCoefficients(0f, 0f, 0f, 0f, 0f)

        // Smoothstep from closed to open: no corner where the gate starts to act.
        private fun gateGain(envelope: Double): Double {
            val t = ((envelope - GATE_CLOSED) / (GATE_OPEN - GATE_CLOSED)).coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }
    }
}
