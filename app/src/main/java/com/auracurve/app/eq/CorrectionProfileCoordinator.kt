package com.auracurve.app.eq

import com.auracurve.app.correction.BuiltInCorrectionProfiles
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.correction.CorrectionProfileRepository
import com.auracurve.app.profile.CorrectionSuggestionDismissals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M3 "My headphones" (roadmap 3, split out of [EqController]): the headphone/speaker correction
 * curves - the built-in ones plus the user's imported ones - and which one is active. The curve
 * pipeline in [EqController] combines the active one with the preset; route bindings name it by id.
 *
 * Also remembers which outputs the user answered "Not now" for when a catalogue model was
 * suggested (the suggestion itself is screen state, see MainViewModel).
 */
@Singleton
class CorrectionProfileCoordinator
    @Inject
    constructor(
        private val repository: CorrectionProfileRepository,
        private val suggestionDismissals: CorrectionSuggestionDismissals,
        @param:EqControllerScope private val scope: CoroutineScope,
    ) {
        private val customProfiles = MutableStateFlow<List<CorrectionProfile>>(emptyList())

        val allProfiles: StateFlow<List<CorrectionProfile>> =
            customProfiles
                .map { custom -> BuiltInCorrectionProfiles.all + custom }
                .stateIn(scope, SharingStarted.Eagerly, BuiltInCorrectionProfiles.all)

        private val _active = MutableStateFlow(BuiltInCorrectionProfiles.None)
        val active: StateFlow<CorrectionProfile> = _active.asStateFlow()

        /** Reads the saved profiles once, so a restore can resolve ids without racing [followChanges]. */
        suspend fun loadSaved(): List<CorrectionProfile> {
            val saved = repository.customProfiles.first()
            customProfiles.value = saved
            return saved
        }

        /** Keeps the list up to date with later saves and deletions (after [loadSaved]). */
        fun followChanges() {
            repository.customProfiles
                .drop(1)
                .onEach { customProfiles.value = it }
                .launchIn(scope)
        }

        /** The profile with [id]; an id that no longer resolves (deleted elsewhere) falls back to none. */
        fun findById(
            id: String,
            custom: List<CorrectionProfile> = customProfiles.value,
        ): CorrectionProfile =
            BuiltInCorrectionProfiles.all.find { it.id == id }
                ?: custom.find { it.id == id }
                ?: BuiltInCorrectionProfiles.None

        fun setActive(profile: CorrectionProfile) {
            _active.value = profile
        }

        /** Saves an imported or suggested profile (it must exist before a route binding names it). */
        fun save(profile: CorrectionProfile) {
            scope.launch { repository.save(profile) }
        }

        suspend fun isSuggestionDismissed(routeId: String): Boolean = suggestionDismissals.isDismissed(routeId)

        fun dismissSuggestion(routeId: String) = suggestionDismissals.dismiss(routeId)
    }
