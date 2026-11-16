package com.auracurve.app.preset

// Sound goals: choosing by the desired result instead of by genre. A goal is a small, additive
// correction curve on top of the selected genre preset (like the headphone curve), so it is
// independent of the genre. "Vocals forward" is deliberately subtle: a little more presence
// (2-4 kHz) and less masking from the region around 300 Hz - it does not isolate vocals from the
// mix and does not repair a bad recording. The visible names and descriptions are in the app's
// resources.
enum class SoundGoal(
    val id: String,
    val curve: List<TargetPoint>,
) {
    BALANCED("goal_balanced", emptyList()),
    VOCALS_FORWARD(
        "goal_vocals_forward",
        listOf(
            TargetPoint(100f, 0f),
            TargetPoint(250f, -0.8f),
            TargetPoint(400f, -1.5f),
            TargetPoint(700f, -0.5f),
            TargetPoint(1200f, 0.5f),
            TargetPoint(2500f, 1.8f),
            TargetPoint(3500f, 2.0f),
            TargetPoint(5000f, 0.8f),
            TargetPoint(8000f, 0f),
        ),
    ),
    MORE_POWER(
        "goal_more_power",
        listOf(
            TargetPoint(40f, 2.5f),
            TargetPoint(70f, 3.0f),
            TargetPoint(120f, 2.0f),
            TargetPoint(220f, 0.5f),
            TargetPoint(400f, 0f),
        ),
    ),
    MORE_PUNCH(
        "goal_more_punch",
        listOf(
            TargetPoint(60f, 0.5f),
            TargetPoint(100f, 2.5f),
            TargetPoint(160f, 1.0f),
            TargetPoint(300f, 0f),
            TargetPoint(2500f, 0f),
            TargetPoint(4000f, 1.5f),
            TargetPoint(7000f, 0f),
        ),
    ),
    LESS_HARSH(
        "goal_less_harsh",
        listOf(
            TargetPoint(1500f, 0f),
            TargetPoint(3000f, -1.5f),
            TargetPoint(5000f, -2.5f),
            TargetPoint(8000f, -2.0f),
            TargetPoint(12000f, -1.0f),
        ),
    ),
    LESS_BASS(
        "goal_less_bass",
        listOf(
            TargetPoint(40f, -3.5f),
            TargetPoint(80f, -3.0f),
            TargetPoint(150f, -2.0f),
            TargetPoint(300f, -0.5f),
            TargetPoint(600f, 0f),
        ),
    ),
    ;

    companion object {
        // Unknown or missing ids (older saved settings) mean "no goal".
        fun fromId(id: String?): SoundGoal = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}
