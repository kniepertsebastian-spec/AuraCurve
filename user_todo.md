# Offene Aufgaben für dich – Teil-Roadmap 2 (Datenpersistenz, Backup & Widgets)

Der Code-Teil der Roadmap ist umgesetzt (PR auf `claude/trusting-mccarthy-jxiai5`).
Diese Punkte brauchen ein echtes Gerät (oder zwei) und bleiben deshalb bei dir.

> Hinweis zum Mergen: Teil-Roadmap 1 legt ebenfalls eine `user_todo.md` an. Beim Zusammenführen
> beide Abschnitte untereinander behalten.

## 1. CI prüfen

- [ ] In der Cloud-Session gibt es kein Android SDK (`dl.google.com` ist gesperrt), der Code wurde
      daher hier nicht kompiliert; nur ktlint lief lokal. Bitte den CI-Lauf des PRs prüfen
      (ktlint, Build, Unit-Tests inkl. `QuickWidgetProviderTest`, `PersistedStateTest`,
      `DeviceAliasStoreTest`, `SharedPreferencesMigrationTest`, `DataStorePlaylistRepositoryTest`,
      `BackupRulesTest`, `:core:test` mit `UnreadableLocalTracksTest`, Release-Build mit Lint).

## 2. Migration bei Bestandskunden (M-5)

Mit einem Build **vor** diesem PR anfangen, damit die alten `SharedPreferences` existieren:

- [ ] Alten Build installieren; einem Ausgabegerät einen eigenen Namen geben, eine Korrektur-Empfehlung
      mit „Not now“ beantworten, im Player Mono-Bass-Grenzfrequenz/Limiter und die Track-Überblendung ändern.
- [ ] Neuen Build drüber installieren (`adb install -r`), App starten: Name, „Not now“, Mono-Bass/Limiter
      und Überblendung sind unverändert da.
- [ ] Optional: `adb shell run-as com.auracurve.app ls shared_prefs files/datastore` – die vier alten
      XML-Dateien (`device_aliases`, `correction_suggestion_dismissals`, `player_dsp`, `playback_fade`)
      sind weg, dafür gibt es die gleichnamigen `*.preferences_pb`.

## 3. StrictMode beim App-Start (Definition of Done)

- [ ] Debug-Build (StrictMode aktiv) kalt starten und mitloggen:
      `adb logcat | grep -E "StrictMode|DiskReadViolation"` – keine Meldungen mehr aus `SharedPreferences`
      bzw. aus `DeviceAliasStore`, `CorrectionSuggestionDismissals`, `PlayerDspState`, `PlaybackFadeState`.
      (Meldungen aus anderen Stellen, z. B. Room oder Coil, gehören nicht zu diesem Track.)

## 4. Backup & Restore mit ungültigen SAF-URIs (M-4)

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

## 5. Widget (M-2)

- [ ] Widget auf den Homescreen legen, App per `adb shell am force-stop com.auracurve.app` beenden, dann
      ein Widget-Update auslösen (z. B. Widget neu platzieren): Widget zeigt Status/Preset, kein Absturz
      in `adb logcat`.
- [ ] Optional: Bei einem Fehler im Update erscheint nur `W QuickWidgetProvider: Widget not updated`,
      der Prozess läuft weiter.

## 6. Datenschutzerklärung

- [ ] `docs/PRIVACY_POLICY_DRAFT.md` (Abschnitt 2) wurde um die Backup-Ausnahmen ergänzt – bitte die
      veröffentlichte Fassung (Store-Eintrag/Website) entsprechend aktualisieren.
