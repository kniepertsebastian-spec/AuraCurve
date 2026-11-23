package com.auracurve.app.profile

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Roadmap 2, M-5: what existing installs saved in SharedPreferences is moved into DataStore once.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class SharedPreferencesMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `device names saved by an older version are kept`() =
        runBlocking {
            context
                .getSharedPreferences(DataStoreDeviceAliasStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("bt_1", "My headphones")
                .commit()

            val store = DataStoreDeviceAliasStore(DataStoreDeviceAliasStore.createStore(context, scope), scope)

            withTimeout(TIMEOUT_MS) { store.aliases.first { it == mapOf("bt_1" to "My headphones") } }
            Unit
        }

    @Test
    fun `dismissed suggestions saved by an older version are kept`() =
        runBlocking {
            context
                .getSharedPreferences(DataStoreCorrectionSuggestionDismissals.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("bt_xm4", true)
                .commit()

            val dismissals =
                DataStoreCorrectionSuggestionDismissals(DataStoreCorrectionSuggestionDismissals.createStore(context, scope), scope)

            assertTrue(dismissals.isDismissed("bt_xm4"))
            assertFalse(dismissals.isDismissed("bt_other"))
        }

    @Test
    fun `a dismissal counts at once and is saved`() =
        runBlocking {
            val dataStore = DataStoreCorrectionSuggestionDismissals.createStore(context, scope)
            val dismissals = DataStoreCorrectionSuggestionDismissals(dataStore, scope)

            dismissals.dismiss("usb_dac")

            assertTrue(dismissals.isDismissed("usb_dac"))
            withTimeout(TIMEOUT_MS) {
                dataStore.data.first { prefs -> prefs.asMap().keys.any { it.name == "usb_dac" } }
            }
            Unit
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
