package com.auracurve.app.eq

import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.data.preset.PresetShareCodec
import com.auracurve.app.preset.BuiltInPresets
import com.auracurve.app.preset.LimiterConfig
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.PresetIntensityBoost
import com.auracurve.app.preset.PresetMetadata
import com.auracurve.app.preset.PresetRepository
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.settings.AppSettingsRepository
import com.auracurve.app.settings.LiveSettings
import kotlinx.coroutines.CancellationException
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the equalizer keeps on disk about presets (roadmap 3, split out of [EqController]):
 * the user's own presets, the last live state ([LiveSettings]) and the preset actions that write
 * to the store (save as new, duplicate, rename, delete, import a shared link). Which preset is
 * active stays with [EqController]; these functions only store and hand back the result.
 */
@Singleton
class PresetPersistenceCoordinator
    @Inject
    constructor(
        private val presetRepository: PresetRepository,
        private val appSettingsRepository: AppSettingsRepository,
        @param:EqControllerScope private val scope: CoroutineScope,
    ) {
        private val customPresets = MutableStateFlow<List<Preset>>(emptyList())

        // What the preset grid actually renders: built-ins (fixed, compile-time)
        // followed by whatever custom presets are currently saved.
        val allPresets: StateFlow<List<Preset>> =
            customPresets
                .map { custom -> BuiltInPresets.all + custom }
                .stateIn(scope, SharingStarted.Eagerly, BuiltInPresets.all)

        // The user's own presets only: the genre list shows the built-in sounds, this the rest.
        val customPresetsOnly: StateFlow<List<Preset>> = customPresets.asStateFlow()

        /** Reads the saved presets once, so a restore can resolve ids without racing [followChanges]. */
        suspend fun loadSaved(): List<Preset> {
            val saved = presetRepository.customPresets.first()
            customPresets.value = saved
            return saved
        }

        /** Keeps the list up to date with later saves and deletions (after [loadSaved]). */
        fun followChanges() {
            presetRepository.customPresets
                .drop(1)
                .onEach { customPresets.value = it }
                .launchIn(scope)
        }

        fun findById(
            id: String,
            custom: List<Preset> = customPresets.value,
        ): Preset? = BuiltInPresets.all.find { it.id == id } ?: custom.find { it.id == id }

        /**
         * The last live state, or null when there is none. Damaged saved data must not stop the
         * controller (it would never report itself restored, and the session service waits for
         * that): the compiled-in defaults stay then.
         */
        suspend fun loadLiveSettings(): LiveSettings? =
            try {
                appSettingsRepository.liveSettings.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

        /** Stores the live state; [snapshot] is taken when the write runs, so it holds the newest values. */
        fun saveLiveSettings(snapshot: () -> LiveSettings) {
            scope.launch { appSettingsRepository.save(snapshot()) }
        }

        /** Saves the current sound as a new user preset. */
        suspend fun saveAsNew(
            name: String,
            settings: ProcessingSettings,
            bands: List<EqualizerBandCapabilities>,
            activePreset: Preset,
            moreIntensity: Boolean,
        ): Preset {
            val preset = presetFromSound(name, settings, bands, activePreset, moreIntensity)
            presetRepository.save(preset)
            return preset
        }

        /** A pasted or scanned link becomes an ordinary, editable user preset. */
        suspend fun importLink(text: String): Result<Preset> = PresetShareCodec.decode(text).onSuccess { presetRepository.save(it) }

        /** Saves a copy of [preset] under a new id and [name]. */
        suspend fun duplicate(
            preset: Preset,
            name: String,
        ): Preset {
            val copy =
                preset.copy(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    metadata = preset.metadata.copy(builtIn = false),
                )
            presetRepository.save(copy)
            return copy
        }

        /** The renamed preset, or null for a built-in or a blank name (built-ins can't be renamed). */
        suspend fun rename(
            preset: Preset,
            newName: String,
        ): Preset? {
            if (preset.metadata.builtIn || newName.isBlank()) return null
            val renamed = preset.copy(name = newName)
            presetRepository.save(renamed)
            return renamed
        }

        suspend fun delete(preset: Preset) = presetRepository.delete(preset.id)

        companion object {
            /**
             * The sound as it plays right now, as a preset. A saved preset holds the sound without
             * the "more intensity" step, which the switch adds again on playback - so it is not
             * counted twice.
             */
            fun presetFromSound(
                name: String,
                settings: ProcessingSettings,
                bands: List<EqualizerBandCapabilities>,
                activePreset: Preset,
                moreIntensity: Boolean,
            ): Preset {
                // PresetJsonSerializer.importFromJson rejects an empty targetCurve (and a
                // save with one would just silently disappear as "corrupted" the next
                // time it's read back) - bands is only populated once a session has
                // actually attached, so macro edits made before that (the only manual
                // edit possible with no bands to show sliders for) fall back to the
                // active preset's own curve instead of capturing nothing.
                val curve =
                    if (bands.isNotEmpty()) {
                        bands.map { band ->
                            TargetPoint(frequencyHz = band.centerFreqHz.toFloat(), gainDb = settings.bandGainsDb[band.index] ?: 0f)
                        }
                    } else {
                        activePreset.targetCurve
                    }
                val boost = moreIntensity && PresetIntensityBoost.appliesTo(activePreset)
                return Preset(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    targetCurve = curve,
                    macroBassDb = settings.macroBassDb - if (boost) PresetIntensityBoost.macroBassDeltaDb else 0f,
                    macroPunchDb = settings.macroPunchDb - if (boost) PresetIntensityBoost.macroPunchDeltaDb else 0f,
                    macroHaerteDb = settings.macroHaerteDb,
                    requestedHeadroomDb = activePreset.requestedHeadroomDb,
                    mbcEnabled = settings.mbcEnabled,
                    mbcThresholdDb = settings.mbcThresholdDb,
                    mbcRatio = settings.mbcRatio,
                    limiter = LimiterConfig(enabled = settings.limiterEnabled, thresholdDb = settings.limiterThresholdDb),
                    metadata = PresetMetadata(genre = activePreset.metadata.genre, builtIn = false),
                    loudnessMaxBoostDb = settings.loudnessMaxBoostDb,
                    subsonicCutoffHz = settings.subsonicCutoffHz,
                    virtualBassMix = settings.virtualBassMix,
                )
            }
        }
    }
