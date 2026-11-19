# AuraCurve

**Feel the bass. Rule the sound.**  
*Music player, bass enhancement, advanced EQ.*

An Android equalizer built for powerful, controlled bass: an interactive frequency-response curve, sound profiles, device-specific presets and a built-in player for audio files on the device.

This repository is the store build of AuraCurve (application ID `com.auracurve.app`, intended for Google Play). It contains no streaming service, no network access and no self-update (see `docs/DECISIONS.md`).

## Features

- **Equalizer:** interactive frequency-response curve with draggable points, presets, sound goals, AutoEq corrections for about 6,000 headphones, bundled offline: suggested when a headphone connects, then loaded automatically for that device.
- **Processing for music apps:** attaches Android audio effects (`Equalizer`, `DynamicsProcessing`) to audio sessions that compatible apps report. Whether this works depends on the app, the Android version and the device manufacturer.
- **Context profiles:** detects the connected output (car, speaker, headphones) and selects a matching profile.
- **Built-in player:** Media3/ExoPlayer for files on the device, with its own real-time processing chain (curve EQ, subsonic filter, virtual bass, limiter), background playback, media controls and Android Auto.
- **Background service:** waits for audio sessions and stops itself after ten minutes without one.

## Project Structure

| Module | Description |
|---|---|
| `app` | Android application (Kotlin, Jetpack Compose, Hilt, Room): UI, audio engine, services. |
| `core` | Platform-independent Kotlin: presets, curve math, DSP, JSON serialization. |
| `player` | Player library based on AndroidX Media3 / ExoPlayer, including Android Auto. |

## Device Requirements

Android 9 (API 28) or newer, because the processing uses `DynamicsProcessing`. A working `DynamicsProcessing` and `Equalizer` effect in the device's audio framework is required.

## Building

Requirements: JDK 21, Android SDK (path in `local.properties`), Android Studio recommended.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew testDebugUnitTest :core:test
```

```bash
./gradlew ktlintCheck lintDebug
```

Check that the APK contains no online-access code and no internet permission (CI runs this too):

```bash
python3 scripts/check_store_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Release signing reads `hardbasseq.releaseStoreFile`, `hardbasseq.releaseStorePassword`, `hardbasseq.releaseKeyAlias` and `hardbasseq.releaseKeyPassword` as Gradle properties (see `app/build.gradle.kts`).

## Documentation

| File | Description |
|---|---|
| `docs/ARCHITECTURE.md` | Audio engine and service architecture. |
| `docs/adr/` | Architecture decision records (Kotlin/KSP/Hilt build setup). |
| `docs/DECISIONS.md` | Decisions, including why the store build has no streaming service. |
| `docs/STORE_LISTING.md` | Draft of the Play Store listing. |
| `docs/PRIVACY_POLICY_DRAFT.md` | Draft privacy policy (not legally reviewed). |
| `docs/licenses/Barlow-OFL.txt` | License of the bundled Barlow font. |
| `docs/licenses/AutoEq-MIT.txt` | License of the bundled AutoEq headphone correction data (also shipped in the app next to the data). |

## Privacy

Audio is processed on the device. The app has no internet permission, no server of its own and contains no analytics, advertising or crash-reporting library.

## License

See [`LICENSE`](LICENSE).
