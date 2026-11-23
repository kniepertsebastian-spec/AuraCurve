package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.auracurve.app.R
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.dsp.PreEqResponseMode
import com.auracurve.app.ui.theme.spacing

// The "Fine tune" tab of the equalizer screen: macros, the band sliders, the parametric bands and
// the high-resolution switch. Split out of EqualizerScreen (roadmap 3). The sliders follow the
// live settings, so this is where a slider move recomposes - and only here and in the curve.

@Composable
internal fun FineTuneSection(
    settings: () -> ProcessingSettings,
    curveModel: () -> EqualizerCurveModel,
    bandCount: Int,
    selectedBand: Int?,
    onSelectBand: (Int) -> Unit,
    actions: EqualizerActions,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val spacing = MaterialTheme.spacing
    val current = settings()
    Section {
        SectionHeader(title = stringResource(R.string.fine_tune_title))
        Text(
            text = stringResource(R.string.fine_tune_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = style.mutedText,
        )
        Text(
            text = stringResource(R.string.macro_title),
            style = MaterialTheme.typography.titleSmall,
            color = style.text,
            modifier = Modifier.padding(top = spacing.small),
        )
        ValueSlider(
            label = stringResource(R.string.macro_bass_name),
            description = stringResource(R.string.macro_bass_desc),
            value = current.macroBassDb,
            onValueChange = actions.onMacroBassChanged,
            valueRange = -6f..6f,
            style = style,
        )
        ValueSlider(
            label = stringResource(R.string.macro_punch_name),
            description = stringResource(R.string.macro_punch_desc),
            value = current.macroPunchDb,
            onValueChange = actions.onMacroPunchChanged,
            valueRange = -6f..6f,
            style = style,
        )
        ValueSlider(
            label = stringResource(R.string.macro_haerte_name),
            description = stringResource(R.string.macro_haerte_desc),
            value = current.macroHaerteDb,
            onValueChange = actions.onMacroHaerteChanged,
            valueRange = -2f..2f,
            style = style,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.bands_title, bandCount),
                style = MaterialTheme.typography.titleSmall,
                color = style.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.bypass_label),
                style = MaterialTheme.typography.bodyMedium,
                color = style.text,
            )
            Spacer(modifier = Modifier.width(spacing.small))
            val bypassDescription = stringResource(R.string.bypass_label)
            Switch(
                modifier = Modifier.semantics { contentDescription = bypassDescription },
                checked = current.bypass,
                onCheckedChange = actions.onBypassToggled,
            )
        }
        // The same bands as the graph, as sliders: the selected one is marked in
        // both places, and moving a slider selects its point.
        Column {
            curveModel().graphBands.sortedBy { it.frequencyHz }.forEach { band ->
                BandSliderRow(
                    band = band,
                    selected = band.index == selectedBand,
                    onGainChange = { gain ->
                        onSelectBand(band.index)
                        actions.onBandGainChanged(band.index, gain)
                    },
                    style = style,
                )
            }
        }
    }
}

// The free bands, read here so dragging one does not recompose the tab around it.
@Composable
internal fun ParametricSection(
    bands: () -> List<ParametricBand>,
    actions: EqualizerActions,
    style: EqualizerDesignStyle = SonicStyle,
) {
    ParametricBandsSection(
        bands = bands(),
        onAdd = actions.onAddParametricBand,
        onChange = actions.onChangeParametricBand,
        onRemove = actions.onRemoveParametricBand,
        spacing = MaterialTheme.spacing,
        style = style,
    )
}

// The high-resolution switch; only shown when the caller handles it.
@Composable
internal fun HighResolutionSection(
    settings: () -> ProcessingSettings,
    responseMode: () -> PreEqResponseMode,
    preEqActive: Boolean,
    actions: EqualizerActions,
) {
    val onChanged = actions.onHighResolutionEqChanged ?: return
    val checked by remember(settings) { derivedStateOf { settings().highResolutionEq } }
    val mode by remember(responseMode) { derivedStateOf { responseMode() } }
    HighResolutionEqRow(
        checked = checked,
        active = preEqActive,
        onCheckedChange = onChanged,
        responseMode = mode,
        onResponseModeChange = actions.onPreEqResponseModeChanged,
    )
}
