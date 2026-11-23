# Offene Aufgaben für dich

## Teil-Roadmap 1 (Audio-Engine, Services & Plattform)

Der Code-Teil der Roadmap ist umgesetzt (PR auf `claude/pensive-keller-hkwhga-tr1-audio-engine`).
Diese Punkte brauchen die Play Console oder ein echtes Gerät und bleiben deshalb bei dir.

### 1. Play Console: FGS-Deklaration verifizieren (M-1)

- [ ] Release-Bundle bauen (`./gradlew :app:bundleRelease`, mit Upload-Key signiert) und in einen
      **Closed-Testing-Track** hochladen (Play Console › Testen › Geschlossener Test).
- [ ] Unter *App-Inhalte › Berechtigungen für Dienste im Vordergrund* beide Dienste mit
      `mediaPlayback` deklarieren. Text und Video-Ablauf stehen in
      [`docs/FGS_DECLARATION.md`](docs/FGS_DECLARATION.md); Video (30–45 s) mit dem Release-Build aufnehmen.
- [ ] Ergebnis des Reviews abwarten:
  - **Akzeptiert:** Fallback-PR (`claude/pensive-keller-hkwhga-tr1-fgs-special-use`) schließen.
  - **Abgelehnt:** Fallback-PR mergen, neu hochladen, Deklaration auf `specialUse` umstellen
    (gleicher Text und gleiches Video; manuelles Review dauert länger).

### 2. Abnahme auf echter Hardware (Definition of Done)

Debug-Build (StrictMode ist dort aktiv) auf einem Gerät installieren und `adb logcat` mitlaufen lassen,
z. B. gefiltert mit `adb logcat | grep -E "StrictMode|hiddenapi|Accessing hidden|AndroidAudioEngine|AudioPlaybackMonitor"`.

- [ ] App-Start: keine `StrictMode`-Meldungen aus dem Audio-Code, keine `hiddenapi`/`Accessing hidden method`-Zeilen.
- [ ] Bluetooth-Kopfhörer verbinden/trennen, während Musik in einer anderen App läuft: EQ hängt sich neu an,
      UI ruckelt nicht, keine ANR.
- [ ] 34-Band-Modus („High resolution EQ“) an, Regler schnell hin und her ziehen: Klang folgt flüssig,
      keine wachsende Verzögerung nach dem Loslassen.
- [ ] Musik in einer anderen App starten/pausieren: In *Diagnostics* erscheint
      „System-wide audio playback: started/stopped“ (neue Erkennung ohne Reflection).
- [ ] Lautstärketasten (auch über Bluetooth) bei aktivem Kopfhörermodus/Loudness: Kurve folgt der Lautstärke.
- [ ] Instrumentierten Test ausführen: `./gradlew :app:connectedDebugAndroidTest`
      (enthält `EngineAttachToSessionTest`).

### 3. CI

- [x] CI-Lauf grün (ktlint, Build, Unit-Tests inkl. `SessionIdleShutdownTest` und `MediaPlaybackActiveTest`,
      Lint) auf beiden PRs. Lokal wurde in der Cloud-Session nicht gebaut (kein Android SDK).

---

## Teil-Roadmap 2 (Datenpersistenz, Backup & Widgets)

Der Code-Teil der Roadmap ist umgesetzt (PR auf `claude/trusting-mccarthy-jxiai5`).
Diese Punkte brauchen ein echtes Gerät (oder zwei) und bleiben deshalb bei dir.

### 1. CI

- [x] CI-Lauf grün (ktlint, Build, Unit-Tests inkl. `QuickWidgetProviderTest`, `PersistedStateTest`,
      `DeviceAliasStoreTest`, `SharedPreferencesMigrationTest`, `DataStorePlaylistRepositoryTest`,
      `BackupRulesTest`, `:core:test` mit `UnreadableLocalTracksTest`, Release-Build mit Lint).
      Lokal wurde in der Cloud-Session nicht gebaut (kein Android SDK).

### 2. Migration bei Bestandskunden (M-5)

Mit einem Build **vor** diesem PR anfangen, damit die alten `SharedPreferences` existieren:

- [ ] Alten Build installieren; einem Ausgabegerät einen eigenen Namen geben, eine Korrektur-Empfehlung
      mit „Not now“ beantworten, im Player Mono-Bass-Grenzfrequenz/Limiter und die Track-Überblendung ändern.
- [ ] Neuen Build drüber installieren (`adb install -r`), App starten: Name, „Not now“, Mono-Bass/Limiter
      und Überblendung sind unverändert da.
- [ ] Optional: `adb shell run-as com.auracurve.app ls shared_prefs files/datastore` – die vier alten
      XML-Dateien (`device_aliases`, `correction_suggestion_dismissals`, `player_dsp`, `playback_fade`)
      sind weg, dafür gibt es die gleichnamigen `*.preferences_pb`.

### 3. StrictMode beim App-Start (Definition of Done)

- [ ] Debug-Build (StrictMode aktiv) kalt starten und mitloggen:
      `adb logcat | grep -E "StrictMode|DiskReadViolation"` – keine Meldungen mehr aus `SharedPreferences`
      bzw. aus `DeviceAliasStore`, `CorrectionSuggestionDismissals`, `PlayerDspState`, `PlaybackFadeState`.
      (Meldungen aus anderen Stellen, z. B. Room oder Coil, gehören nicht zu diesem Track.)

### 4. Backup & Restore mit ungültigen SAF-URIs (M-4)

Auf Gerät A eine Playlist mit lokalen Dateien („Play files from this device“ → zu einer Playlist
hinzufügen) und einem eigenen Gerätenamen anlegen. Dann eine der beiden Varianten:

- [ ] **Cloud-Backup:** `adb shell bmgr backupnow com.auracurve.app`, App deinstallieren, neu installieren
      (Restore läuft automatisch) – oder auf ein zweites Gerät mit demselben Google-Konto wiederherstellen.
- [ ] **Geräteübertragung:** neues Gerät per Kabel/WLAN-Übertragung einrichten.

Danach prüfen:

- [ ] Presets, App-Einstellungen, Playlists (Titel) und Mono-Bass/Limiter/Überblendung sind da.
- [ ] Eigene Gerätenamen und „Not now“-Antworten sind **nicht** mitgekommen.
- [ ] Die lokalen Dateien sind aus den Playlists entfernt, im Playlist-Bereich steht einmal der Hinweis
      „… tracks were taken out of your playlists …“; die Playlists selbst sind noch da.
      (Nach Neuinstallation auf demselben Gerät gilt das genauso: die Lesefreigaben gehen bei der
      Deinstallation verloren.)

### 5. Widget (M-2)

- [ ] Widget auf den Homescreen legen, App per `adb shell am force-stop com.auracurve.app` beenden, dann
      ein Widget-Update auslösen (z. B. Widget neu platzieren): Widget zeigt Status/Preset, kein Absturz
      in `adb logcat`.
- [ ] Optional: Bei einem Fehler im Update erscheint nur `W QuickWidgetProvider: Widget not updated`,
      der Prozess läuft weiter.

### 6. Datenschutzerklärung

- [ ] `docs/PRIVACY_POLICY_DRAFT.md` (Abschnitt 2) wurde um die Backup-Ausnahmen ergänzt – bitte die
      veröffentlichte Fassung (Store-Eintrag/Website) entsprechend aktualisieren.

---

## Teil-Roadmap 3 (Architektur, State-Management & Compose-UI)

Der Code-Teil der Roadmap ist umgesetzt (PR auf `claude/trusting-mccarthy-jxiai5`, neu auf `main`
aufgesetzt). In der Cloud-Session wurde diesmal mit Android SDK gebaut: ktlint, `assembleDebug`,
alle Unit-Tests (`:app` inkl. `EqCharacterizationTest`, `MainViewModelTest`, `EqualizerScreenUiTest`,
`PlayerShellUiTest`, neu `EqualizerRecompositionTest` und `MainViewModelScopeTest`; `:player`; `:core`),
Release-Build und Lint liefen lokal. Diese Punkte brauchen ein Gerät oder die Play-/GitHub-Oberfläche.

### 1. CI

- [ ] CI-Lauf auf dem PR grün (ktlint, Build, Unit-Tests, `check_store_apk.py`, Release-Build, Lint).

### 2. Recomposition im Layout Inspector prüfen (Definition of Done)

Debug-Build auf einem Gerät/Emulator starten, in Android Studio *Layout Inspector* öffnen und
*Show Recomposition Counts* einschalten.

- [ ] Im Tab „Fine tune“ einen Band- oder Makro-Regler ziehen: `MainScreen` und `EqualizerContent`
      zählen nicht mit; hoch zählen nur `HeadGraph`/`ResponseGraph`, `FineTuneSection` und die Slider.
- [ ] Lautstärketasten drücken (Kopfhörermodus an): `MainScreen` zählt nicht mit; höchstens die
      Kurve (`HeadGraph`) zählt hoch.
- [ ] Tab „Sound“: Regler im Tab „Fine tune“ ändern, zurückwechseln – `GenreSection` und
      `ModulesSection` wurden dabei nicht neu zusammengesetzt (Zähler unverändert).

### 3. Kurzer Funktionstest nach dem Umbau

Das Verhalten soll unverändert sein (die Golden-Tests decken die Logik ab); auf dem Gerät einmal kurz:

- [ ] Preset wählen, Regler ändern, „Save“ (als neues Preset), umbenennen, löschen; App neu starten –
      zuletzt aktiver Klang ist wieder da.
- [ ] Bluetooth-Kopfhörer verbinden/trennen: gerätespezifischer Klang und Korrektur werden übernommen.
- [ ] Original/EQ-Vergleich (Tab „Sound“) und „Hold to compare“ funktionieren.
- [ ] Android Auto (z. B. mit der Desktop Head Unit oder im Auto): Playlists lassen sich durchsuchen
      und abspielen. Der Katalog kommt jetzt über `AutoCatalogProvider` (Application) statt über das frühere
      globale `AutoCatalogHolder` – ein leerer Ordner im Auto wäre ein Hinweis auf ein Problem.
- [ ] Optional, Debug-Build: unter „More tools“ › „Show debug effects“ erscheint die Effektliste.

### 4. Hinweis zum Merge

- [ ] Teil-Roadmap 2 ist bereits in `main`; die angekündigte Überschneidung in `AuraCurveApplication.kt`
      ist damit aufgelöst. Falls Teil-Roadmap 1 oder 4 noch offen ist und `AuraCurveApplication.kt`,
      `EqController.kt`, `MainViewModel.kt` oder `EqualizerScreen.kt` ändert, gibt es dort Konflikte:
      diese Dateien wurden hier stark umgebaut (Logik liegt jetzt in den Koordinatoren unter `eq/`
      bzw. den Sektionsdateien unter `ui/equalizer/`, siehe `docs/ARCHITECTURE.md`).
