package com.auracurve.app.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceAliasStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun setAlias_storesTrimmedNamePerRoute() {
        val store = InMemoryDeviceAliasStore()
        store.setAlias("bt_1", "  My headphones ")
        store.setAlias("bt_2", "Car")
        assertEquals(mapOf("bt_1" to "My headphones", "bt_2" to "Car"), store.aliases.value)
    }

    @Test
    fun blankName_removesTheAlias() {
        val store = InMemoryDeviceAliasStore()
        store.setAlias("bt_1", "My headphones")
        store.setAlias("bt_1", "   ")
        assertEquals(emptyMap<String, String>(), store.aliases.value)
    }

    // Roadmap 2, M-5: the names are saved in the background; they show once written.
    @Test
    fun dataStore_savesTrimmedNamesInTheOrderGiven() =
        runBlocking {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("device_aliases.preferences_pb") }
            val store = DataStoreDeviceAliasStore(dataStore, scope)

            // Every step leaves a different set of names, so only the last one matches below.
            store.setAlias("bt_2", "Car")
            store.setAlias("bt_1", "First")
            store.setAlias("bt_2", " ")
            store.setAlias("bt_1", "  My headphones ")

            withTimeout(TIMEOUT_MS) { store.aliases.first { it == mapOf("bt_1" to "My headphones") } }
            val saved = dataStore.data.first()
            assertEquals("My headphones", saved[stringPreferencesKey("bt_1")])
            assertNull(saved[stringPreferencesKey("bt_2")])
        }

    @Test
    fun dataStore_readsWhatWasSavedBefore() =
        runBlocking {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("device_aliases.preferences_pb") }
            dataStore.updateData { prefs -> prefs.toMutablePreferences().apply { set(stringPreferencesKey("usb_1"), "Desk DAC") } }

            val store = DataStoreDeviceAliasStore(dataStore, scope)

            withTimeout(TIMEOUT_MS) { store.aliases.first { it == mapOf("usb_1" to "Desk DAC") } }
            Unit
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
