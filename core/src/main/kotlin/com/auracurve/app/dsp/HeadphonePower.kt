package com.auracurve.app.dsp

import kotlinx.serialization.Serializable

// "Headphone power": makes the formerly fixed headphone values (bass +2.5 dB, eased
// dynamics, loudness up to 9 dB) adjustable. DEFAULT matches the old behaviour
// exactly; KNALL (shown as "Boom") is the strong preset (more bass, full dynamics, more
// loudness) - deliberately loud, while the input gain is still limited by the
// headroom protection.
@Serializable
data class HeadphonePower(
    val bassDb: Float = DEFAULT_BASS_DB,
    val easeDynamics: Boolean = true,
    val loudnessMaxDb: Float = DEFAULT_LOUDNESS_DB,
) {
    fun sanitized(): HeadphonePower =
        copy(
            bassDb = bassDb.coerceIn(0f, MAX_BASS_DB),
            loudnessMaxDb = loudnessMaxDb.coerceIn(0f, MAX_LOUDNESS_DB),
        )

    companion object {
        const val DEFAULT_BASS_DB = 2.5f
        const val DEFAULT_LOUDNESS_DB = 9f
        const val MAX_BASS_DB = 9f
        const val MAX_LOUDNESS_DB = 15f

        val DEFAULT = HeadphonePower()
        val KNALL = HeadphonePower(bassDb = 7f, easeDynamics = false, loudnessMaxDb = 12f)
    }
}
