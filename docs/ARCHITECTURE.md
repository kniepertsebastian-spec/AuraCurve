# Architecture

An overview of how the code is organized today. Details live in the code; this file describes the
structure and the rules that hold across it.

## Modules

| Module | Kind | Contents |
|---|---|---|
| `:app` | Android application | UI (Jetpack Compose), audio engine, services, persistence, DI. |
| `:core` | Plain Kotlin/JVM | Presets, curve math, DSP (biquads, limiter, bass exciter, crossfeed, ...), AutoEQ parsing and the bundled AutoEq headphone catalogue (`core/src/main/resources/autoeq`, built by `scripts/build_autoeq_catalog.py`), JSON serialization. No Android dependency, so it is tested with plain JUnit. |
| `:player` | Android library | Built-in player based on Media3/ExoPlayer: `AudioPlayerService` (a `MediaLibraryService` for notification, lock screen, Bluetooth and Android Auto) and `PlayerDspAudioProcessor`, the player's own real-time processing chain. |

All packages live under `com.auracurve.app` (the player under `com.auracurve.app.player`).

## Packages in `:app`

```text
com.auracurve.app/
  AuraCurveApplication.kt  # Hilt application, starts the session service
  MainActivity.kt
  audio/        # AudioEngine interface and AndroidAudioEngine, sessions, routes, capabilities
  auto/         # Android Auto catalog and car connection
  correction/   # Headphone correction profiles (AutoEQ)
  data/         # Room database, entities, DAOs, migrations
  di/           # Hilt modules
  diagnostics/  # In-memory diagnostics recorder, report, DiagnosticsCoordinator
  eq/           # EqController and its coordinators (see "Equalizer control" below)
  integration/  # Bridge to the built-in player, local track reader
  playlist/     # Saved playlists
  preset/       # Preset repository
  profile/      # Device and app profiles
  quick/        # Quick settings tiles and home-screen widget
  service/      # AudioSessionForegroundService and its idle shutdown
  settings/     # DataStore settings and the live editing state
  ui/           # Compose screens: equalizer, player, diagnostics, theme, navigation
```

## Equalizer control

The equalizer's state and control logic are process-wide (they also run with no screen open, for
the quick tiles, the widget and route changes). `EqController` (`@Singleton`, in `eq/`) owns the
sound itself and the order in which a change is recalculated, pushed and saved; the rest lives in
focused coordinators it drives, each a `@Singleton` running in the same `@EqControllerScope`:

| Class | Owns |
|---|---|
| `EqController` | Active preset, "more intensity", context mode, sound goal, headphone power, parametric bands, macros and band gains; the reactions to routes, sessions, Android Auto, capabilities and volume; quick-tile commands. |
| `RouteProfileCoordinator` | Active route and its user-given name, audio sessions, per-route binding (preset, correction, headphone mode, context mode), the context-mode decision (`RouteContextResolver`), the "headphones, car or speaker?" question, per-app sound (P5-5). |
| `CorrectionProfileCoordinator` | Built-in and imported correction curves, the active one, "Not now" answers to catalogue suggestions. |
| `PresetPersistenceCoordinator` | Custom presets, the saved live state (`LiveSettings`), and the preset actions that write to the store (save as new, duplicate, rename, delete, import a shared link). |
| `CompareSessionController` | The Original/EQ comparison and its loudness match. |
| `SoundOutputCoordinator` | Hands the finished settings to `AudioEngine` and the built-in player (`PlayerBridge`); decides which of them applies the curve. |

The arithmetic is pure and tested on its own: `CurveCalculator` (combined curve, band mapping,
pre-EQ gains, the player's curve) and `PresetSettingsMapper` (preset to settings, dynamics,
input gain). Coordinators never call back into `EqController`; it calls them and applies what
they decide. `EqCoordinators.create(...)` wires them by hand for the tests.

`@EqControllerScope` is `SupervisorJob + Dispatchers.Main.immediate` with a
`CoroutineExceptionHandler` (`EqControllerModule`): one failing job is logged and noted in the
diagnostics report instead of ending the process, and the other jobs keep running.

## State and data flow

- `MainViewModel` is the equalizer screen's thin layer over `EqController`: it forwards to it and
  keeps what belongs to the screen (dialogs, import preview, catalogue suggestion, source picker,
  confirmations). It takes the controller, the coordinators it needs, `DiagnosticsCoordinator`,
  the engine, the player bridge, texts, a dispatcher and its `SavedStateHandle`.
- The screen's state comes in topic groups (`EqDeviceUiState`, `EqPresetUiState`,
  `EqCorrectionUiState`, `EqShapingUiState`, `MainDialogsUiState`, `DebugToolsUiState`, all
  `@Immutable`, in `ui/main/EqualizerUiState.kt`). What changes many times a second - the
  processing settings while a slider moves, the system volume, a dragged parametric band - is not
  grouped: `MainScreen` collects it without reading it and hands it on as a function, and only the
  composables that draw it (the curve, the sliders, the signal chain) read it. A slider move
  therefore does not recompose `MainScreen` or the sections that do not show it.
- `EqualizerScreen` is split into section composables (`EqualizerHead`, `SoundTabSections`,
  `FineTuneSection`, `DeviceTabSections`, `SignalChainSection`), each taking only the values it
  shows plus one remembered `EqualizerActions`, so unchanged sections are skipped.
  Composables only read state and forward events.
- Presets, device profiles, correction profiles and app profiles are stored in a Room database
  (`AppDatabase`, schema version 3; exported schemas in `app/schemas/` back the migration tests).
  Settings and playlists use DataStore.
- `AudioSessionForegroundService` watches for audio sessions that other apps report, attaches the
  effects through `AudioEngine` and stops itself after ten minutes without a session.
- `DiagnosticsRecorder` keeps the last 50 session, route and engine events in memory only, for
  the diagnostics report. Nothing is persisted.

## Dependency injection

Hilt: `@HiltAndroidApp` on `AuraCurveApplication`, `@AndroidEntryPoint` on `MainActivity` and
`AudioSessionForegroundService`, `@HiltViewModel` view models. Modules in `di/`: `AudioModule`, `AutoModule`,
`DatabaseModule`, `DiagnosticsModule`, `DispatcherModule`, `EqControllerModule`,
`IntegrationModule` and `PersistenceModule`. The store build has no online source and no network
access.

No global service locators: what a class needs is injected. `:player` has no DI framework; it
declares interfaces for what the app provides and asks the `Application` for them (interface
injection, like Coil's `SingletonImageLoader.Factory`): `AudioPlayerService` gets the Android Auto
catalog through `AutoCatalogProvider`, which `AuraCurveApplication` implements with the
`AutoCatalog` Hilt injects (bound to `AppAutoCatalog` in `AutoModule`).

KSP (needed by Hilt and Room) does not work with AGP 9's built-in Kotlin. The workaround
(`android.builtInKotlin=false`, `android.newDsl=false`, the `org.jetbrains.kotlin.android` plugin
applied again) is described in `docs/adr/0001-kotlin-ksp-with-agp9.md`.

## Navigation

`AppNavHost` with the routes home (equalizer), player and diagnostics.

## Threading rules

- No `AudioEffect` calls directly from composables.
- Every effect object has exactly one owner in the audio layer.
- Creating, applying and releasing effects is serialized with a `Mutex` (see `AndroidAudioEngine`).
- Every effect call is a binder transaction and runs on `@AudioIoDispatcher`
  (`Dispatchers.IO.limitedParallelism(1)`), never on the main thread; that includes the device
  query (`AudioEffect.queryEffects`) and the teardown from `markDetached()`. Rapid `apply()` calls
  are coalesced: only the newest settings reach the effects.
- Only public SDK APIs: no reflection on hidden platform methods (non-SDK interfaces are
  restricted and flagged by Play).
- Effects are always released in `finally`.

## Errors and logging

- Error conditions (lost effect control, dead session, unsupported parameters) are modelled as
  state, not thrown to the caller: `AndroidAudioEngine` catches failures and reports a state.
- Log lines in release builds contain **no** media or track names, no Bluetooth device names or
  MAC addresses and no other personal or uniquely identifying data.

## Package name

The application ID and namespace are set once in `gradle.properties`
(`hardbasseq.applicationId`, `hardbasseq.namespace`) and read by `app/build.gradle.kts`. The
display name (`app_name`) is in `app/src/main/res/values/strings.xml`.

## CI

`.github/workflows/ci.yml`: ktlint, `assembleDebug`, unit tests (`testDebugUnitTest`,
`:core:test`), a check that the APK contains no online-access code (`scripts/check_store_apk.py`),
compiling the instrumented tests, the release build and Android Lint.
