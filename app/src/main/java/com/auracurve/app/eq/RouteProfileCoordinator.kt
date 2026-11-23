package com.auracurve.app.eq

import com.auracurve.app.BuildConfig
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioRouteRepository
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.audio.AudioSessionRepository
import com.auracurve.app.audio.DeviceKind
import com.auracurve.app.audio.defaultHeadphoneAcoustics
import com.auracurve.app.auto.CarConnectionRepository
import com.auracurve.app.context.SoundContext
import com.auracurve.app.data.profile.AppProfileEntity
import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.Preset
import com.auracurve.app.profile.AppProfileRepository
import com.auracurve.app.profile.DeviceAliasStore
import com.auracurve.app.profile.DeviceProfileRepository
import com.auracurve.app.ui.main.CONTEXT_MODE_NONE
import com.auracurve.app.ui.main.RouteContextResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a route (output device) or an app the EQ is attached to remembers, read back when it shows
 * up. [preset] is the bound sound; [legacyContext] is set when that "preset" is one of the former
 * context tunings, which now means "this route uses that context mode".
 */
data class RouteBinding(
    val preset: Preset?,
    val legacyContext: SoundContext?,
    val correctionProfileId: String?,
)

/**
 * Outputs, sessions and what each of them remembers (roadmap 3, split out of [EqController]):
 * the active route and the name the user gave it, the per-route binding (preset, correction,
 * headphone mode, listening-context mode), the "headphones, car or speaker?" question, the per-app
 * sound (P5-5) and which app the EQ is attached to.
 *
 * It decides and stores; applying a sound is [EqController]'s job, which calls in here at the
 * points the M3 conflict rule allows ("a manual choice holds until the next route change").
 */
@Singleton
class RouteProfileCoordinator
    @Inject
    constructor(
        private val routeRepository: AudioRouteRepository,
        private val deviceProfileRepository: DeviceProfileRepository,
        private val sessionRepository: AudioSessionRepository,
        private val carConnectionRepository: CarConnectionRepository,
        private val appProfileRepository: AppProfileRepository,
        private val deviceAliases: DeviceAliasStore,
        private val presets: PresetPersistenceCoordinator,
        @param:EqControllerScope private val scope: CoroutineScope,
    ) {
        val currentRoute: StateFlow<AudioRoute> = routeRepository.activeRoute

        // The route as shown to the user: the device's own name unless the user gave it one.
        val displayRoute: StateFlow<AudioRoute> =
            combine(currentRoute, deviceAliases.aliases) { route, aliases ->
                aliases[route.id]?.let { route.copy(name = it) } ?: route
            }.stateIn(scope, SharingStarted.Eagerly, routeRepository.activeRoute.value)

        /** Blank resets to the device's own name. */
        fun renameCurrentRoute(name: String) {
            deviceAliases.setAlias(currentRoute.value.id, name)
        }

        // Every app currently holding an audio effect session, and the one the EQ is bound to.
        val sessions: StateFlow<List<AudioSession>> = sessionRepository.sessions
        val activeSession: StateFlow<AudioSession?> = sessionRepository.activeSession

        fun selectSession(session: AudioSession) = sessionRepository.setActiveSession(session)

        // Android Auto / Automotive OS is running - a safe "car" signal that does not depend on
        // the Bluetooth device name (and also holds when the audio goes over USB).
        val carConnected: StateFlow<Boolean> = carConnectionRepository.carConnected

        // False only while the EQ is bound to another app's session: Virtual Bass runs in
        // this app's own player only, so for a foreign session it has no effect there.
        val isOwnPlayerSession: StateFlow<Boolean> =
            sessionRepository.activeSession
                .map { it == null || it.packageName == BuildConfig.APPLICATION_ID }
                .stateIn(scope, SharingStarted.Eagerly, true)

        // P5-5: true while the app the EQ is attached to has its own remembered sound.
        val appProfileBound: StateFlow<Boolean> =
            combine(appProfileRepository.allProfiles, sessionRepository.activeSession) { profiles, session ->
                session != null && profiles.any { it.packageName == session.packageName }
            }.stateIn(scope, SharingStarted.Eagerly, false)

        // Chat feature (item 1 of "implement all the points", not a roadmap-2026.md
        // milestone): null until loadBinding() loads whatever the current route's
        // DeviceProfileEntity has saved (or confirms there's none yet) - see
        // effectiveHeadphoneAcoustics for how null resolves to a per-route-type default
        // instead of a hardcoded one.
        private val headphoneAcousticsOverride = MutableStateFlow<Boolean?>(null)

        val effectiveHeadphoneAcoustics: StateFlow<Boolean> =
            combine(currentRoute, headphoneAcousticsOverride) { route, override ->
                override ?: route.type.defaultHeadphoneAcoustics()
            }.stateIn(
                scope,
                SharingStarted.Eagerly,
                routeRepository.activeRoute.value.type
                    .defaultHeadphoneAcoustics(),
            )

        // A Bluetooth output the name heuristic could not place, asked about once so the
        // user's answer (stored like a manual mode choice) replaces the guesswork.
        private val _deviceKindPrompt = MutableStateFlow<AudioRoute?>(null)
        val deviceKindPrompt: StateFlow<AudioRoute?> = _deviceKindPrompt.asStateFlow()
        private val dismissedDeviceKindRouteIds = mutableSetOf<String>()

        // Saved context choice of the current route: null = none, CONTEXT_MODE_NONE = off.
        private var routeContextMode: String? = null
        private var pendingRestoredContext: SoundContext? = null

        /**
         * True when route detection switched the context mode on, so it is switched off again when
         * that route goes away; false once the user chose it themselves.
         */
        var contextSetAutomatically: Boolean = false
            private set

        /** The context state saved with the last live settings: a manual choice survives until the first route decision. */
        fun restoreContext(
            context: SoundContext?,
            automatic: Boolean,
        ) {
            contextSetAutomatically = automatic
            pendingRestoredContext = if (automatic) null else context
        }

        /**
         * Reads what [route] remembers and takes over its headphone-mode override and context
         * choice. A route with no saved profile still resets the override to null (no explicit
         * choice for it) instead of keeping the previous route's value.
         */
        suspend fun loadBinding(route: AudioRoute): RouteBinding {
            val binding = deviceProfileRepository.getProfileForRoute(route.id)
            headphoneAcousticsOverride.value = binding?.headphoneAcousticsOverride
            routeContextMode = binding?.contextMode
            val preset = binding?.let { presets.findById(it.boundPresetId) }
            return RouteBinding(
                preset = preset,
                legacyContext = preset?.let { BuiltInContextPresets.contextOf(it) },
                correctionProfileId = binding?.boundCorrectionProfileId,
            )
        }

        /** Only the legacy context of [route]'s binding, for re-deciding the mode (Android Auto changed). */
        suspend fun legacyContextOf(route: AudioRoute): SoundContext? {
            val binding = deviceProfileRepository.getProfileForRoute(route.id)
            val preset = binding?.let { presets.findById(it.boundPresetId) }
            return preset?.let { BuiltInContextPresets.contextOf(it) }
        }

        /**
         * Car / Bluetooth-speaker mode is decided per route, from the route's saved choice
         * (DeviceProfileEntity.contextMode) or, with none, from detection (see
         * SoundContextClassifier - a name heuristic). A manual choice therefore never follows the
         * user to another route. Consumes a context restored from the last session.
         */
        fun decideContextMode(
            route: AudioRoute,
            legacyContext: SoundContext?,
        ): SoundContext? {
            val restored = pendingRestoredContext
            pendingRestoredContext = null
            val decision =
                RouteContextResolver.resolve(
                    route = route,
                    carConnected = carConnectionRepository.carConnected.value,
                    storedMode = routeContextMode,
                    legacyContext = legacyContext,
                    restoredContext = restored,
                )
            contextSetAutomatically = decision.automatic
            return decision.mode
        }

        /** Asks about [route]'s kind when nothing places it (see RouteContextResolver.shouldAskDeviceKind). */
        fun updateDeviceKindPrompt(
            route: AudioRoute,
            legacyContext: SoundContext?,
        ) {
            _deviceKindPrompt.value =
                route.takeIf {
                    RouteContextResolver.shouldAskDeviceKind(
                        route = it,
                        storedMode = routeContextMode,
                        legacyContext = legacyContext,
                        dismissedRouteIds = dismissedDeviceKindRouteIds,
                        carConnected = carConnectionRepository.carConnected.value,
                    )
                }
        }

        /** The user's own choice of context mode (null = off) for the current route. */
        fun chooseContextMode(context: SoundContext?) {
            contextSetAutomatically = false
            routeContextMode = context?.name ?: CONTEXT_MODE_NONE
        }

        /**
         * Takes the answer to the device-kind question: headphones or not sets the headphone mode.
         * False when no question was open; the caller then leaves the context mode alone.
         */
        fun answerDeviceKind(kind: DeviceKind): Boolean {
            _deviceKindPrompt.value ?: return false
            _deviceKindPrompt.value = null
            headphoneAcousticsOverride.value = kind == DeviceKind.HEADPHONES
            return true
        }

        fun dismissDeviceKindPrompt() {
            _deviceKindPrompt.value?.let { dismissedDeviceKindRouteIds.add(it.id) }
            _deviceKindPrompt.value = null
        }

        // Chat feature (item 1): explicit per-route override, e.g. for a USB DAC feeding studio
        // monitors (defaultHeadphoneAcoustics() guesses "true" for any USB route, which is wrong
        // there). Stored with the rest of the binding by saveBinding().
        fun setHeadphoneAcousticsOverride(override: Boolean) {
            headphoneAcousticsOverride.value = override
        }

        /**
         * Persists which sound is active while the current route is the active one - called from
         * every user-initiated choice, never from a route change (that would just save back the
         * binding it just read). The ids are read when the write runs, so they are the newest.
         * Also keeps the sound of the attached app up to date when it has one of its own.
         */
        fun saveBinding(
            presetId: () -> String,
            correctionProfileId: () -> String,
        ) {
            rememberForActiveAppIfBound(presetId, correctionProfileId)
            val route = currentRoute.value
            val override = headphoneAcousticsOverride.value
            scope.launch {
                deviceProfileRepository.saveProfile(
                    routeId = route.id,
                    routeType = route.type.name,
                    displayName = route.name,
                    boundPresetId = presetId(),
                    boundCorrectionProfileId = correctionProfileId(),
                    headphoneAcousticsOverride = override,
                    contextMode = routeContextMode,
                )
            }
        }

        /** The correction a route's binding names, or null; for the catalogue suggestion. */
        suspend fun boundCorrectionProfileId(routeId: String): String? =
            deviceProfileRepository.getProfileForRoute(routeId)?.boundCorrectionProfileId

        // "Use this sound for <app>": remembers the current preset and correction for the app the EQ is
        // attached to (and keeps them up to date with every later choice), or forgets them again.
        fun setAppProfileBound(
            bound: Boolean,
            presetId: () -> String,
            correctionProfileId: () -> String,
        ) {
            val packageName = sessionRepository.activeSession.value?.packageName ?: return
            scope.launch {
                if (bound) {
                    appProfileRepository.saveProfile(packageName, presetId(), correctionProfileId())
                } else {
                    appProfileRepository.deleteProfile(packageName)
                }
            }
        }

        /** The remembered sound of [packageName], if it has one. */
        suspend fun appProfileFor(packageName: String?): AppProfileEntity? = packageName?.let { appProfileRepository.getProfile(it) }

        private fun rememberForActiveAppIfBound(
            presetId: () -> String,
            correctionProfileId: () -> String,
        ) {
            val packageName = sessionRepository.activeSession.value?.packageName ?: return
            if (!appProfileBound.value) return
            scope.launch {
                appProfileRepository.saveProfile(packageName, presetId(), correctionProfileId())
            }
        }

        companion object {
            /** The context mode an answer to the device-kind question stands for. */
            fun contextFor(kind: DeviceKind): SoundContext? =
                when (kind) {
                    DeviceKind.HEADPHONES -> null
                    DeviceKind.CAR -> SoundContext.CAR
                    DeviceKind.SPEAKER -> SoundContext.BLUETOOTH_SPEAKER
                    DeviceKind.SOUNDBAR -> SoundContext.SOUNDBAR
                }
        }
    }
