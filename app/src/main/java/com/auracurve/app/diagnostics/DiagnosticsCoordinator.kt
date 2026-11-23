package com.auracurve.app.diagnostics

import com.auracurve.app.audio.AudioEffectDescriptor
import com.auracurve.app.audio.AudioEffectRepository
import com.auracurve.app.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the diagnostics screen reads from the platform when it opens. */
data class SystemDiagnosticsSnapshot(
    val processExits: List<ProcessExitRecord> = emptyList(),
    val outputAudio: OutputAudioInfo? = null,
    val extendedAppDetection: Boolean? = null,
)

/**
 * The diagnostics side of the equalizer screen (roadmap 3, taken out of MainViewModel): the event
 * history, the platform's own records and the list of audio effects on the device. Every platform
 * call runs off the main thread.
 */
class DiagnosticsCoordinator
    @Inject
    constructor(
        private val effectRepository: AudioEffectRepository,
        recorder: DiagnosticsRecorder,
        private val systemDiagnostics: SystemDiagnosticsSource,
        @DefaultDispatcher private val backgroundDispatcher: CoroutineDispatcher,
    ) {
        val events: StateFlow<List<DiagnosticsEvent>> = recorder.events

        // Read when the diagnostics screen opens (roadmap 06, P6-4/P5-8).
        suspend fun loadSystemDiagnostics(): SystemDiagnosticsSnapshot =
            withContext(backgroundDispatcher) {
                SystemDiagnosticsSnapshot(
                    systemDiagnostics.recentProcessExits(),
                    systemDiagnostics.outputAudioInfo(),
                    systemDiagnostics.extendedAppDetectionGranted(),
                )
            }

        // The debug list of every effect the device offers (AudioEffect.queryEffects, a binder call).
        suspend fun availableEffects(): List<AudioEffectDescriptor> =
            withContext(backgroundDispatcher) { effectRepository.queryAvailableEffects() }
    }
