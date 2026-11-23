package com.auracurve.app.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.preset.GenrePreset
import com.auracurve.app.preset.GenrePresetGroups
import com.auracurve.app.preset.Preset
import com.auracurve.app.ui.main.CompareSide
import com.auracurve.app.ui.main.CompareState
import com.auracurve.app.ui.theme.spacing

// The "Sound" tab of the equalizer screen: genre presets, signal modules and the Original/EQ
// comparison (the sound goal is SoundGoalCard). Split out of EqualizerScreen (roadmap 3).

@Composable
internal fun GenreSection(
    activePreset: Preset,
    customPresets: List<Preset>,
    genrePresets: List<GenrePreset>,
    moreIntensity: Boolean,
    actions: EqualizerActions,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val spacing = MaterialTheme.spacing
    var presetUsageOpen by rememberSaveable { mutableStateOf(false) }
    Section {
        SectionHeader(title = stringResource(R.string.genre_title)) {
            // P5-4: a preset link someone sent (or a scanned QR code, which opens the app directly).
            TextButton(onClick = actions.onImportPresetLinkRequested) {
                Text(stringResource(R.string.preset_import_link))
            }
            // When the active preset (genre or custom) is a good choice; each custom preset
            // also has it in its own menu.
            IconButton(onClick = { presetUsageOpen = !presetUsageOpen }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription =
                        stringResource(
                            if (presetUsageOpen) R.string.preset_usage_active_less else R.string.preset_usage_active_more,
                        ),
                    tint = if (presetUsageOpen) style.accent else style.mutedText,
                )
            }
        }
        AnimatedVisibility(visible = presetUsageOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = spacing.small)) {
                Text(
                    text = stringResource(R.string.preset_usage_active_label, activePreset.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = style.text,
                )
                PresetUsageText(preset = activePreset, style = style)
            }
        }
        // One switch instead of the five intensity steps: the former "Aggressive" on top of
        // whatever preset is active (PresetIntensityBoost).
        SwitchRow(
            title = stringResource(R.string.more_intensity_title),
            description = stringResource(R.string.more_intensity_desc),
            checked = moreIntensity,
            onCheckedChange = actions.onMoreIntensityChanged,
            style = style,
        )
        GenrePresetList(
            groups = remember(genrePresets) { GenrePresetGroups.forGenres(genrePresets) },
            activePresetId = activePreset.id,
            onPresetSelected = actions.onPresetSelected,
            onDuplicatePreset = actions.onDuplicatePreset,
            onSharePreset = actions.onSharePreset,
            style = style,
        )
        if (customPresets.isNotEmpty()) {
            var customOpen by rememberSaveable { mutableStateOf(true) }
            val activeCustom = customPresets.firstOrNull { it.id == activePreset.id }
            Spacer(modifier = Modifier.height(2.dp))
            ExpandableGroupHeader(
                title = stringResource(R.string.presets_custom),
                subtitle =
                    pluralStringResource(R.plurals.genre_presets_count, customPresets.size, customPresets.size) +
                        (activeCustom?.let { " · " + stringResource(R.string.genre_presets_active, it.name) } ?: ""),
                icon = Icons.Outlined.Tune,
                expanded = customOpen,
                highlighted = activeCustom != null,
                onToggle = { customOpen = !customOpen },
                style = style,
            )
            AnimatedVisibility(visible = customOpen) {
                PresetGrid(
                    presets = customPresets,
                    activePresetId = activePreset.id,
                    onPresetSelected = actions.onPresetSelected,
                    onDuplicatePreset = actions.onDuplicatePreset,
                    onRenamePresetRequest = actions.onRenamePresetRequest,
                    onDeletePresetRequest = actions.onDeletePresetRequest,
                    onSharePreset = actions.onSharePreset,
                    spacing = spacing,
                    style = style,
                )
            }
        }
    }
}

// The three module values the tiles show: they change when a module is switched, not with the
// band gains, so a slider move does not recompose the tiles.
@Immutable
private data class ModuleValues(
    val loudnessMaxBoostDb: Float,
    val subsonicCutoffHz: Float,
    val virtualBassMix: Float,
)

// Virtual Bass, loudness and subsonic: switched often, so they sit with the sound choice.
@Composable
internal fun ModulesSection(
    settings: () -> ProcessingSettings,
    preEqActive: Boolean,
    isOwnPlayerSession: Boolean,
    actions: EqualizerActions,
) {
    val modules by remember(settings) {
        derivedStateOf {
            val current = settings()
            ModuleValues(current.loudnessMaxBoostDb, current.subsonicCutoffHz, current.virtualBassMix)
        }
    }
    Section {
        SectionHeader(
            title = stringResource(R.string.modules_title),
            info =
                listOf(
                    stringResource(R.string.context_loudness_desc),
                    if (modules.subsonicCutoffHz > 0f) {
                        stringResource(R.string.context_subsonic_on, modules.subsonicCutoffHz.toInt())
                    } else {
                        stringResource(R.string.context_subsonic_off)
                    },
                    stringResource(R.string.context_virtual_bass_desc),
                ).joinToString("\n"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            SignalModuleTile(
                title = stringResource(R.string.module_loudness),
                detail = stringResource(if (modules.loudnessMaxBoostDb > 0f) R.string.module_on else R.string.module_off),
                checked = modules.loudnessMaxBoostDb > 0f,
                onCheckedChange = actions.onLoudnessCompensationChanged,
                icon = Icons.Outlined.Hearing,
                modifier = Modifier.weight(1f),
            )
            SignalModuleTile(
                title = stringResource(R.string.module_subsonic),
                detail =
                    if (modules.subsonicCutoffHz > 0f) {
                        // The 5-band system EQ can only lower its lowest band, not cut like a high-pass.
                        val prefix = if (preEqActive) "<" else "≈"
                        "$prefix ${modules.subsonicCutoffHz.toInt()} Hz"
                    } else {
                        stringResource(R.string.module_off)
                    },
                checked = modules.subsonicCutoffHz > 0f,
                onCheckedChange = actions.onSubsonicFilterChanged,
                icon = Icons.Outlined.FilterAlt,
                modifier = Modifier.weight(1f),
            )
            SignalModuleTile(
                title = stringResource(R.string.module_virtual_bass),
                detail =
                    stringResource(
                        when {
                            modules.virtualBassMix <= 0f -> R.string.module_off
                            !isOwnPlayerSession && preEqActive -> R.string.module_approx
                            !isOwnPlayerSession -> R.string.module_player_only
                            else -> R.string.module_on
                        },
                    ),
                checked = modules.virtualBassMix > 0f,
                onCheckedChange = actions.onVirtualBassChanged,
                icon = Icons.Outlined.GraphicEq,
                modifier = Modifier.weight(1f).alpha(if (isOwnPlayerSession) 1f else 0.6f),
            )
        }
    }
}

@Composable
internal fun CompareSection(
    compare: CompareState?,
    actions: EqualizerActions,
    style: EqualizerDesignStyle = SonicStyle,
) {
    Section {
        // The side chips sit in the header; the idle explanation is behind (i), and the
        // level-matched delta shows only while comparing.
        SectionHeader(
            title = stringResource(R.string.compare_title),
            info = stringResource(R.string.compare_hint_idle),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceChip(
                text = stringResource(R.string.compare_original),
                selected = compare?.side == CompareSide.ORIGINAL,
                onClick = { actions.onCompareSide(CompareSide.ORIGINAL) },
            )
            ChoiceChip(
                text = stringResource(R.string.compare_eq),
                selected = compare == null || compare.side == CompareSide.EQ,
                onClick = { if (compare == null) actions.onStartCompare() else actions.onCompareSide(CompareSide.EQ) },
            )
            HoldToCompareButton(
                modifier = Modifier.weight(1f),
                onPress = { actions.onCompareSide(CompareSide.ORIGINAL) },
                onRelease = actions.onEndCompare,
                style = style,
            )
        }
        if (compare != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.compare_hint_active, String.format("%+.1f", compare.eqLevelDeltaDb)),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onEndCompare) { Text(stringResource(R.string.compare_done)) }
            }
        }
    }
}
