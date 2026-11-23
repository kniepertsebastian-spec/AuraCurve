package com.auracurve.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auracurve.app.R
import com.auracurve.app.audio.AudioEffectDescriptor
import com.auracurve.app.integration.PlayerSource
import com.auracurve.app.preset.BuiltInGenrePresets
import com.auracurve.app.preset.Preset
import com.auracurve.app.ui.equalizer.CurvePreview
import com.auracurve.app.ui.equalizer.EqualizerActions
import com.auracurve.app.ui.equalizer.EqualizerContent
import com.auracurve.app.ui.equalizer.appLabelFor
import com.auracurve.app.ui.equalizer.localizedSourceLabel
import com.auracurve.app.ui.main.MainViewModel
import com.auracurve.app.ui.share.SharePresetDialog
import com.auracurve.app.ui.update.ReleaseUpdateNotice

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToDiagnostics: () -> Unit = {},
    // The built-in player has a track loaded (mini player and "Now playing" live in the app shell).
    playerLoaded: Boolean = false,
) {
    // Roadmap 3: the state in topic groups, each changing rarely (see EqualizerUiState.kt).
    val dialogs by viewModel.dialogsUiState.collectAsStateWithLifecycle()
    val device = viewModel.deviceUiState.collectAsStateWithLifecycle()
    val presets = viewModel.presetUiState.collectAsStateWithLifecycle()
    val correction = viewModel.correctionUiState.collectAsStateWithLifecycle()
    val shaping = viewModel.shapingUiState.collectAsStateWithLifecycle()
    val debugTools = viewModel.debugToolsUiState.collectAsStateWithLifecycle()
    // What changes many times a second (slider moves, volume steps, a dragged parametric band) is
    // collected here but not read: EqualizerContent reads it only where it is drawn, so neither
    // changes recompose this screen.
    val settings = viewModel.processingSettings.collectAsStateWithLifecycle()
    val currentLevelDb = viewModel.currentLevelDb.collectAsStateWithLifecycle()
    val parametricBands = viewModel.parametricBands.collectAsStateWithLifecycle()
    // Leaving the equalizer ends a running Original/EQ comparison, so the next track is not heard "as original" by accident.
    DisposableEffect(Unit) { onDispose { viewModel.endCompare() } }
    var showMoreTools by rememberSaveable { mutableStateOf(false) }
    var showSaveAsNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Preset?>(null) }
    var shareTarget by remember { mutableStateOf<Preset?>(null) }

    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val resources = LocalResources.current
    // M5 "integrate import, export and sharing ... via the Android Storage Access
    // Framework/share sheet" - OpenDocument shows the system file picker (any
    // storage provider, no runtime storage permission needed) and hands back a
    // content:// Uri; the file's text is read here (this composable has the
    // Context/ContentResolver access) and handed to the ViewModel, which owns
    // parsing/validation.
    val importCorrectionProfileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val text =
                runCatching {
                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }.getOrNull()
            if (text.isNullOrBlank()) {
                return@rememberLauncherForActivityResult
            }
            val sourceName =
                uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                    ?: resources.getString(R.string.imported_profile_default)
            viewModel.previewCorrectionProfileImport(sourceName, text)
        }

    if (dialogs.showSourcePicker) {
        PlayerSourcePickerDialog(
            onSourceChosen = { viewModel.choosePlayerSource(it) },
            onDismiss = { viewModel.dismissSourcePicker() },
        )
    }

    if (showSaveAsNewDialog) {
        PresetNameDialog(
            title = stringResource(R.string.preset_save_as_new),
            initialName = "",
            onConfirm = { name ->
                viewModel.saveAsNewPreset(name)
                showSaveAsNewDialog = false
            },
            onDismiss = { showSaveAsNewDialog = false },
        )
    }

    shareTarget?.let { target ->
        val link = remember(target) { viewModel.presetShareLink(target) }
        SharePresetDialog(
            presetName = target.name,
            link = link,
            onShareLink = {
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, link)
                        putExtra(Intent.EXTRA_SUBJECT, target.name)
                    }
                context.startActivity(Intent.createChooser(send, resources.getString(R.string.preset_share)))
            },
            onCopyLink = { clipboard.setPrimaryClip(ClipData.newPlainText(target.name, link)) },
            onDismiss = { shareTarget = null },
        )
    }

    renameTarget?.let { target ->
        PresetNameDialog(
            title = stringResource(R.string.preset_rename_title),
            initialName = target.name,
            onConfirm = { name ->
                viewModel.renamePreset(target, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    dialogs.pendingDeletePreset?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeletePreset() },
            title = { Text(stringResource(R.string.preset_delete_title)) },
            text = { Text(stringResource(R.string.preset_delete_text, target.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeletePreset() }) {
                    Text(stringResource(R.string.preset_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeletePreset() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // M5 "the resulting curve is visible before it is applied" / "extreme
    // boosts are not applied silently but limited or confirmed" -
    // nothing is saved until this dialog's "Import" is pressed.
    dialogs.pendingImportPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelCorrectionProfileImport() },
            title = { Text(preview.profile.name) },
            text = {
                Column {
                    Text(stringResource(R.string.import_preview_source, localizedSourceLabel(preview.profile.sourceLabel)))
                    Text(
                        stringResource(
                            R.string.import_preview_range,
                            preview.minFreqHz.toInt(),
                            preview.maxFreqHz.toInt(),
                            preview.profile.curve.size,
                        ),
                    )
                    Text(stringResource(R.string.import_preview_boost, String.format("%.1f", preview.maxBoostDb)))
                    Text(stringResource(R.string.import_preview_headroom, String.format("%.1f", preview.requiredHeadroomDb)))
                    CurvePreview(curve = preview.profile.curve, modifier = Modifier.padding(top = 8.dp))
                    if (preview.isExtremeBoost) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.import_preview_extreme),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCorrectionProfileImport() }) {
                    Text(stringResource(R.string.action_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelCorrectionProfileImport() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    dialogs.importError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportError() },
            title = { Text(stringResource(R.string.import_failed_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportError() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    // Created once: the sections that take it are skipped when nothing they show changed.
    val actions =
        remember(viewModel, context, clipboard, resources, importCorrectionProfileLauncher) {
            EqualizerActions(
                onMasterToggled = viewModel::setMasterEnabled,
                onOpenSourcePicker = viewModel::openSourcePicker,
                onBypassToggled = viewModel::setBypass,
                onRetryAttach = viewModel::retryAttach,
                onPresetSelected = viewModel::selectPreset,
                onMoreIntensityChanged = viewModel::setMoreIntensity,
                onResetToActivePreset = viewModel::resetToActivePreset,
                onSaveAsNewRequest = { showSaveAsNewDialog = true },
                onDuplicatePreset = viewModel::duplicatePreset,
                onRenamePresetRequest = { renameTarget = it },
                onDeletePresetRequest = viewModel::requestDeletePreset,
                onSharePreset = { shareTarget = it },
                onImportPresetLinkRequested = {
                    val clip =
                        clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                    viewModel.importPresetLink(clip.orEmpty())
                },
                onCorrectionProfileSelected = viewModel::selectCorrectionProfile,
                onImportCorrectionProfileRequested = {
                    // "*/*" rather than a specific text MIME type: AutoEQ files are
                    // typically .txt/.csv, but different file managers/providers
                    // report inconsistent MIME types for those (some report
                    // application/octet-stream) - permissive here, AutoEqParser
                    // itself already validates the actual content.
                    importCorrectionProfileLauncher.launch(arrayOf("*/*"))
                },
                onExportCorrectionProfile = { profile ->
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, viewModel.exportCorrectionProfileJson(profile))
                            putExtra(Intent.EXTRA_SUBJECT, profile.name)
                        }
                    context.startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.share_correction_profile)))
                },
                onAcceptSuggestedCorrectionProfile = viewModel::acceptSuggestedCorrectionProfile,
                onDismissSuggestedCorrectionProfile = viewModel::dismissSuggestedCorrectionProfile,
                onHeadphoneAcousticsChanged = viewModel::setHeadphoneAcousticsOverride,
                onHeadphonePowerChanged = viewModel::setHeadphonePower,
                onSearchHeadphones = viewModel::searchHeadphones,
                onHeadphoneChosen = viewModel::applyCatalogHeadphone,
                onGoalSelected = viewModel::setSoundGoal,
                onContextModeChanged = viewModel::setContextMode,
                onLoudnessCompensationChanged = viewModel::setLoudnessCompensationEnabled,
                onSubsonicFilterChanged = viewModel::setSubsonicFilterEnabled,
                onVirtualBassChanged = viewModel::setVirtualBassEnabled,
                onStartCompare = viewModel::startCompare,
                onCompareSide = viewModel::setCompareSide,
                onEndCompare = viewModel::endCompare,
                onMacroBassChanged = viewModel::setMacroBass,
                onMacroPunchChanged = viewModel::setMacroPunch,
                onMacroHaerteChanged = viewModel::setMacroHaerte,
                onBandGainChanged = viewModel::setBandGain,
                onAddParametricBand = viewModel::addParametricBand,
                onChangeParametricBand = viewModel::updateParametricBand,
                onRemoveParametricBand = viewModel::removeParametricBand,
                onHighResolutionEqChanged = viewModel::setHighResolutionEq,
                onPreEqResponseModeChanged = viewModel::setPreEqResponseMode,
                onRenameRoute = viewModel::renameCurrentRoute,
                onSelectAudioSession = viewModel::selectAudioSession,
                onAppProfileBoundChanged = viewModel::setAppProfileBound,
                onAnswerDeviceKind = viewModel::answerDeviceKind,
                onDismissDeviceKindPrompt = viewModel::dismissDeviceKindPrompt,
            )
        }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.feedback.collect { snackbarHostState.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The navigation bar of the app shell below handles the bottom inset.
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReleaseUpdateNotice()
            val activeSession = device.value.activeAudioSession
            EqualizerContent(
                device = device.value,
                presets = presets.value,
                correction = correction.value,
                shaping = shaping.value,
                genrePresets = BuiltInGenrePresets.all,
                pathLabel =
                    activeSession?.let { appLabelFor(context, it.packageName) }
                        ?: if (playerLoaded) stringResource(R.string.source_builtin_player) else null,
                settings = { settings.value },
                currentLevelDb = { currentLevelDb.value },
                parametricBands = { parametricBands.value },
                actions = actions,
                // Diagnostics and debug tools sit at the end of the Device tab instead of taking a
                // row under every tab.
                deviceTabFooter = {
                    // One collapsed entry, opened on demand. The button row scrolls sideways because
                    // the buttons don't fit one screen width.
                    TextButton(
                        onClick = { showMoreTools = !showMoreTools },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(if (showMoreTools) R.string.more_tools_hide else R.string.more_tools_show))
                    }
                    val tools = debugTools.value
                    if (showMoreTools) {
                        Row(
                            modifier =
                                Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp),
                        ) {
                            OutlinedButton(onClick = onNavigateToDiagnostics) {
                                Text(stringResource(R.string.diagnostics_button))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.toggleDebugEffects() }) {
                                Text(
                                    stringResource(
                                        if (tools.showDebugEffects) R.string.hide_debug_effects else R.string.show_debug_effects,
                                    ),
                                )
                            }
                        }
                    }

                    if (tools.showDebugEffects) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(text = stringResource(R.string.debug_effects_title), style = MaterialTheme.typography.titleMedium)
                            if (tools.descriptors.isEmpty()) {
                                Text(text = stringResource(R.string.debug_effects_empty))
                            } else {
                                // A stable key: the list is queried again on every toggle, and an
                                // effect is identified by its type and implementation. A device that
                                // reported one twice must not crash the list (keys have to be unique).
                                val descriptors = remember(tools.descriptors) { tools.descriptors.distinctBy(::effectKey) }
                                LazyColumn(modifier = Modifier.height(100.dp)) {
                                    items(descriptors, key = ::effectKey) { descriptor ->
                                        EffectDescriptorRow(descriptor)
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlayerSourcePickerDialog(
    onSourceChosen: (PlayerSource) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_picker_title)) },
        text = { Text(stringResource(R.string.source_picker_text)) },
        confirmButton = {
            TextButton(onClick = { onSourceChosen(PlayerSource.BUILT_IN) }) {
                Text(stringResource(R.string.source_builtin_player))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

// Shared by "save as new preset" and "rename" - both are just "give this
// preset a name", differing only in title/initial value/what happens on confirm.
@Composable
private fun PresetNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.field_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun effectKey(descriptor: AudioEffectDescriptor): String = "${descriptor.typeUuid}_${descriptor.effectUuid}"

@Composable
private fun EffectDescriptorRow(descriptor: AudioEffectDescriptor) {
    val description = "${descriptor.name}, ${descriptor.implementor}, ${descriptor.connectMode}"
    Column(
        modifier =
            Modifier
                .padding(vertical = 4.dp)
                .semantics { contentDescription = description },
    ) {
        Text(text = descriptor.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "${descriptor.implementor} · ${descriptor.connectMode} · ${descriptor.typeUuid}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
