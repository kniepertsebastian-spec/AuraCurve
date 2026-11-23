package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.auracurve.app.R
import com.auracurve.app.audio.AudioDeviceType
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.ui.theme.spacing

// The "Device" tab of the equalizer screen: the app the EQ is attached to, the output and what it
// remembers, the headphone correction and the listening context. Split out of EqualizerScreen
// (roadmap 3); headphone power is HeadphonePowerSection.

@Composable
internal fun SessionsSection(
    audioSessions: List<AudioSession>,
    activeAudioSession: AudioSession?,
    appProfileBound: Boolean,
    actions: EqualizerActions,
    style: EqualizerDesignStyle = SonicStyle,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        AudioSourceList(
            sessions = audioSessions,
            activeSession = activeAudioSession,
            onSelect = actions.onSelectAudioSession,
            appProfileBound = appProfileBound,
            onAppProfileBoundChange = actions.onAppProfileBoundChanged,
        )
        Text(
            text = stringResource(R.string.eq_playback_only_note),
            style = MaterialTheme.typography.bodySmall,
            color = style.mutedText,
        )
    }
}

@Composable
internal fun ProfilesSection(
    route: AudioRoute,
    activePresetName: String,
    activeCorrectionProfile: CorrectionProfile,
    onRenameRoute: (String) -> Unit,
    style: EqualizerDesignStyle = SonicStyle,
) {
    Section {
        SectionHeader(
            title = stringResource(R.string.profiles_title),
            info =
                stringResource(R.string.profiles_intro) + "\n" +
                    stringResource(
                        R.string.profiles_saved_for_output,
                        activePresetName,
                        if (activeCorrectionProfile.id == "correction_none") {
                            stringResource(R.string.profiles_correction_none)
                        } else {
                            activeCorrectionProfile.localizedName()
                        },
                    ),
        )
        Text(
            text = stringResource(R.string.profiles_output, route.name),
            style = MaterialTheme.typography.bodyMedium,
            color = style.text,
        )
        var showRenameRoute by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { showRenameRoute = true }) { Text(stringResource(R.string.route_rename)) }
        if (showRenameRoute) {
            RenameRouteDialog(
                currentName = route.name,
                onConfirm = {
                    showRenameRoute = false
                    onRenameRoute(it)
                },
                onDismiss = { showRenameRoute = false },
            )
        }
    }
}

@Composable
internal fun CorrectionSection(
    allProfiles: List<CorrectionProfile>,
    activeProfile: CorrectionProfile,
    effectiveHeadphoneAcoustics: Boolean,
    // Opens the catalogue search; null when there is no search.
    onFindModel: (() -> Unit)?,
    actions: EqualizerActions,
    style: EqualizerDesignStyle = SonicStyle,
) {
    Section {
        SectionHeader(
            title = stringResource(R.string.correction_title),
            trailing = {
                if (onFindModel != null) {
                    TextButton(onClick = onFindModel) {
                        Text(stringResource(R.string.action_find_model))
                    }
                }
                // M5: opens the SAF file picker; parsing and preview happen in
                // MainViewModel once MainScreen has read the picked file.
                TextButton(onClick = actions.onImportCorrectionProfileRequested) {
                    Text(stringResource(R.string.action_import))
                }
            },
        )

        CorrectionProfileRow(
            profiles = allProfiles,
            activeProfileId = activeProfile.id,
            onProfileSelected = actions.onCorrectionProfileSelected,
            onExportProfile = actions.onExportCorrectionProfile,
            spacing = MaterialTheme.spacing,
            style = style,
        )

        // Folds HeadphoneComfortCurve into the applied band-EQ curve
        // (CurveCalculator.combinedCurve()) - a fixed bass/presence tilt, not a literal
        // crossfeed simulation, which the session-attached system effects cannot run.
        // Defaults from the route type until overridden here.
        SwitchRow(
            title = stringResource(R.string.headphone_mode_title),
            description = stringResource(R.string.headphone_mode_desc),
            checked = effectiveHeadphoneAcoustics,
            onCheckedChange = actions.onHeadphoneAcousticsChanged,
            style = style,
        )
    }
}

// Listening context (car / Bluetooth speaker): an overlay on the active genre preset
// (MainViewModel.activeContext); a detected car/speaker route turns it on by itself.
@Composable
internal fun ContextSection(
    route: AudioRoute,
    activeContext: SoundContext?,
    onContextModeChanged: (SoundContext?) -> Unit,
    style: EqualizerDesignStyle = SonicStyle,
) {
    Section {
        SectionHeader(
            title = stringResource(R.string.context_title),
            info = stringResource(R.string.context_default_desc),
        )
        val detectedRoute =
            when (route.type) {
                AudioDeviceType.CAR -> stringResource(R.string.context_car_detected, route.name)
                AudioDeviceType.BLUETOOTH_SPEAKER -> stringResource(R.string.context_speaker_detected, route.name)
                AudioDeviceType.SOUNDBAR -> stringResource(R.string.context_soundbar_detected, route.name)
                else -> null
            }
        if (detectedRoute != null) {
            Text(text = detectedRoute, style = MaterialTheme.typography.bodySmall, color = style.mutedText)
        }
        ContextModeChips(
            activeContext = activeContext,
            onContextModeChanged = onContextModeChanged,
        )
    }
}
