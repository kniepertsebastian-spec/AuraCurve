package com.auracurve.app.dsp

import kotlin.math.PI
import kotlin.math.tan

// Two chains of four first-order allpasses each, whose outputs are 90° apart in the bass range
// (IIR Hilbert pair). A tone A·cos(θ) thus becomes I ≈ A·cos(θ + φ) and Q ≈ A·sin(θ + φ): the
// envelope sqrt(I² + Q²) is exactly A without smoothing and without ripple, and I/A, Q/A are the
// cosine and sine of the instantaneous phase. The harmonic generator in BassExciter builds on
// this.
//
// Both chains are pure allpasses: magnitude 1 at every frequency, only the phase differs.
// φ itself (the common phase rotation) depends on frequency and does not matter for generating
// harmonics.
//
// The pole frequencies are optimised offline (minimax on the deviation from 90° between 16 and
// 800 Hz at 48 kHz). Thanks to the bilinear transform with prewarping they stay in the same place
// at every sample rate; the deviation from 90° in the 16–800 Hz range is below 0.2° at
// 44.1/48 kHz and below 0.4° at 96/192 kHz (HilbertAllpassPairTest).
class HilbertAllpassPair {
    private val coefficientsI = DoubleArray(SECTIONS)
    private val coefficientsQ = DoubleArray(SECTIONS)
    private val previousInputI = DoubleArray(SECTIONS)
    private val previousOutputI = DoubleArray(SECTIONS)
    private val previousInputQ = DoubleArray(SECTIONS)
    private val previousOutputQ = DoubleArray(SECTIONS)
    private var configuredSampleRateHz = -1f

    /** In-phase output of the last [process] call. */
    var inPhase = 0.0
        private set

    /** Output of the last [process] call, 90° away from [inPhase] in the design band. */
    var quadrature = 0.0
        private set

    fun configure(sampleRateHz: Float) {
        if (sampleRateHz == configuredSampleRateHz) return
        for (k in 0 until SECTIONS) {
            coefficientsI[k] = allpassCoefficient(POLES_I_HZ[k], sampleRateHz)
            coefficientsQ[k] = allpassCoefficient(POLES_Q_HZ[k], sampleRateHz)
        }
        configuredSampleRateHz = sampleRateHz
    }

    // One sample, in stream order (the sections carry state between calls). configure() first.
    fun process(input: Double) {
        inPhase = runChain(input, coefficientsI, previousInputI, previousOutputI)
        quadrature = runChain(input, coefficientsQ, previousInputQ, previousOutputQ)
    }

    /**
     * The first [count] samples of [input] at once: the in-phase output goes to [inPhaseOut], the
     * quadrature output to [quadratureOut]. Same result and same state as [process] per sample.
     * [input] is not changed; the output arrays must not be [input].
     */
    fun processBlock(
        input: DoubleArray,
        count: Int,
        inPhaseOut: DoubleArray,
        quadratureOut: DoubleArray,
    ) {
        if (count <= 0) return
        runChainBlock(input, count, inPhaseOut, coefficientsI, previousInputI, previousOutputI)
        runChainBlock(input, count, quadratureOut, coefficientsQ, previousInputQ, previousOutputQ)
        inPhase = inPhaseOut[count - 1]
        quadrature = quadratureOut[count - 1]
    }

    // Section by section over the whole block, so each section's state stays in local variables.
    private fun runChainBlock(
        input: DoubleArray,
        count: Int,
        output: DoubleArray,
        coefficients: DoubleArray,
        previousInput: DoubleArray,
        previousOutput: DoubleArray,
    ) {
        var source = input
        for (k in 0 until SECTIONS) {
            val a = coefficients[k]
            var lastInput = previousInput[k]
            var lastOutput = previousOutput[k]
            for (n in 0 until count) {
                val x = source[n]
                val y = a * x + lastInput - a * lastOutput
                lastInput = x
                lastOutput = y
                output[n] = y
            }
            previousInput[k] = lastInput
            previousOutput[k] = lastOutput
            source = output
        }
    }

    fun reset() {
        previousInputI.fill(0.0)
        previousOutputI.fill(0.0)
        previousInputQ.fill(0.0)
        previousOutputQ.fill(0.0)
        inPhase = 0.0
        quadrature = 0.0
    }

    private fun runChain(
        input: Double,
        coefficients: DoubleArray,
        previousInput: DoubleArray,
        previousOutput: DoubleArray,
    ): Double {
        var x = input
        for (k in 0 until SECTIONS) {
            // H(z) = (a + z^-1) / (1 + a·z^-1)
            val a = coefficients[k]
            val y = a * x + previousInput[k] - a * previousOutput[k]
            previousInput[k] = x
            previousOutput[k] = y
            x = y
        }
        return x
    }

    companion object {
        const val SECTIONS = 4

        // Design band of the 90° difference; outside it the phase difference drifts away.
        const val BAND_LOW_HZ = 16f
        const val BAND_HIGH_HZ = 800f

        private val POLES_I_HZ = doubleArrayOf(18.67, 80.72, 316.7, 2351.6)
        private val POLES_Q_HZ = doubleArrayOf(5.405, 40.45, 158.7, 685.6)

        // Bilinear transform of the analog allpass (p - s)/(p + s), prewarped so the pole lands at
        // poleHz. The poles sit close to z = 1 (a ≈ -0.998 at 16 Hz), hence double precision.
        private fun allpassCoefficient(
            poleHz: Double,
            sampleRateHz: Float,
        ): Double {
            val t = tan(PI * poleHz.coerceAtMost(sampleRateHz * 0.49) / sampleRateHz)
            return (t - 1.0) / (t + 1.0)
        }
    }
}
