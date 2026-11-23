package com.auracurve.app.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.autoeq.AutoEqCatalogEntry
import com.auracurve.app.dsp.HeadroomWarningLevel
import com.auracurve.app.preset.Preset
import com.auracurve.app.ui.eq.EqStatus
import com.auracurve.app.ui.eq.EqStatusKind
import com.auracurve.app.ui.eq.EqStatusLine
import com.auracurve.app.ui.theme.spacing

// The fixed head of the equalizer screen (docs/EQUALIZER_REDESIGN.md): output and preset, status,
// and the response curve. Split out of EqualizerScreen (roadmap 3) so the parts that change with
// every slider move - the curve and the selected band's readout - recompose on their own; the
// title and status rows only follow the few values they show.

@Composable
internal fun EqualizerHead(
    route: AudioRoute,
    activePreset: Preset,
    isDirty: Boolean,
    engineState: AudioEngineState,
    pathLabel: String?,
    settings: () -> ProcessingSettings,
    curveModel: () -> EqualizerCurveModel,
    selectedBand: Int?,
    onSelectBand: (Int?) -> Unit,
    graphHeight: Dp,
    collapsed: Boolean,
    onExpand: () -> Unit,
    noSessionDetails: Boolean,
    onToggleNoSessionDetails: () -> Unit,
    onOpenSignalChain: () -> Unit,
    actions: EqualizerActions,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val spacing = MaterialTheme.spacing
    // Read through derived states: they change only when the value shown changes, not with every
    // band gain the settings carry while a slider moves.
    val masterEnabled by remember(settings) { derivedStateOf { settings().masterEnabled } }
    val statusKind by remember(engineState, settings) { derivedStateOf { EqStatus.kind(engineState, settings()) } }
    val warningLevel by remember(settings, curveModel) {
        derivedStateOf { if (settings().masterEnabled) curveModel().warningLevel else null }
    }
    val waitingForSession = engineState.isWaitingForSession()
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        HeadTitleRow(
            route = route,
            activePreset = activePreset,
            isDirty = isDirty,
            masterEnabled = masterEnabled,
            onOpenSourcePicker = actions.onOpenSourcePicker,
            onMasterToggled = actions.onMasterToggled,
            style = style,
        )
        HeadStatusRow(
            statusKind = statusKind,
            pathLabel = pathLabel,
            waitingForSession = waitingForSession,
            noSessionDetails = noSessionDetails,
            onToggleNoSessionDetails = onToggleNoSessionDetails,
            warningLevel = warningLevel,
            onOpenSignalChain = onOpenSignalChain,
            style = style,
        )
        AnimatedVisibility(visible = waitingForSession && noSessionDetails) {
            Text(
                text = stringResource(R.string.no_session_text),
                style = MaterialTheme.typography.bodySmall,
                color = style.mutedText,
            )
        }
        AnimatedVisibility(visible = isDirty) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = actions.onResetToActivePreset) { Text(stringResource(R.string.compare_reset)) }
                TextButton(onClick = actions.onSaveAsNewRequest) { Text(stringResource(R.string.action_save)) }
            }
        }
        HeadGraph(
            settings = settings,
            curveModel = curveModel,
            selectedBand = selectedBand,
            onSelectBand = onSelectBand,
            graphHeight = graphHeight,
            collapsed = collapsed,
            onExpand = onExpand,
            onBandGainChanged = actions.onBandGainChanged,
        )
    }
}

@Composable
private fun HeadTitleRow(
    route: AudioRoute,
    activePreset: Preset,
    isDirty: Boolean,
    masterEnabled: Boolean,
    onOpenSourcePicker: () -> Unit,
    onMasterToggled: (Boolean) -> Unit,
    style: EqualizerDesignStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Caption(text = stringResource(R.string.route_label, route.name), color = style.mutedText)
            Text(
                text =
                    if (isDirty) {
                        stringResource(R.string.compare_preset_custom, activePreset.name)
                    } else {
                        activePreset.name
                    },
                style = MaterialTheme.typography.headlineSmall,
                color = style.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Independent of the master switch - that one only offers the source
        // picker when flipped on with nothing attached yet (see
        // MainViewModel.setMasterEnabled).
        IconButton(onClick = onOpenSourcePicker) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = stringResource(R.string.eq_pick_source),
                tint = style.mutedText,
            )
        }
        PowerKey(
            checked = masterEnabled,
            onCheckedChange = onMasterToggled,
            description = stringResource(R.string.eq_master_switch),
        )
    }
}

@Composable
private fun HeadStatusRow(
    statusKind: EqStatusKind,
    pathLabel: String?,
    waitingForSession: Boolean,
    noSessionDetails: Boolean,
    onToggleNoSessionDetails: () -> Unit,
    warningLevel: HeadroomWarningLevel?,
    onOpenSignalChain: () -> Unit,
    style: EqualizerDesignStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        // Waiting for a session: the line itself opens what to do (it used to be a second card
        // below the curve saying the same).
        EqStatusLine(
            kind = statusKind,
            pathLabel = pathLabel,
            onClick = { if (waitingForSession) onToggleNoSessionDetails() },
            modifier = Modifier.weight(1f),
            trailing =
                if (waitingForSession) {
                    {
                        Icon(
                            imageVector = if (noSessionDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription =
                                stringResource(if (noSessionDetails) R.string.info_less else R.string.info_more),
                            tint = style.mutedText,
                        )
                    }
                } else {
                    null
                },
        )
        HeadroomBadge(
            level = warningLevel,
            onClick = onOpenSignalChain,
            style = style,
        )
    }
}

// The response curve and, while a band is selected, its readout: the part of the head that follows
// every slider move and every volume step (loudness compensation).
@Composable
private fun ColumnScope.HeadGraph(
    settings: () -> ProcessingSettings,
    curveModel: () -> EqualizerCurveModel,
    selectedBand: Int?,
    onSelectBand: (Int?) -> Unit,
    graphHeight: Dp,
    collapsed: Boolean,
    onExpand: () -> Unit,
    onBandGainChanged: (Int, Float) -> Unit,
) {
    val shownGraphHeight by animateDpAsState(
        targetValue = if (collapsed) CollapsedGraphHeight else graphHeight,
        label = "graphHeight",
    )
    val model = curveModel()
    val current = settings()
    val curvePeak = CurveGeometry.strongestChange(model.curvePoints)
    val curveDescription =
        if (curvePeak != null) {
            stringResource(
                R.string.eq_curve_description,
                String.format("%+.1f", curvePeak.gainDb),
                CurveGeometry.formatFrequency(curvePeak.frequencyHz),
            )
        } else {
            stringResource(R.string.eq_curve_flat)
        }
    val heavyLimiting = current.masterEnabled && model.warningLevel == HeadroomWarningLevel.HEAVY_LIMITING
    val graphBands = model.graphBands
    Box {
        ResponseGraph(
            height = shownGraphHeight,
            collapsed = collapsed,
            hint =
                if (selectedBand == null) {
                    stringResource(
                        if (graphBands.isEmpty()) R.string.graph_hint_no_bands_short else R.string.graph_hint_select_short,
                    )
                } else {
                    null
                },
            curve = model.curvePoints,
            bands = graphBands,
            selectedIndex = selectedBand,
            onSelect = onSelectBand,
            onGainChange = onBandGainChanged,
            processing = current.masterEnabled && !current.bypass,
            stateLabel =
                when {
                    !current.masterEnabled -> stringResource(R.string.graph_state_off)
                    current.bypass -> stringResource(R.string.graph_state_bypass)
                    else -> null
                },
            hotFrequencyHz = curvePeak?.takeIf { heavyLimiting && it.gainDb > 0f }?.frequencyHz,
            description = curveDescription,
        )
        if (collapsed) {
            val expand = stringResource(R.string.graph_expand)
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clickable(onClickLabel = expand, role = Role.Button, onClick = onExpand),
            )
        }
    }
    // The readout takes a row only while a band is selected; the help sits in the graph.
    val selected = graphBands.firstOrNull { it.index == selectedBand }
    AnimatedVisibility(visible = selected != null && !collapsed) {
        BandReadout(
            band = selected,
            onGainChange = onBandGainChanged,
            hint = "",
        )
    }
}

// At most one notice, the most urgent first (docs/EQUALIZER_REDESIGN.md, 3.2).
@Composable
internal fun NoticeSlot(
    engineState: AudioEngineState,
    deviceKindPrompt: AudioRoute?,
    suggestedCorrectionProfile: AutoEqCatalogEntry?,
    routeName: String,
    // Opens the catalogue search with a starting query; null when there is no search.
    onSearchModel: ((String) -> Unit)?,
    actions: EqualizerActions,
    style: EqualizerDesignStyle = SonicStyle,
) {
    when {
        engineState is AudioEngineState.Error ->
            OutlinedButton(onClick = actions.onRetryAttach, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_retry))
            }
        deviceKindPrompt != null ->
            DeviceKindCard(
                deviceName = deviceKindPrompt.name,
                onAnswer = actions.onAnswerDeviceKind,
                onDismiss = actions.onDismissDeviceKindPrompt,
            )
        // A suggestion only, never applied silently - see
        // MainViewModel.updateSuggestedCorrectionProfile()/AutoEqCatalogMatcher.
        suggestedCorrectionProfile != null ->
            Notice {
                Text(
                    text = stringResource(R.string.correction_detected, suggestedCorrectionProfile.displayName),
                    style = MaterialTheme.typography.titleSmall,
                    color = style.text,
                )
                Text(
                    text =
                        stringResource(
                            R.string.correction_suggestion,
                            localizedSourceLabel(suggestedCorrectionProfile.profile.sourceLabel),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.mutedText,
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (onSearchModel != null) {
                        TextButton(onClick = { onSearchModel(routeName) }) {
                            Text(stringResource(R.string.action_other_model))
                        }
                    }
                    TextButton(onClick = actions.onDismissSuggestedCorrectionProfile) {
                        Text(stringResource(R.string.action_not_now))
                    }
                    TextButton(onClick = actions.onAcceptSuggestedCorrectionProfile) {
                        Text(stringResource(R.string.action_apply))
                    }
                }
            }
    }
}

/** Detached is only the brief cold-start window; Listening is the idle time without a session. */
internal fun AudioEngineState.isWaitingForSession(): Boolean = this is AudioEngineState.Detached || this is AudioEngineState.Listening
