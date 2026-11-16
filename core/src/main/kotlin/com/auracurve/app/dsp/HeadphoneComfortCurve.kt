package com.auracurve.app.dsp

import com.auracurve.app.preset.TargetPoint

// Chat follow-up to item 1 ("headphone mode" should really do something, not just be a
// database flag): real, audible crossfeed/bass-mono summing (items 2/5) is technically not
// possible over the playback path in use (AndroidAudioEngine: Android's system
// Equalizer/DynamicsProcessing effects attached to other apps' sessions) - these system
// effects only know band EQ and compressor/limiter/MBC, no custom algorithms. This curve is
// the pragmatic substitute: a fixed, simple EQ adjustment that runs exactly over that
// already working path (MainViewModel.combinedCurve() -> EqualizerInterpolator ->
// AndroidAudioEngine.applyInternal() -> Equalizer.setBandLevel()).
//
// Reasoning behind the curve: headphones sit closer to the ear than speakers - presence and
// sibilance (~3-6 kHz) tend to be more tiring there (no room reflections/distance to smooth
// them), and small headphone drivers often benefit from a little more bass because they lack
// the "room gain" speakers get from being near walls and floor. Not a scientifically derived
// target curve, just a sensible fixed starting point - similar to how many headphone apps
// implement a "headphone mode" as a noticeable bass/presence tilt rather than a 1:1
// recreation of crossfeed.
object HeadphoneComfortCurve {
    const val DEFAULT_BASS_DB = 2.5f

    // Bass boost is selectable (headphone power); the presence dip stays fixed.
    fun curve(bassDb: Float): List<TargetPoint> =
        listOf(
            TargetPoint(20f, bassDb),
            TargetPoint(60f, bassDb),
            TargetPoint(150f, bassDb * 0.4f),
            TargetPoint(300f, 0f),
            TargetPoint(3000f, 0f),
            TargetPoint(4500f, -1.5f),
            TargetPoint(8000f, -1f),
            TargetPoint(16000f, 0f),
        )

    val curve: List<TargetPoint> = curve(DEFAULT_BASS_DB)
}
