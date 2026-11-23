package com.auracurve.app.ui.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.AudioSession
import com.auracurve.app.audio.DeviceKind
import com.auracurve.app.audio.EqualizerBandCapabilities
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.autoeq.AutoEqCatalogEntry
import com.auracurve.app.context.SoundContext
import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.dsp.HeadphonePower
import com.auracurve.app.dsp.ParametricBand
import com.auracurve.app.dsp.PreEqResponseMode
import com.auracurve.app.preset.GenrePreset
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.ui.main.CompareSide
import com.auracurve.app.ui.main.CompareState
import com.auracurve.app.ui.main.EqCorrectionUiState
import com.auracurve.app.ui.main.EqDeviceUiState
import com.auracurve.app.ui.main.EqPresetUiState
import com.auracurve.app.ui.main.EqShapingUiState
import com.auracurve.app.ui.theme.spacing

/**
 * The equalizer screen with every input as a plain value (the form previews and the UI tests
 * use). It maps them onto the grouped state of [EqualizerContent], which MainScreen calls directly.
 */
@Composable
fun EqualizerScreen(
    state: AudioEngineState,
    route: AudioRoute,
    settings: ProcessingSettings,
    bands: List<EqualizerBandCapabilities>,
    activePreset: Preset,
    customPresets: List<Preset>,
    genrePresets: List<GenrePreset>,
    moreIntensity: Boolean,
    activeCorrectionProfile: CorrectionProfile,
    allCorrectionProfiles: List<CorrectionProfile>,
    suggestedCorrectionProfile: AutoEqCatalogEntry?,
    effectiveHeadphoneAcoustics: Boolean,
    onRenameRoute: (String) -> Unit = {},
    activeGoal: SoundGoal,
    onGoalSelected: (SoundGoal) -> Unit,
    pathLabel: String?,
    compare: CompareState?,
    onStartCompare: () -> Unit,
    onCompareSide: (CompareSide) -> Unit,
    onEndCompare: () -> Unit,
    headphonePower: HeadphonePower,
    activeContext: SoundContext?,
    currentLevelDb: Float,
    isDirty: Boolean,
    onMasterToggled: (Boolean) -> Unit,
    onOpenSourcePicker: () -> Unit,
    onBypassToggled: (Boolean) -> Unit,
    onPresetSelected: (Preset) -> Unit,
    onMoreIntensityChanged: (Boolean) -> Unit,
    onCorrectionProfileSelected: (CorrectionProfile) -> Unit,
    onImportCorrectionProfileRequested: () -> Unit,
    onExportCorrectionProfile: (CorrectionProfile) -> Unit,
    onAcceptSuggestedCorrectionProfile: () -> Unit,
    onDismissSuggestedCorrectionProfile: () -> Unit,
    onHeadphoneAcousticsChanged: (Boolean) -> Unit,
    onHeadphonePowerChanged: (HeadphonePower) -> Unit,
    onContextModeChanged: (SoundContext?) -> Unit,
    onLoudnessCompensationChanged: (Boolean) -> Unit,
    onSubsonicFilterChanged: (Boolean) -> Unit,
    onVirtualBassChanged: (Boolean) -> Unit,
    onResetToActivePreset: () -> Unit,
    onSaveAsNewRequest: () -> Unit,
    onDuplicatePreset: (Preset) -> Unit,
    onRenamePresetRequest: (Preset) -> Unit,
    onDeletePresetRequest: (Preset) -> Unit,
    onSharePreset: (Preset) -> Unit,
    onImportPresetLinkRequested: () -> Unit,
    onMacroBassChanged: (Float) -> Unit,
    onMacroPunchChanged: (Float) -> Unit,
    onMacroHaerteChanged: (Float) -> Unit,
    onBandGainChanged: (bandIndex: Int, gainDb: Float) -> Unit,
    onRetryAttach: () -> Unit,
    audioSessions: List<AudioSession> = emptyList(),
    activeAudioSession: AudioSession? = null,
    parametricBands: List<ParametricBand> = emptyList(),
    onAddParametricBand: () -> Unit = {},
    onChangeParametricBand: (Int, ParametricBand) -> Unit = { _, _ -> },
    onRemoveParametricBand: (Int) -> Unit = {},
    appProfileBound: Boolean = false,
    onAppProfileBoundChanged: ((Boolean) -> Unit)? = null,
    isOwnPlayerSession: Boolean = true,
    deviceKindPrompt: AudioRoute? = null,
    onAnswerDeviceKind: (DeviceKind) -> Unit = {},
    onDismissDeviceKindPrompt: () -> Unit = {},
    preEqActive: Boolean = false,
    onHighResolutionEqChanged: ((Boolean) -> Unit)? = null,
    preEqResponseMode: PreEqResponseMode = PreEqResponseMode.BALANCED,
    onPreEqResponseModeChanged: ((PreEqResponseMode) -> Unit)? = null,
    onSelectAudioSession: (AudioSession) -> Unit = {},
    // The bundled AutoEq catalogue: searching it by name and picking a model. Null hides the search.
    onSearchHeadphones: (suspend (String) -> List<AutoEqCatalogEntry>)? = null,
    onHeadphoneChosen: (AutoEqCatalogEntry) -> Unit = {},
    // Rarely used extras (diagnostics, debug) at the end of the Device tab, out of the way.
    deviceTabFooter: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // As snapshot states, so the content reads them where they are drawn, like MainScreen's flows.
    val currentSettings = rememberUpdatedState(settings)
    val currentLevel = rememberUpdatedState(currentLevelDb)
    val currentParametricBands = rememberUpdatedState(parametricBands)
    val currentResponseMode = rememberUpdatedState(preEqResponseMode)
    EqualizerContent(
        device =
            EqDeviceUiState(
                engineState = state,
                route = route,
                bands = bands,
                preEqActive = preEqActive,
                audioSessions = audioSessions,
                activeAudioSession = activeAudioSession,
                appProfileBound = appProfileBound,
                isOwnPlayerSession = isOwnPlayerSession,
                deviceKindPrompt = deviceKindPrompt,
            ),
        presets = EqPresetUiState(activePreset, customPresets, moreIntensity, isDirty),
        correction =
            EqCorrectionUiState(
                activeProfile = activeCorrectionProfile,
                allProfiles = allCorrectionProfiles,
                suggested = suggestedCorrectionProfile,
                effectiveHeadphoneAcoustics = effectiveHeadphoneAcoustics,
                headphonePower = headphonePower,
            ),
        shaping = EqShapingUiState(activeGoal = activeGoal, activeContext = activeContext, compare = compare),
        genrePresets = genrePresets,
        pathLabel = pathLabel,
        settings = { currentSettings.value },
        currentLevelDb = { currentLevel.value },
        parametricBands = { currentParametricBands.value },
        preEqResponseMode = { currentResponseMode.value },
        actions =
            EqualizerActions(
                onMasterToggled = onMasterToggled,
                onOpenSourcePicker = onOpenSourcePicker,
                onBypassToggled = onBypassToggled,
                onRetryAttach = onRetryAttach,
                onPresetSelected = onPresetSelected,
                onMoreIntensityChanged = onMoreIntensityChanged,
                onResetToActivePreset = onResetToActivePreset,
                onSaveAsNewRequest = onSaveAsNewRequest,
                onDuplicatePreset = onDuplicatePreset,
                onRenamePresetRequest = onRenamePresetRequest,
                onDeletePresetRequest = onDeletePresetRequest,
                onSharePreset = onSharePreset,
                onImportPresetLinkRequested = onImportPresetLinkRequested,
                onCorrectionProfileSelected = onCorrectionProfileSelected,
                onImportCorrectionProfileRequested = onImportCorrectionProfileRequested,
                onExportCorrectionProfile = onExportCorrectionProfile,
                onAcceptSuggestedCorrectionProfile = onAcceptSuggestedCorrectionProfile,
                onDismissSuggestedCorrectionProfile = onDismissSuggestedCorrectionProfile,
                onHeadphoneAcousticsChanged = onHeadphoneAcousticsChanged,
                onHeadphonePowerChanged = onHeadphonePowerChanged,
                onSearchHeadphones = onSearchHeadphones,
                onHeadphoneChosen = onHeadphoneChosen,
                onGoalSelected = onGoalSelected,
                onContextModeChanged = onContextModeChanged,
                onLoudnessCompensationChanged = onLoudnessCompensationChanged,
                onSubsonicFilterChanged = onSubsonicFilterChanged,
                onVirtualBassChanged = onVirtualBassChanged,
                onStartCompare = onStartCompare,
                onCompareSide = onCompareSide,
                onEndCompare = onEndCompare,
                onMacroBassChanged = onMacroBassChanged,
                onMacroPunchChanged = onMacroPunchChanged,
                onMacroHaerteChanged = onMacroHaerteChanged,
                onBandGainChanged = onBandGainChanged,
                onAddParametricBand = onAddParametricBand,
                onChangeParametricBand = onChangeParametricBand,
                onRemoveParametricBand = onRemoveParametricBand,
                onHighResolutionEqChanged = onHighResolutionEqChanged,
                onPreEqResponseModeChanged = onPreEqResponseModeChanged,
                onRenameRoute = onRenameRoute,
                onSelectAudioSession = onSelectAudioSession,
                onAppProfileBoundChanged = onAppProfileBoundChanged,
                onAnswerDeviceKind = onAnswerDeviceKind,
                onDismissDeviceKindPrompt = onDismissDeviceKindPrompt,
            ),
        deviceTabFooter = deviceTabFooter,
        modifier = modifier,
    )
}

/**
 * The equalizer screen (AuraCurve roadmap 04, part A, docs/EQUALIZER_REDESIGN.md): a fixed head
 * with the curve, one notice at a time below it, and the rest in three tabs sorted by how often it
 * is used.
 *
 * Roadmap 3: the state comes in topic groups that change rarely; what changes many times a second
 * - [settings] while a slider moves, [currentLevelDb] with every volume step, [parametricBands]
 * while one is dragged - comes as a function and is read only in the sections that show it (the
 * curve, the sliders, the signal chain). Every section is its own composable with the values it
 * shows as parameters, so it is skipped when none of them changed.
 */
@Composable
internal fun EqualizerContent(
    device: EqDeviceUiState,
    presets: EqPresetUiState,
    correction: EqCorrectionUiState,
    shaping: EqShapingUiState,
    genrePresets: List<GenrePreset>,
    pathLabel: String?,
    settings: () -> ProcessingSettings,
    currentLevelDb: () -> Float,
    parametricBands: () -> List<ParametricBand>,
    actions: EqualizerActions,
    modifier: Modifier = Modifier,
    preEqResponseMode: () -> PreEqResponseMode = { preEqResponseModeOf(settings().preEqResponseMode) },
    // Rarely used extras (diagnostics, debug) at the end of the Device tab, out of the way.
    deviceTabFooter: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val spacing = MaterialTheme.spacing
    val style = SonicStyle
    // Opened from "My headphones" or from a suggestion ("Other model"); holds the starting query.
    var headphoneSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val onSearchHeadphones = actions.onSearchHeadphones
    if (onSearchHeadphones != null) {
        headphoneSearchQuery?.let { initial ->
            HeadphoneCatalogDialog(
                initialQuery = initial,
                onSearch = onSearchHeadphones,
                onChosen = {
                    headphoneSearchQuery = null
                    actions.onHeadphoneChosen(it)
                },
                onDismiss = { headphoneSearchQuery = null },
                style = style,
            )
        }
    }
    val openSearch: ((String) -> Unit)? = if (onSearchHeadphones != null) ({ headphoneSearchQuery = it }) else null

    // What the screen derives before it draws: the combined curve, headroom and graph bands. Built
    // in a derived state from the live settings and volume, so only its readers recompose, and
    // only when the result differs; a change of the grouped inputs builds a new one.
    val curveModelState =
        remember(device, presets.activePreset, correction, shaping.activeGoal, shaping.activeContext, settings, currentLevelDb) {
            derivedStateOf {
                EqualizerCurveModel.of(
                    settings = settings(),
                    bands = device.bands,
                    activePreset = presets.activePreset,
                    activeCorrectionProfile = correction.activeProfile,
                    activeGoal = shaping.activeGoal,
                    activeContext = shaping.activeContext,
                    headphonePower = correction.headphonePower,
                    effectiveHeadphoneAcoustics = correction.effectiveHeadphoneAcoustics,
                    currentLevelDb = currentLevelDb(),
                    isOwnPlayerSession = device.isOwnPlayerSession,
                    preEqActive = device.preEqActive,
                )
            }
        }
    val curveModel = { curveModelState.value }

    var selectedBand by rememberSaveable { mutableStateOf<Int?>(null) }
    var tab by rememberSaveable { mutableStateOf(EqualizerTab.SOUND) }
    var signalChainOpen by rememberSaveable { mutableStateOf(false) }
    var noSessionDetails by rememberSaveable { mutableStateOf(false) }
    // The curve folds while the options below are scrolled down and unfolds at their top (or on a
    // tap), so the options get most of a phone screen without losing sight of the curve.
    var curveCollapsed by rememberSaveable { mutableStateOf(false) }
    val collapseOnScroll =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y < -COLLAPSE_THRESHOLD_PX && source == NestedScrollSource.UserInput) curveCollapsed = true
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // Only what the options could not take: they are at their top and the pull goes on.
                    if (available.y > COLLAPSE_THRESHOLD_PX) curveCollapsed = false
                    return Offset.Zero
                }
            }
        }

    @Composable
    fun HeadAndNotice(
        graphHeight: Dp,
        collapsible: Boolean,
    ) {
        EqualizerHead(
            route = device.route,
            activePreset = presets.activePreset,
            isDirty = presets.isDirty,
            engineState = device.engineState,
            pathLabel = pathLabel,
            settings = settings,
            curveModel = curveModel,
            selectedBand = selectedBand,
            onSelectBand = { selectedBand = it },
            graphHeight = graphHeight,
            collapsed = collapsible && curveCollapsed,
            onExpand = { curveCollapsed = false },
            noSessionDetails = noSessionDetails,
            onToggleNoSessionDetails = { noSessionDetails = !noSessionDetails },
            onOpenSignalChain = { signalChainOpen = true },
            actions = actions,
            style = style,
        )
        NoticeSlot(
            engineState = device.engineState,
            deviceKindPrompt = device.deviceKindPrompt,
            suggestedCorrectionProfile = correction.suggested,
            routeName = device.route.name,
            onSearchModel = openSearch,
            actions = actions,
            style = style,
        )
    }

    @Composable
    fun TabContent(modifier: Modifier) {
        val scroll = key(tab) { rememberScrollState() }
        Column(
            modifier =
                modifier
                    .nestedScroll(collapseOnScroll)
                    .verticalScroll(scroll)
                    .padding(horizontal = spacing.medium)
                    .padding(top = spacing.extraSmall, bottom = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                EqualizerTab.SOUND -> {
                    GenreSection(
                        activePreset = presets.activePreset,
                        customPresets = presets.customPresets,
                        genrePresets = genrePresets,
                        moreIntensity = presets.moreIntensity,
                        actions = actions,
                        style = style,
                    )
                    ModulesSection(
                        settings = settings,
                        preEqActive = device.preEqActive,
                        isOwnPlayerSession = device.isOwnPlayerSession,
                        actions = actions,
                    )
                    SoundGoalCard(active = shaping.activeGoal, onSelect = actions.onGoalSelected)
                    CompareSection(compare = shaping.compare, actions = actions, style = style)
                }
                EqualizerTab.FINE_TUNE -> {
                    FineTuneSection(
                        settings = settings,
                        curveModel = curveModel,
                        bandCount = device.bands.size,
                        selectedBand = selectedBand,
                        onSelectBand = { selectedBand = it },
                        actions = actions,
                        style = style,
                    )
                    ParametricSection(bands = parametricBands, actions = actions, style = style)
                    HighResolutionSection(
                        settings = settings,
                        responseMode = preEqResponseMode,
                        preEqActive = device.preEqActive,
                        actions = actions,
                    )
                    Text(
                        text = stringResource(R.string.graph_note_interpolated),
                        style = MaterialTheme.typography.bodySmall,
                        color = style.mutedText,
                    )
                }
                EqualizerTab.DEVICE -> {
                    SessionsSection(
                        audioSessions = device.audioSessions,
                        activeAudioSession = device.activeAudioSession,
                        appProfileBound = device.appProfileBound,
                        actions = actions,
                        style = style,
                    )
                    ProfilesSection(
                        route = device.route,
                        activePresetName = presets.activePreset.name,
                        activeCorrectionProfile = correction.activeProfile,
                        onRenameRoute = actions.onRenameRoute,
                        style = style,
                    )
                    CorrectionSection(
                        allProfiles = correction.allProfiles,
                        activeProfile = correction.activeProfile,
                        effectiveHeadphoneAcoustics = correction.effectiveHeadphoneAcoustics,
                        onFindModel = openSearch?.let { search -> { search("") } },
                        actions = actions,
                        style = style,
                    )
                    if (correction.effectiveHeadphoneAcoustics) {
                        HeadphonePowerSection(
                            power = correction.headphonePower,
                            onPowerChanged = actions.onHeadphonePowerChanged,
                            style = style,
                        )
                    }
                    ContextSection(
                        route = device.route,
                        activeContext = shaping.activeContext,
                        onContextModeChanged = actions.onContextModeChanged,
                        style = style,
                    )
                    deviceTabFooter?.invoke(this)
                }
            }
        }
    }

    CompositionLocalProvider(LocalContentColor provides style.text) {
        BoxWithConstraints(modifier = modifier.fillMaxSize().background(style.background)) {
            // Short screens get a smaller curve; wide ones (landscape) put the head beside the tabs.
            val wide = maxWidth > maxHeight && maxWidth >= WIDE_LAYOUT_MIN_WIDTH
            val compact = maxHeight < COMPACT_HEIGHT
            val graphHeight = if (compact) CompactGraphHeight else DefaultGraphHeight
            // Folding only helps where the head sits above the options.
            val collapsible = !wide
            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        HeadAndNotice(graphHeight, collapsible)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        EqualizerTabs(selected = tab, onSelect = { tab = it })
                        TabContent(Modifier.weight(1f))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.medium).padding(top = spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        HeadAndNotice(graphHeight, collapsible)
                    }
                    EqualizerTabs(selected = tab, onSelect = { tab = it })
                    TabContent(Modifier.weight(1f))
                }
            }
        }
        if (signalChainOpen) {
            SignalChainSheet(onDismiss = { signalChainOpen = false }) {
                SignalChainSection(engineState = device.engineState, settings = settings, curveModel = curveModel, style = style)
            }
        }
    }
}

// A response mode saved by an older version that no longer exists falls back to the default.
internal fun preEqResponseModeOf(name: String): PreEqResponseMode =
    try {
        PreEqResponseMode.valueOf(name)
    } catch (e: IllegalArgumentException) {
        PreEqResponseMode.DEFAULT
    }

// Finger movement (px per event) that counts as scrolling the options, not as jitter.
private const val COLLAPSE_THRESHOLD_PX = 1f
