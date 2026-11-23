package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.auracurve.app.R
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.dsp.HeadroomWarningLevel
import com.auracurve.app.ui.eq.EqStatus
import com.auracurve.app.ui.eq.EqStatusKind
import com.auracurve.app.ui.theme.spacing

// Size of the LED meters' scale: 12 dB fills a chain.
private const val METER_RANGE_DB = 12f

// The signal chain (levels, limiter, compressor, headroom), shown in a sheet from the head's
// headroom badge. Split out of EqualizerScreen (roadmap 3).
@Composable
internal fun SignalChainSection(
    engineState: AudioEngineState,
    settings: () -> ProcessingSettings,
    curveModel: () -> EqualizerCurveModel,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val current = settings()
    val model = curveModel()
    val headroom = model.headroom
    val warningLevel = model.warningLevel
    val clippingRisk = headroom.isClippingRisk && current.masterEnabled
    Section {
        SectionHeader(
            title = stringResource(R.string.meters_title),
            info = stringResource(R.string.signal_explain),
        )
        if (EqStatus.kind(engineState, current) != EqStatusKind.ACTIVE) {
            Text(
                text = stringResource(R.string.signal_not_confirmed),
                style = MaterialTheme.typography.bodySmall,
                color = style.mutedText,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LedMeter(
                label = stringResource(R.string.meter_boost),
                value = CurveGeometry.formatGain(headroom.maxPositiveGainDb.coerceAtLeast(0f)),
                fraction = headroom.maxPositiveGainDb / METER_RANGE_DB,
                scale = LedScale.LEVEL,
            )
            LedMeter(
                label = stringResource(R.string.meter_input),
                value = CurveGeometry.formatGain(current.inputGainDb),
                fraction = -current.inputGainDb / METER_RANGE_DB,
                scale = LedScale.REDUCTION,
            )
            LedMeter(
                label = stringResource(R.string.meter_limit),
                value =
                    stringResource(
                        when (warningLevel) {
                            HeadroomWarningLevel.SUFFICIENT -> R.string.meter_limit_low
                            HeadroomWarningLevel.OCCASIONAL_LIMITING -> R.string.meter_limit_some
                            HeadroomWarningLevel.HEAVY_LIMITING -> R.string.meter_limit_heavy
                        },
                    ),
                fraction =
                    when (warningLevel) {
                        HeadroomWarningLevel.SUFFICIENT -> 0.07f
                        HeadroomWarningLevel.OCCASIONAL_LIMITING -> 0.5f
                        HeadroomWarningLevel.HEAVY_LIMITING -> 1f
                    },
                scale = LedScale.HOT,
            )
        }
        Text(
            text =
                if (current.limiterEnabled) {
                    stringResource(R.string.signal_limiter_on, String.format("%.1f", current.limiterThresholdDb))
                } else {
                    stringResource(R.string.signal_limiter_off)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = style.text,
        )
        Text(
            text =
                if (current.mbcEnabled) {
                    stringResource(R.string.signal_mbc_on)
                } else {
                    stringResource(R.string.signal_mbc_off)
                },
            style = MaterialTheme.typography.bodySmall,
            color = style.mutedText,
        )
        val (warningText, warningColor) =
            when (warningLevel) {
                HeadroomWarningLevel.SUFFICIENT ->
                    stringResource(R.string.headroom_ok) to style.mutedText
                HeadroomWarningLevel.OCCASIONAL_LIMITING ->
                    stringResource(R.string.headroom_limiter_sometimes) to style.caution
                HeadroomWarningLevel.HEAVY_LIMITING ->
                    stringResource(R.string.headroom_limiter_heavy) to style.hot
            }
        Text(
            text = warningText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = warningColor,
        )
        Text(
            text =
                if (clippingRisk) {
                    stringResource(
                        R.string.clipping_danger,
                        String.format("%.1f", headroom.maxPositiveGainDb),
                        String.format("%.1f", headroom.recommendedInputGainDb),
                    )
                } else if (current.limiterEnabled) {
                    stringResource(R.string.clipping_protected, String.format("%+.1f", current.inputGainDb))
                } else {
                    stringResource(R.string.clipping_limiter_off, String.format("%+.1f", current.inputGainDb))
                },
            style = MaterialTheme.typography.bodySmall,
            color = if (clippingRisk) style.hot else style.mutedText,
        )
        Text(
            text = stringResource(R.string.meters_estimate_note),
            style = MaterialTheme.typography.bodySmall,
            color = style.mutedText,
        )
    }
}
