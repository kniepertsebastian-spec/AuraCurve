# Architecture

An overview of how the code is organized today. Details live in the code; this file describes the
structure and the rules that hold across it.

## Modules

| Module | Kind | Contents |
|---|---|---|
| `:app` | Android application | UI (Jetpack Compose), audio engine, services, persistence, DI. |
| `:core` | Plain Kotlin/JVM | Presets, curve math, DSP (biquads, limiter, bass exciter, crossfeed, ...), AutoEQ parsing, JSON serialization. No Android dependency, so it is tested with plain JUnit. |
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
  diagnostics/  # In-memory diagnostics recorder and report
  discovery/    # Discovery seam (no source in this build, see DistributionModule)
  integration/  # Bridge to the built-in player, local track reader, online-source seam
  playlist/     # Saved playlists
  preset/       # Preset repository
  profile/      # Device and app profiles
  quick/        # Quick settings tiles and home-screen widget
  service/      # AudioSessionForegroundService and its idle shutdown
  settings/     # DataStore settings and the live editing state
  ui/           # Compose screens: equalizer, player, diagnostics, theme, navigation
```

## State and data flow

- `MainViewModel` holds the equalizer screen's state as `StateFlow`s and owns the business logic.
  Composables only read state and forward events.
- Presets, device profiles, correction profiles and app profiles are stored in a Room database
  (`AppDatabase`, schema version 3; exported schemas in `app/schemas/` back the migration tests).
  Settings and playlists use DataStore.
- `AudioSessionForegroundService` watches for audio sessions that other apps report, attaches the
  effects through `AudioEngine` and stops itself after ten minutes without a session.
- `DiagnosticsRecorder` keeps the last 50 session, route and engine events in memory only, for
  the diagnostics report. Nothing is persisted.

## Dependency injection

Hilt: `@HiltAndroidApp` on `AuraCurveApplication`, `@AndroidEntryPoint` on `MainActivity`,
`@HiltViewModel` view models. Modules in `di/`: `AudioModule`, `DatabaseModule`,
`DiagnosticsModule`, `DispatcherModule`, `IntegrationModule`, `PersistenceModule` and
`DistributionModule`, which binds the "no online source" implementations of `OnlineSources`,
`DiscoverySource` and `LinkImport`.

KSP (needed by Hilt and Room) does not work with AGP 9's built-in Kotlin. The workaround
(`android.builtInKotlin=false`, `android.newDsl=false`, the `org.jetbrains.kotlin.android` plugin
applied again) is described in `docs/adr/0001-kotlin-ksp-with-agp9.md`.

## Navigation

`AppNavHost` with the routes home (equalizer), player, linked services and diagnostics.

## Threading rules

- No `AudioEffect` calls directly from composables.
- Every effect object has exactly one owner in the audio layer.
- Creating, applying and releasing effects is serialized with a `Mutex` (see `AndroidAudioEngine`).
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
