package com.auracurve.app.ui.main

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.FakeAudioEngine
import com.auracurve.app.diagnostics.InMemoryDiagnosticsRecorder
import com.auracurve.app.eq.EqFakeAppSettingsRepository
import com.auracurve.app.eq.EqFakeCorrectionProfileRepository
import com.auracurve.app.eq.EqFakeDeviceProfileRepository
import com.auracurve.app.eq.EqFakeEffectRepository
import com.auracurve.app.eq.EqFakePlayerBridge
import com.auracurve.app.eq.EqFakePresetRepository
import com.auracurve.app.eq.EqFakeRouteRepository
import com.auracurve.app.eq.EqFakeVolumeRepository
import com.auracurve.app.text.FakeTextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Roadmap 3 (M-2): the test constructor of MainViewModel builds its own EqController in a scope
// of its own. That scope belongs to the view model and ends with it, so the controller's
// collectors do not keep running (and reacting) after the view model is cleared.
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelScopeTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clearing the view model cancels the scope of its own controller`() {
        val routes = EqFakeRouteRepository()
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider.create(
                store,
                viewModelFactory {
                    initializer {
                        MainViewModel(
                            repository = EqFakeEffectRepository(),
                            audioEngine = FakeAudioEngine(),
                            routeRepository = routes,
                            volumeRepository = EqFakeVolumeRepository(),
                            diagnosticsRecorder = InMemoryDiagnosticsRecorder(),
                            playerBridge = EqFakePlayerBridge(),
                            presetRepository = EqFakePresetRepository(),
                            appSettingsRepository = EqFakeAppSettingsRepository(),
                            correctionProfileRepository = EqFakeCorrectionProfileRepository(),
                            deviceProfileRepository = EqFakeDeviceProfileRepository(),
                            texts = FakeTextProvider(),
                            backgroundDispatcher = dispatcher,
                        )
                    }
                },
            )[MainViewModel::class]
        val headphones = AudioRoute(AudioDeviceType.WIRED_HEADPHONES, "Headphones", "wired")
        routes.setRoute(headphones)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(headphones, viewModel.displayRoute.value)

        store.clear()
        routes.setRoute(AudioRoute(AudioDeviceType.SPEAKER, "Speaker", "speaker"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("a cleared view model still follows the route", headphones, viewModel.displayRoute.value)
    }
}
