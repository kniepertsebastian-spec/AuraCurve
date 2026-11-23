package com.auracurve.app.eq

import com.auracurve.app.audio.AudioEngine
import com.auracurve.app.audio.AudioRouteRepository
import com.auracurve.app.audio.AudioSessionRepository
import com.auracurve.app.auto.CarConnectionRepository
import com.auracurve.app.correction.CorrectionProfileRepository
import com.auracurve.app.integration.PlayerBridge
import com.auracurve.app.preset.PresetRepository
import com.auracurve.app.profile.AppProfileRepository
import com.auracurve.app.profile.CorrectionSuggestionDismissals
import com.auracurve.app.profile.DeviceAliasStore
import com.auracurve.app.profile.DeviceProfileRepository
import com.auracurve.app.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope

/** The coordinators of one [EqController], wired by hand where Hilt does not build them (tests). */
internal class EqCoordinators(
    val routeProfiles: RouteProfileCoordinator,
    val corrections: CorrectionProfileCoordinator,
    val presets: PresetPersistenceCoordinator,
    val compareSession: CompareSessionController,
    val output: SoundOutputCoordinator,
) {
    companion object {
        fun create(
            audioEngine: AudioEngine,
            playerBridge: PlayerBridge,
            routeRepository: AudioRouteRepository,
            presetRepository: PresetRepository,
            appSettingsRepository: AppSettingsRepository,
            correctionProfileRepository: CorrectionProfileRepository,
            deviceProfileRepository: DeviceProfileRepository,
            sessionRepository: AudioSessionRepository,
            carConnectionRepository: CarConnectionRepository,
            appProfileRepository: AppProfileRepository,
            deviceAliases: DeviceAliasStore,
            suggestionDismissals: CorrectionSuggestionDismissals,
            scope: CoroutineScope,
        ): EqCoordinators {
            val presets = PresetPersistenceCoordinator(presetRepository, appSettingsRepository, scope)
            val routeProfiles =
                RouteProfileCoordinator(
                    routeRepository = routeRepository,
                    deviceProfileRepository = deviceProfileRepository,
                    sessionRepository = sessionRepository,
                    carConnectionRepository = carConnectionRepository,
                    appProfileRepository = appProfileRepository,
                    deviceAliases = deviceAliases,
                    presets = presets,
                    scope = scope,
                )
            val compareSession = CompareSessionController()
            return EqCoordinators(
                routeProfiles = routeProfiles,
                corrections = CorrectionProfileCoordinator(correctionProfileRepository, suggestionDismissals, scope),
                presets = presets,
                compareSession = compareSession,
                output = SoundOutputCoordinator(audioEngine, playerBridge, routeProfiles, compareSession, scope),
            )
        }
    }
}
