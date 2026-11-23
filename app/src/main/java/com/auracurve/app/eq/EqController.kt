package com.auracurve.app.eq

import com.auracurve.app.BuildConfig
import com.auracurve.app.audio.AudioEngine
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioRouteRepository
import com.auracurve.app.audio.AudioSessionRepository
import com.auracurve.app.audio.DeviceKind
import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.audio.SystemVolumeRepository
import com.auracurve.app.auto.CarConnectionRepository
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.correction.CorrectionProfileRepository
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.dsp.ParametricCurve
import com.auracurve.app.dsp.PreEqResponseMode
import com.auracurve.app.integration.PlayerBridge
import com.auracurve.app.preset.BuiltInContextPresets
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.GenrePreset
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetCycle
import com.auracurve.app.preset.PresetIntensity
import com.auracurve.app.preset.PresetIntensityBoost
import com.auracurve.app.preset.PresetIntensityResolver
import com.auracurve.app.preset.PresetRepository
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.profile.AppProfileRepository
import com.auracurve.app.profile.CorrectionSuggestionDismissals
import com.auracurve.app.profile.DeviceAliasStore
import com.auracurve.app.profile.DeviceProfileRepository
import com.auracurve.app.profile.InMemoryCorrectionSuggestionDismissals
import com.auracurve.app.profile.InMemoryDeviceAliasStore
import com.auracurve.app.quick.QuickCommand
import com.auracurve.app.quick.QuickControls
import com.auracurve.app.quick.QuickState
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.settings.LiveSettings
import com.auracurve.app.ui.main.CurveCalculator
import com.auracurve.app.ui.main.CurveInputs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.math.abs

// Where a new parametric band is put first: free spots from the mids outwards.
private val PARAMETRIC_START_FREQUENCIES_HZ = listOf(1000f, 100f, 4000f, 250f, 8000f, 60f, 2000f, 500f)

/** The scope the [EqController] runs its work in: process lifetime, main thread (see EqControllerModule). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EqControllerScope

/**
 * E-008 stage 2: the equalizer's control logic, process-wide. It used to live only in
 * MainViewModel, so without an open activity nobody switched presets (quick tile, later the
 * remote server), followed route changes or saved anything. MainViewModel is a thin layer on top
 * of this, and the session service starts it (see [awaitRestored]) so it also works with the app
 * closed.
 *
 * Roadmap 3 split the former single class along its concerns; this class keeps the sound itself
 * (preset, intensity, context mode, goal, headphone power, parametric bands, macros, bands), the
 * reactions to routes, sessions, Android Auto, capabilities and volume, and the order in which a
 * change is recalculated, pushed and saved. The rest lives in focused coordinators it drives:
 *
 * - [RouteProfileCoordinator]: routes, sessions and what each remembers (route/app bindings,
 *   headphone mode, context-mode decision, the device-kind question).
 * - [CorrectionProfileCoordinator]: the correction curves and which one is active.
 * - [PresetPersistenceCoordinator]: custom presets, the saved live state and the preset actions
 *   that write to the store.
 * - [CompareSessionController]: the Original/EQ comparison.
 * - [SoundOutputCoordinator]: hands the finished settings to the engine and the built-in player.
 *
 * The curve arithmetic itself is in [CurveCalculator] and [PresetSettingsMapper].
 *
 * Everything runs in [scope] on the main thread, exactly as before, so the split added no
 * threading.
 */
@Singleton
class EqController
    @Inject
    constructor(
        private val audioEngine: AudioEngine,
        private val volumeRepository: SystemVolumeRepository,
        private val quickControls: QuickControls,
        private val routeProfiles: RouteProfileCoordinator,
        private val corrections: CorrectionProfileCoordinator,
        private val presets: PresetPersistenceCoordinator,
        private val compareSession: CompareSessionController,
        private val output: SoundOutputCoordinator,
        @param:EqControllerScope private val scope: CoroutineScope,
    ) {
        /**
         * Builds the controller and its coordinators from the repositories, the way the tests (and
         * the test constructor of MainViewModel) have always created it.
         */
        constructor(
            audioEngine: AudioEngine,
            routeRepository: AudioRouteRepository,
            volumeRepository: SystemVolumeRepository,
            playerBridge: PlayerBridge,
            presetRepository: PresetRepository,
            appSettingsRepository: AppSettingsRepository,
            correctionProfileRepository: CorrectionProfileRepository,
            deviceProfileRepository: DeviceProfileRepository,
            sessionRepository: AudioSessionRepository,
            carConnectionRepository: CarConnectionRepository,
            quickControls: QuickControls,
            appProfileRepository: AppProfileRepository,
            scope: CoroutineScope,
            deviceAliases: DeviceAliasStore = InMemoryDeviceAliasStore(),
            suggestionDismissals: CorrectionSuggestionDismissals = InMemoryCorrectionSuggestionDismissals(),
        ) : this(
            EqCoordinators.create(
                audioEngine = audioEngine,
                playerBridge = playerBridge,
                routeRepository = routeRepository,
                presetRepository = presetRepository,
                appSettingsRepository = appSettingsRepository,
                correctionProfileRepository = correctionProfileRepository,
                deviceProfileRepository = deviceProfileRepository,
                sessionRepository = sessionRepository,
                carConnectionRepository = carConnectionRepository,
                appProfileRepository = appProfileRepository,
                deviceAliases = deviceAliases,
                suggestionDismissals = suggestionDismissals,
                scope = scope,
            ),
            audioEngine = audioEngine,
            volumeRepository = volumeRepository,
            quickControls = quickControls,
            scope = scope,
        )

        /** Wires the controller to coordinators built by hand (see [EqCoordinators]). */
        internal constructor(
            coordinators: EqCoordinators,
            audioEngine: AudioEngine,
            volumeRepository: SystemVolumeRepository,
            quickControls: QuickControls,
            scope: CoroutineScope,
        ) : this(
            audioEngine = audioEngine,
            volumeRepository = volumeRepository,
            quickControls = quickControls,
            routeProfiles = coordinators.routeProfiles,
            corrections = coordinators.corrections,
            presets = coordinators.presets,
            compareSession = coordinators.compareSession,
            output = coordinators.output,
            scope = scope,
        )

        val capabilities = audioEngine.capabilities
        val currentRoute: StateFlow<AudioRoute> = routeProfiles.currentRoute
        val currentLevelDb: StateFlow<Float> = volumeRepository.currentLevelDb
        val isOwnPlayerSession: StateFlow<Boolean> = routeProfiles.isOwnPlayerSession
        val appProfileBound: StateFlow<Boolean> = routeProfiles.appProfileBound
        val effectiveHeadphoneAcoustics: StateFlow<Boolean> = routeProfiles.effectiveHeadphoneAcoustics
        val deviceKindPrompt: StateFlow<AudioRoute?> = routeProfiles.deviceKindPrompt

        // "Use this sound for <app>": remembers the current preset and correction for the app the EQ is
        // attached to (and keeps them up to date with every later choice), or forgets them again.
        fun setAppProfileBound(bound: Boolean) {
            routeProfiles.setAppProfileBound(bound, { _activePreset.value.id }, { corrections.active.value.id })
        }

        // An app with a remembered sound gets it as soon as it becomes the active one. The device profile
        // stays in charge for apps without one; on a route change the app's sound wins again afterwards.
        private suspend fun applyAppProfile(packageName: String?) {
            val profile = routeProfiles.appProfileFor(packageName) ?: return
            val bound = presets.findById(profile.boundPresetId) ?: return
            if (BuiltInContextPresets.contextOf(bound) != null) return
            val preset = withoutRetiredIntensity(bound)
            _activePreset.value = preset
            corrections.setActive(corrections.findById(profile.boundCorrectionProfileId))
            _isDirty.value = false
            _processingSettings.value = _processingSettings.value.withPreset(preset)
            recalculateBandGains()
            persistLiveSettings()
        }

        private val _activePreset = MutableStateFlow<Preset>(BuiltInPresets.CleanPunch)
        val activePreset: StateFlow<Preset> = _activePreset.asStateFlow()

        // M2: "mark a manually changed preset as custom automatically" -
        // true once a band/macro edit has moved processingSettings away from what
        // freshly selecting activePreset would produce. Cleared by selecting a
        // preset (including re-selecting the current one, i.e. "reset") or by
        // saving the edit as its own preset.
        private val _isDirty = MutableStateFlow(false)
        val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

        // "More intensity": the former Aggressive step on top of whatever preset is active
        // (PresetIntensityBoost). Replaces the five intensity steps; kept across preset changes.
        private val _moreIntensity = MutableStateFlow(false)
        val moreIntensity: StateFlow<Boolean> = _moreIntensity.asStateFlow()

        // Listening-context mode ("Auto" / "Bluetooth-Box"): an overlay on whatever
        // preset is active, not a preset of its own - so picking another genre keeps
        // it on. It adds the context's curve, subsonic filter, volume loudness,
        // virtual bass and (for small speakers) a tighter limiter. null = off.
        // Which mode a route gets is decided by RouteProfileCoordinator.
        private val _activeContext = MutableStateFlow<SoundContext?>(null)
        val activeContext: StateFlow<SoundContext?> = _activeContext.asStateFlow()

        val allPresets: StateFlow<List<Preset>> = presets.allPresets

        // Chat feature: genre x intensity preset redesign. The old preset grid
        // (still used for user-duplicated custom presets, see EqualizerScreen) is
        // no longer how the built-in sounds are picked - a genre dropdown plus 5
        // intensity buttons is. customPresetsOnly feeds that grid instead of
        // allPresets now that BuiltInPresets.all also carries every genre x
        // intensity combination.
        val customPresetsOnly: StateFlow<List<Preset>> = presets.customPresetsOnly

        // Mirrors activePreset, parsed back into which genre/intensity button
        // combination it came from (null if the active preset isn't one of
        // those - a legacy built-in or a custom preset).
        val selectedGenreIntensity: StateFlow<Pair<GenrePreset, PresetIntensity>?> =
            activePreset
                .map { preset -> PresetIntensityResolver.parse(preset.id, BuiltInGenrePresets.all) }
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    PresetIntensityResolver.parse(_activePreset.value.id, BuiltInGenrePresets.all),
                )

        // M3 "My headphones": the headphone/speaker correction curve, combined with
        // activePreset ("Klangstil") via CurveComposer before mapping to hardware
        // bands - see recalculateBandGains().
        val activeCorrectionProfile: StateFlow<CorrectionProfile> = corrections.active
        val allCorrectionProfiles: StateFlow<List<CorrectionProfile>> = corrections.allProfiles

        private val _processingSettings =
            MutableStateFlow(ProcessingSettings().withPreset(BuiltInPresets.CleanPunch))
        val processingSettings: StateFlow<ProcessingSettings> = _processingSettings.asStateFlow()

        // Original/EQ comparison with matched loudness, see CompareSessionController.
        val compare: StateFlow<CompareState?> = compareSession.state

        // Sound goal ("Vocals forward", "More power", ...): a small curve on top of the genre preset.
        private val _activeGoal = MutableStateFlow(SoundGoal.BALANCED)
        val activeGoal: StateFlow<SoundGoal> = _activeGoal.asStateFlow()

        fun setSoundGoal(goal: SoundGoal) {
            _activeGoal.value = goal
            recalculateBandGains()
            persistLiveSettings()
        }

        // "Headphone power": only takes effect in headphone mode.
        private val _headphonePower = MutableStateFlow(HeadphonePower())
        val headphonePower: StateFlow<HeadphonePower> = _headphonePower.asStateFlow()

        fun setHeadphonePower(power: HeadphonePower) {
            _headphonePower.value = power.sanitized()
            recalculateBandGains()
            persistLiveSettings()
        }

        // P5-3: the free bands and their evaluated curve (re-evaluated only when a band changes).
        //
        // Declared here, above the init block, on purpose: the collectors launched in init run at
        // once on the main thread (scope is Dispatchers.Main.immediate) and already
        // compute the curve. A property declared below init is still null at that point - that
        // crashed the app at every start (NullPointerException in CurveInputs) in every release
        // after 1.1.2-beta.5. Everything the init block reads has to be declared above it.
        private val _parametricBands = MutableStateFlow<List<ParametricBand>>(emptyList())
        val parametricBands: StateFlow<List<ParametricBand>> = _parametricBands.asStateFlow()
        private var parametricCurve: List<TargetPoint> = emptyList()

        // Guards persistLiveSettings() against running before restoreSavedState()
        // has had a chance to load what was on disk - without this, the very
        // first capabilities/init-driven applySettings() call would write this
        // controller's temporary just-constructed defaults over the real saved
        // state before it's even been read back.
        private var hasRestoredState = false
        private val stateRestored = CompletableDeferred<Unit>()

        // Session listening/attach and route monitoring are owned by
        // AudioSessionForegroundService, not this controller - that keeps them
        // running for as long as the app process is alive. This only reacts to
        // what the (singleton) engine, routes, sessions and volume report.
        init {
            quickControls.consumerAttached()
            scope.launch {
                // Tiles and widget show this; republished on every change that matters to them.
                combine(_processingSettings, _activePreset) { settings, preset ->
                    QuickState(masterEnabled = settings.masterEnabled, presetName = preset.name)
                }.collect { state -> if (hasRestoredState) quickControls.publish(state) }
            }
            scope.launch {
                quickControls.commands.collect { command ->
                    stateRestored.await()
                    handleQuickCommand(command)
                }
            }
            scope.launch {
                audioEngine.capabilities.collect { caps ->
                    recalculateBandGains(caps.bands)
                }
            }
            scope.launch {
                restoreSavedState()
            }
            // M3 "switch profile when Bluetooth, USB or speaker output changes": the
            // starting route is handled inside restoreSavedState() itself (it may
            // still be resolving when this collector's first emission fires); this
            // only reacts to routes that change *after* that, e.g. a Bluetooth
            // headset connecting/disconnecting during the session. AudioRoute is a
            // data class and activeRoute is a StateFlow, so equal consecutive
            // routes are already deduplicated before this collector ever sees them.
            scope.launch {
                currentRoute
                    .drop(1)
                    .collect { route ->
                        if (hasRestoredState) {
                            applyDeviceProfileForRoute(route)
                            applyAppProfile(routeProfiles.activeSession.value?.packageName)
                        }
                    }
            }
            scope.launch {
                routeProfiles.activeSession
                    .map { it?.packageName }
                    .distinctUntilChanged()
                    .collect { packageName ->
                        stateRestored.await()
                        applyAppProfile(packageName)
                    }
            }
            // The Virtual Bass approximation exists only for foreign sessions.
            scope.launch {
                isOwnPlayerSession
                    .drop(1)
                    .collect { if (hasRestoredState) recalculateBandGains() }
            }
            // Binding to this app's own player (or away from it) moves the curve between the
            // player's PCM equalizer and the system equalizer.
            scope.launch {
                routeProfiles.activeSession
                    .map { it?.packageName == BuildConfig.APPLICATION_ID }
                    .drop(1)
                    .collect { if (hasRestoredState) pushToEngine(_processingSettings.value) }
            }
            // Android Auto connecting or disconnecting changes what "detected" means for the
            // current route: re-decide the mode (a saved choice for the route still wins).
            scope.launch {
                routeProfiles.carConnected
                    .drop(1)
                    .collect {
                        if (hasRestoredState) reapplyContextModeForCurrentRoute()
                    }
            }
            // Makes the "headphone mode" switch (and automatic route-based
            // defaulting) actually change the applied EQ curve - combinedCurve()
            // already reads effectiveHeadphoneAcoustics.value itself, but nothing
            // re-triggers recalculateBandGains() when *only* this changes without
            // this collector.
            scope.launch {
                effectiveHeadphoneAcoustics.collect {
                    recalculateBandGains()
                }
            }
            // Loudness compensation follows the system volume, which can change at any
            // time (hardware keys, another app): re-run the band mapping, but only while
            // something actually uses it - headphone mode's loudness curve or the active
            // sound's own loudnessMaxBoostDb - so volume key presses do not needlessly
            // re-apply unchanged settings to the engine.
            scope.launch {
                volumeRepository.currentLevelDb.collect {
                    if (hasRestoredState && (effectiveHeadphoneAcoustics.value || _processingSettings.value.loudnessMaxBoostDb > 0f)) {
                        recalculateBandGains()
                    }
                }
            }
        }

        private suspend fun restoreSavedState() {
            val initialCustomPresets = presets.loadSaved()
            val initialCorrectionProfiles = corrections.loadSaved()
            val saved = presets.loadLiveSettings()
            if (saved != null) {
                _moreIntensity.value = saved.moreIntensity
                val savedPreset = presets.findById(saved.activePresetId, initialCustomPresets)
                if (savedPreset != null) {
                    val preset = withoutRetiredIntensity(savedPreset)
                    // An old intensity step becomes the genre's base plus the switch; its saved
                    // values held that step's macros, so they are derived anew from the base.
                    val settings =
                        if (preset.id == savedPreset.id) {
                            saved.processingSettings
                        } else {
                            saved.processingSettings.withPreset(preset)
                        }
                    _activePreset.value = preset
                    _isDirty.value = saved.isDirty && preset.id == savedPreset.id
                    _processingSettings.value = settings
                    output.applyRestored(settings)
                }
                corrections.setActive(corrections.findById(saved.activeCorrectionProfileId, initialCorrectionProfiles))
                _activeContext.value = saved.activeContext?.let { name -> SoundContext.entries.firstOrNull { it.name == name } }
                // A mode the user switched on by hand survives the restart until the first
                // route decision; from then on the route's own saved choice or detection rules.
                routeProfiles.restoreContext(_activeContext.value, saved.activeContextAutomatic)
                _headphonePower.value = saved.headphonePower.sanitized()
                _activeGoal.value = SoundGoal.fromId(saved.activeGoal)
                _parametricBands.value = saved.parametricBands.take(ParametricCurve.MAX_BANDS).map { it.clamped() }
                parametricCurve = ParametricCurve.bandsToCurve(_parametricBands.value)
                // A saved activePresetId/activeCorrectionProfileId that no longer
                // resolves (its custom entry was deleted from another install, say)
                // just keeps the compiled-in default - not an error, nothing to
                // recover, per M2's "a corrupted ... cannot keep the app from starting".
            }

            hasRestoredState = true
            stateRestored.complete(Unit)
            quickControls.publish(QuickState(_processingSettings.value.masterEnabled, _activePreset.value.name))

            // Route-specific binding takes precedence over the plain "last active"
            // LiveSettings restored above, if this route has one - that's the
            // actual point of M3 (same taste, different headphones).
            applyDeviceProfileForRoute(currentRoute.value)
            applyAppProfile(routeProfiles.activeSession.value?.packageName)

            // Keep collecting custom-preset/-correction-profile changes after this
            // point (deletions, saves) - the one-shot reads above were only for
            // resolving the restore without a race against these collectors.
            presets.followChanges()
            corrections.followChanges()
        }

        // Switches to what `route` remembers, if anything - the M3 conflict rule ("a manual choice
        // holds until the next route change") means this is the *only* place a route is allowed to
        // change the active preset/correction; selectPreset()/selectCorrectionProfile() never do.
        private suspend fun applyDeviceProfileForRoute(route: AudioRoute) {
            val binding = routeProfiles.loadBinding(route)
            applyContextMode(routeProfiles.decideContextMode(route, binding.legacyContext))
            routeProfiles.updateDeviceKindPrompt(route, binding.legacyContext)
            val preset = binding.preset ?: return
            // A profile saved while the context tunings were still presets of their own
            // means "this route uses that context": keep the genre preset as it is and
            // turn the mode on instead of replacing the sound with it.
            if (binding.legacyContext != null) return
            val correction = corrections.findById(binding.correctionProfileId.orEmpty())

            val target = withoutRetiredIntensity(preset)
            _activePreset.value = target
            corrections.setActive(correction)
            _isDirty.value = false
            _processingSettings.value = _processingSettings.value.withPreset(target)
            recalculateBandGains()
            persistLiveSettings()
        }

        fun answerDeviceKind(kind: DeviceKind) {
            if (!routeProfiles.answerDeviceKind(kind)) return
            setContextMode(RouteProfileCoordinator.contextFor(kind))
        }

        fun dismissDeviceKindPrompt() = routeProfiles.dismissDeviceKindPrompt()

        private suspend fun reapplyContextModeForCurrentRoute() {
            val legacyContext = routeProfiles.legacyContextOf(currentRoute.value)
            applyContextMode(routeProfiles.decideContextMode(currentRoute.value, legacyContext))
        }

        /** The user's own choice of listening-context mode (null = off), remembered for the current route. */
        fun setContextMode(context: SoundContext?) {
            routeProfiles.chooseContextMode(context)
            applyContextMode(context)
            saveDeviceProfileBinding()
        }

        private fun applyContextMode(context: SoundContext?) {
            _activeContext.value = context
            _processingSettings.value = _processingSettings.value.withContextFeatures(_activePreset.value)
            recalculateBandGains()
            persistLiveSettings()
        }

        // Persists which voicing/correction are active while the current route is the active one -
        // called from every user-initiated preset/correction selection, never from
        // applyDeviceProfileForRoute() above (that would just save back the exact binding it just read).
        private fun saveDeviceProfileBinding() {
            routeProfiles.saveBinding({ _activePreset.value.id }, { corrections.active.value.id })
        }

        // Chat feature (item 1): explicit per-route override, e.g. for a USB DAC
        // feeding studio monitors. Goes through the same saveDeviceProfileBinding()
        // write path as preset/correction changes so the three stay merged into one
        // row instead of racing each other.
        fun setHeadphoneAcousticsOverride(override: Boolean) {
            routeProfiles.setHeadphoneAcousticsOverride(override)
            saveDeviceProfileBinding()
        }

        private fun persistLiveSettings() {
            if (!hasRestoredState) return
            presets.saveLiveSettings {
                LiveSettings(
                    activePresetId = _activePreset.value.id,
                    isDirty = _isDirty.value,
                    processingSettings = _processingSettings.value,
                    activeCorrectionProfileId = corrections.active.value.id,
                    activeContext = _activeContext.value?.name,
                    activeContextAutomatic = routeProfiles.contextSetAutomatically,
                    headphonePower = _headphonePower.value,
                    activeGoal = _activeGoal.value.id.takeIf { _activeGoal.value != SoundGoal.BALANCED },
                    parametricBands = _parametricBands.value,
                    moreIntensity = _moreIntensity.value,
                )
            }
        }

        fun selectPreset(picked: Preset) {
            // A context tuning is a mode, not a sound: selecting one (an older
            // shortcut) switches the mode on and leaves the active preset alone.
            BuiltInContextPresets.contextOf(picked)?.let { context ->
                setContextMode(context)
                return
            }
            val preset = withoutRetiredIntensity(picked)
            _activePreset.value = preset
            _isDirty.value = false
            _processingSettings.value = _processingSettings.value.withPreset(preset)
            recalculateBandGains()
            persistLiveSettings()
            saveDeviceProfileBinding()
        }

        // Chat feature: genre x intensity preset redesign. Resolves the pair into
        // a concrete Preset and routes through the existing selectPreset() -
        // same persistence/headroom/dirty-flag pipeline as any other preset pick.
        fun selectGenreIntensity(
            genre: GenrePreset,
            intensity: PresetIntensity,
        ) {
            selectPreset(PresetIntensityResolver.resolve(genre, intensity))
        }

        /**
         * "More intensity" on or off. Without manual edits the preset is simply applied again;
         * with them only the macros move by the step, so the edits stay.
         */
        fun setMoreIntensity(enabled: Boolean) {
            if (_moreIntensity.value == enabled) return
            val preset = _activePreset.value
            val before = intensified(preset)
            _moreIntensity.value = enabled
            val after = intensified(preset)
            _processingSettings.value =
                if (_isDirty.value) {
                    _processingSettings.value.copy(
                        macroBassDb = _processingSettings.value.macroBassDb + after.macroBassDb - before.macroBassDb,
                        macroPunchDb = _processingSettings.value.macroPunchDb + after.macroPunchDb - before.macroPunchDb,
                    )
                } else {
                    _processingSettings.value.withPreset(preset)
                }
            recalculateBandGains()
            persistLiveSettings()
        }

        // The preset as it plays: with the "more intensity" step on top while the switch is on.
        private fun intensified(preset: Preset): Preset = PresetSettingsMapper.intensified(preset, _moreIntensity.value)

        // An old genre x intensity step (still reachable through a bound profile, a quick tile or
        // an older remote) becomes the genre's base; Aggressive and up switch "more intensity" on.
        private fun withoutRetiredIntensity(preset: Preset): Preset {
            val legacy = PresetIntensityBoost.legacy(preset.id) ?: return preset
            legacy.moreIntensity?.let { _moreIntensity.value = it }
            return legacy.base
        }

        // M3 "My headphones": switches the active correction curve, independent of
        // the voicing (selectPreset above) it gets combined with.
        fun selectCorrectionProfile(profile: CorrectionProfile) {
            corrections.setActive(profile)
            recalculateBandGains()
            persistLiveSettings()
            saveDeviceProfileBinding()
        }

        // From the quick-settings tiles, the widget, or an intent that opened the app for one.
        fun handleQuickCommand(command: QuickCommand) {
            when (command) {
                QuickCommand.TOGGLE_MASTER -> setMasterEnabled(!_processingSettings.value.masterEnabled)
                QuickCommand.NEXT_PRESET -> {
                    val all = allPresets.value.filterNot { PresetIntensityBoost.isRetiredStep(it.id) }
                    val nextId = PresetCycle.nextId(all.map { it.id }, _activePreset.value.id) ?: return
                    all.firstOrNull { it.id == nextId }?.let { selectPreset(it) }
                }
            }
        }

        // Switching off also stops the built-in player. Whether switching on offers the source
        // picker is the screen's business (MainViewModel.setMasterEnabled): without a screen, as
        // from a quick tile, no dialog opens.
        fun setMasterEnabled(enabled: Boolean) {
            val newSettings = _processingSettings.value.copy(masterEnabled = enabled)
            applySettings(newSettings)
            if (!enabled) output.stopPlayer()
        }

        /** Suspends until the saved state has been loaded and applied (E-008: the session service waits for this). */
        suspend fun awaitRestored() = stateRestored.await()

        /** True once [awaitRestored] would return at once. */
        val isRestored: Boolean get() = stateRestored.isCompleted

        // Preset management. The store side lives in PresetPersistenceCoordinator; the screen adds
        // its own confirmations (MainViewModel.feedback).

        /** Saves the current sound as a new user preset and makes it the active one. */
        suspend fun saveAsNewPreset(name: String): Preset {
            val newPreset =
                presets.saveAsNew(
                    name = name,
                    settings = _processingSettings.value,
                    bands = capabilities.value.bands,
                    activePreset = _activePreset.value,
                    moreIntensity = _moreIntensity.value,
                )
            _activePreset.value = newPreset
            _isDirty.value = false
            persistLiveSettings()
            return newPreset
        }

        /** A pasted or scanned link becomes an ordinary, editable user preset and is selected. */
        suspend fun importPresetLink(text: String): Result<Preset> = presets.importLink(text).onSuccess { selectPreset(it) }

        /** Saves a copy of [preset] under a new id and [name] and selects it. */
        suspend fun duplicatePreset(
            preset: Preset,
            name: String,
        ): Preset {
            val copy = presets.duplicate(preset, name)
            selectPreset(copy)
            return copy
        }

        /** Built-ins can't be renamed; returns false then. */
        suspend fun renamePreset(
            preset: Preset,
            newName: String,
        ): Boolean {
            val renamed = presets.rename(preset, newName) ?: return false
            if (_activePreset.value.id == preset.id) {
                _activePreset.value = renamed
                persistLiveSettings()
            }
            return true
        }

        /** Deletes a user preset; the active one falls back to Clean Punch. */
        suspend fun deletePreset(preset: Preset) {
            presets.delete(preset)
            if (_activePreset.value.id == preset.id) {
                selectPreset(BuiltInPresets.CleanPunch)
            }
        }

        /** Saves an imported or suggested correction profile (it must exist before a route binding names it). */
        fun saveCorrectionProfile(profile: CorrectionProfile) = corrections.save(profile)

        fun setBypass(bypass: Boolean) {
            val newSettings = _processingSettings.value.copy(bypass = bypass)
            applySettings(newSettings)
        }

        fun setMacroBass(gainDb: Float) {
            val newSettings = _processingSettings.value.copy(macroBassDb = gainDb)
            _processingSettings.value = newSettings
            _isDirty.value = true
            recalculateBandGains()
        }

        fun setMacroPunch(gainDb: Float) {
            val newSettings = _processingSettings.value.copy(macroPunchDb = gainDb)
            _processingSettings.value = newSettings
            _isDirty.value = true
            recalculateBandGains()
        }

        fun setMacroHaerte(gainDb: Float) {
            val newSettings = _processingSettings.value.copy(macroHaerteDb = gainDb)
            _processingSettings.value = newSettings
            _isDirty.value = true
            recalculateBandGains()
        }

        fun setBandGain(
            bandIndex: Int,
            gainDb: Float,
        ) {
            val currentGains = _processingSettings.value.bandGainsDb.toMutableMap()
            currentGains[bandIndex] = gainDb
            val newSettings =
                _processingSettings.value.copy(
                    bandGainsDb = currentGains,
                    inputGainDb = automaticInputGainDb(currentGains),
                    preEqGainsDb = preEqGainsFor(currentGains),
                )
            _isDirty.value = true
            applySettings(newSettings)
        }

        fun addParametricBand() {
            val current = _parametricBands.value
            if (current.size >= ParametricCurve.MAX_BANDS) return
            // A new band starts where there is room: at 1 kHz first, then at the next free octave.
            val taken = current.map { it.frequencyHz }
            val frequency = PARAMETRIC_START_FREQUENCIES_HZ.firstOrNull { start -> taken.none { abs(it - start) < start * 0.2f } } ?: 1000f
            setParametricBands(current + ParametricBand(frequencyHz = frequency, gainDb = 0f, q = 1f))
        }

        fun updateParametricBand(
            index: Int,
            band: ParametricBand,
        ) {
            val current = _parametricBands.value
            if (index !in current.indices) return
            setParametricBands(current.toMutableList().also { it[index] = band.clamped() })
        }

        fun removeParametricBand(index: Int) {
            val current = _parametricBands.value
            if (index !in current.indices) return
            setParametricBands(current.toMutableList().also { it.removeAt(index) })
        }

        private fun setParametricBands(bands: List<ParametricBand>) {
            _parametricBands.value = bands
            parametricCurve = ParametricCurve.bandsToCurve(bands)
            _isDirty.value = true
            recalculateBandGains()
            persistLiveSettings()
        }

        // The inputs of the target curve as they are right now; the arithmetic lives in CurveCalculator.
        private fun curveInputs(): CurveInputs =
            CurveInputs(
                correctionCurve = corrections.active.value.curve,
                presetCurve = _activePreset.value.targetCurve,
                headphoneAcoustics = effectiveHeadphoneAcoustics.value,
                headphonePower = _headphonePower.value,
                currentLevelDb = volumeRepository.currentLevelDb.value,
                goalCurve = _activeGoal.value.curve,
                context = _activeContext.value,
                ownPlayerSession = isOwnPlayerSession.value,
                preEqAvailable = capabilities.value.preEqCutoffsHz.isNotEmpty(),
                settings = _processingSettings.value,
                parametricCurve = parametricCurve,
            )

        private fun preEqGainsFor(classicBandGainsDb: Map<Int, Float>): List<Float> =
            CurveCalculator.preEqGains(curveInputs(), capabilities.value, classicBandGainsDb)

        /** Switch between the classic 5-band equalizer and the DynamicsProcessing pre-EQ (experimental). */
        fun setHighResolutionEq(enabled: Boolean) {
            applySettings(_processingSettings.value.copy(highResolutionEq = enabled))
            // The band layout is fixed when the effect is created, so it has to be rebuilt.
            output.retryEngine()
        }

        /**
         * Set the pre-EQ response mode: SMOOTH (gentle), BALANCED (default), or PRECISE (aggressive).
         * Controls how manual band adjustments spread and whether Simpson integration is used.
         * More precise = punchier bass, tighter control, but potentially more artifacts.
         */
        fun setPreEqResponseMode(mode: PreEqResponseMode) {
            applySettings(_processingSettings.value.copy(preEqResponseMode = mode.name))
            recalculateBandGains()
        }

        // M3 "combine target curves", mapped onto the hardware bands (CurveCalculator.mapToBands).
        private fun recalculateBandGains(bands: List<EqualizerBandCapabilities> = audioEngine.capabilities.value.bands) {
            // Chat feature: "quality changes for headphones" - gentler dynamics.
            // Recomputed from the canonical activePreset value (not the current
            // processingSettings, which may already be headphone-eased from a
            // previous call) every time this runs, so toggling headphone mode without
            // reselecting a preset still applies/removes the easing, and it never
            // compounds across repeated calls.
            val preset = intensified(_activePreset.value)
            val easeDynamics = effectiveHeadphoneAcoustics.value && _headphonePower.value.easeDynamics
            val dynamics = PresetSettingsMapper.resolveDynamics(preset, _activeContext.value, easeDynamics)
            applySettings(CurveCalculator.mapToBands(curveInputs(), bands, capabilities.value, dynamics))
        }

        // The arithmetic lives in PresetSettingsMapper; these only supply the controller's state.
        private fun ProcessingSettings.withPreset(preset: Preset): ProcessingSettings =
            PresetSettingsMapper.withPreset(this, preset, _moreIntensity.value, _activeContext.value)

        private fun ProcessingSettings.withContextFeatures(basePreset: Preset): ProcessingSettings =
            PresetSettingsMapper.withContextFeatures(this, basePreset, _activeContext.value)

        private fun automaticInputGainDb(bandGainsDb: Map<Int, Float>): Float =
            PresetSettingsMapper.automaticInputGainDb(bandGainsDb, _activeContext.value)

        private fun applySettings(settings: ProcessingSettings) {
            _processingSettings.value = settings
            pushToEngine(settings)
            persistLiveSettings()
        }

        // What the engine and the player really get: see SoundOutputCoordinator.push.
        private fun pushToEngine(settings: ProcessingSettings) {
            output.push(settings) { CurveCalculator.playerCurve(curveInputs()) }
        }

        fun startCompare() = setCompareSide(CompareSide.EQ)

        fun setCompareSide(side: CompareSide) {
            val settings = _processingSettings.value
            compareSession.setSide(side, settings, capabilities.value.bands)
            pushToEngine(settings)
        }

        fun endCompare() {
            if (compareSession.end()) pushToEngine(_processingSettings.value)
        }

        // Individual switches for the three context features, for A/B listening
        // and for enabling them on a preset that does not use them. Switching on
        // restores the active preset's own value, or the car preset's as a sane
        // default if that preset has none. Counts as a manual edit (isDirty).
        fun setLoudnessCompensationEnabled(enabled: Boolean) {
            val onValue = _activePreset.value.loudnessMaxBoostDb.takeIf { it > 0f } ?: BuiltInContextPresets.Car.loudnessMaxBoostDb
            updateContextFeature { it.copy(loudnessMaxBoostDb = if (enabled) onValue else 0f) }
        }

        fun setSubsonicFilterEnabled(enabled: Boolean) {
            val onValue = _activePreset.value.subsonicCutoffHz.takeIf { it > 0f } ?: BuiltInContextPresets.Car.subsonicCutoffHz
            updateContextFeature { it.copy(subsonicCutoffHz = if (enabled) onValue else 0f) }
        }

        fun setVirtualBassEnabled(enabled: Boolean) {
            val onValue = _activePreset.value.virtualBassMix.takeIf { it > 0f } ?: BuiltInContextPresets.Car.virtualBassMix
            updateContextFeature { it.copy(virtualBassMix = if (enabled) onValue else 0f) }
        }

        private fun updateContextFeature(transform: (ProcessingSettings) -> ProcessingSettings) {
            _processingSettings.value = transform(_processingSettings.value)
            _isDirty.value = true
            recalculateBandGains()
        }
    }
