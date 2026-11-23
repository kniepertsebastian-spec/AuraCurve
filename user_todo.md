# Offene Aufgaben für dich – Teil-Roadmap 1 (Audio-Engine, Services & Plattform)

Der Code-Teil der Roadmap ist umgesetzt (PR auf `claude/pensive-keller-hkwhga-tr1-audio-engine`).
Diese Punkte brauchen die Play Console oder ein echtes Gerät und bleiben deshalb bei dir.

## 1. Play Console: FGS-Deklaration verifizieren (M-1)

- [ ] Release-Bundle bauen (`./gradlew :app:bundleRelease`, mit Upload-Key signiert) und in einen
      **Closed-Testing-Track** hochladen (Play Console › Testen › Geschlossener Test).
- [ ] Unter *App-Inhalte › Berechtigungen für Dienste im Vordergrund* beide Dienste mit
      `mediaPlayback` deklarieren. Text und Video-Ablauf stehen in
      [`docs/FGS_DECLARATION.md`](docs/FGS_DECLARATION.md); Video (30–45 s) mit dem Release-Build aufnehmen.
- [ ] Ergebnis des Reviews abwarten:
  - **Akzeptiert:** Fallback-PR (`claude/pensive-keller-hkwhga-tr1-fgs-special-use`) schließen.
  - **Abgelehnt:** Fallback-PR mergen, neu hochladen, Deklaration auf `specialUse` umstellen
    (gleicher Text und gleiches Video; manuelles Review dauert länger).

## 2. Abnahme auf echter Hardware (Definition of Done)

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

## 3. CI prüfen

- [ ] In der Cloud-Session gibt es kein Android SDK (und `dl.google.com` ist gesperrt), der Code wurde
      daher hier nicht kompiliert. Bitte den CI-Lauf des PRs prüfen (ktlint, Build, Unit-Tests inkl.
      `SessionIdleShutdownTest` und dem neuen `MediaPlaybackActiveTest`, Lint).
