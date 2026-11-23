package com.auracurve.app.profile

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// Outputs (by stable route id) for which the user answered a headphone-correction suggestion with
// "Not now". Kept across app starts, so a device the user deliberately leaves uncorrected is not
// asked about on every connection; picking a profile for it later works as before.
interface CorrectionSuggestionDismissals {
    suspend fun isDismissed(routeId: String): Boolean

    fun dismiss(routeId: String)
}

class InMemoryCorrectionSuggestionDismissals : CorrectionSuggestionDismissals {
    private val dismissed = mutableSetOf<String>()

    override suspend fun isDismissed(routeId: String): Boolean = routeId in dismissed

    override fun dismiss(routeId: String) {
        dismissed += routeId
    }
}

// Kept in a DataStore, read and written off the main thread (roadmap 2, M-5); the old
// SharedPreferences file of the same name is moved over once on the first read. Device-specific:
// not part of the backup (backup_rules.xml).
@Singleton
class DataStoreCorrectionSuggestionDismissals internal constructor(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : CorrectionSuggestionDismissals {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private constructor(context: Context, scope: CoroutineScope) : this(createStore(context, scope), scope)

    // Answers given in this process count at once, before their write has reached the disk.
    private val dismissedNow = ConcurrentHashMap.newKeySet<String>()

    override suspend fun isDismissed(routeId: String): Boolean {
        if (routeId in dismissedNow) return true
        return try {
            store.data.first()[booleanPreferencesKey(routeId)] == true
        } catch (e: IOException) {
            // Unreadable: asking once more is the harmless side.
            Log.w(TAG, "Dismissed suggestions not read", e)
            false
        }
    }

    override fun dismiss(routeId: String) {
        dismissedNow += routeId
        scope.launch {
            try {
                store.edit { it[booleanPreferencesKey(routeId)] = true }
            } catch (e: IOException) {
                Log.w(TAG, "Dismissed suggestion not saved", e)
            }
        }
    }

    companion object {
        const val PREFS_NAME = "correction_suggestion_dismissals"
        private const val TAG = "CorrectionDismissals"

        internal fun createStore(
            context: Context,
            scope: CoroutineScope,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(context, PREFS_NAME)),
                scope = scope,
                produceFile = { context.preferencesDataStoreFile(PREFS_NAME) },
            )
    }
}
