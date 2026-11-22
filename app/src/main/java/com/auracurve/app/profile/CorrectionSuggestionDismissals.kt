package com.auracurve.app.profile

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Outputs (by stable route id) for which the user answered a headphone-correction suggestion with
// "Not now". Kept across app starts, so a device the user deliberately leaves uncorrected is not
// asked about on every connection; picking a profile for it later works as before.
interface CorrectionSuggestionDismissals {
    fun isDismissed(routeId: String): Boolean

    fun dismiss(routeId: String)
}

class InMemoryCorrectionSuggestionDismissals : CorrectionSuggestionDismissals {
    private val dismissed = mutableSetOf<String>()

    override fun isDismissed(routeId: String): Boolean = routeId in dismissed

    override fun dismiss(routeId: String) {
        dismissed += routeId
    }
}

@Singleton
class SharedPreferencesCorrectionSuggestionDismissals
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
    ) : CorrectionSuggestionDismissals {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun isDismissed(routeId: String): Boolean = prefs.getBoolean(routeId, false)

        override fun dismiss(routeId: String) {
            prefs.edit().putBoolean(routeId, true).apply()
        }

        companion object {
            const val PREFS_NAME = "correction_suggestion_dismissals"
        }
    }
