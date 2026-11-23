package com.auracurve.app.profile

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// The user's own name for an audio device ("Sony WH-1000XM5" -> "My headphones"), keyed by
// the stable route id. Only changes what the app shows; the device profile itself is untouched.
interface DeviceAliasStore {
    val aliases: StateFlow<Map<String, String>>

    // A blank name removes the alias again.
    fun setAlias(
        routeId: String,
        name: String,
    )
}

class InMemoryDeviceAliasStore : DeviceAliasStore {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())
    override val aliases: StateFlow<Map<String, String>> = state.asStateFlow()

    override fun setAlias(
        routeId: String,
        name: String,
    ) {
        val trimmed = name.trim()
        state.value = if (trimmed.isEmpty()) state.value - routeId else state.value + (routeId to trimmed)
    }
}

// Kept in a DataStore, read and written off the main thread (roadmap 2, M-5). Until the saved
// names are read the device's own names show. The old SharedPreferences file of the same name is
// moved over once on the first read. Device-specific: not part of the backup (backup_rules.xml).
@Singleton
class DataStoreDeviceAliasStore internal constructor(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope,
) : DeviceAliasStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private constructor(context: Context, scope: CoroutineScope) : this(createStore(context, scope), scope)

    // One writer, in the order the names were given: the last name for a device wins.
    private val edits = Channel<Pair<String, String>>(Channel.UNLIMITED)

    override val aliases: StateFlow<Map<String, String>> =
        store.data
            .map { prefs -> prefs.asMap().mapNotNull { (key, value) -> (value as? String)?.let { key.name to it } }.toMap() }
            .catch { e ->
                Log.w(TAG, "Device names not read", e)
                emit(emptyMap())
            }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch {
            for ((routeId, name) in edits) {
                try {
                    store.edit { prefs ->
                        val key = stringPreferencesKey(routeId)
                        prefs.remove(key)
                        if (name.isNotEmpty()) prefs[key] = name
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Device name not saved", e)
                }
            }
        }
    }

    override fun setAlias(
        routeId: String,
        name: String,
    ) {
        edits.trySend(routeId to name.trim())
    }

    companion object {
        const val PREFS_NAME = "device_aliases"
        private const val TAG = "DeviceAliasStore"

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
