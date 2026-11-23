# Store listing · draft

**Status:** 19 Nov 2026, draft. Applies to `com.auracurve.app`: equalizer, sound chain and a player for the user's own files. **No** streaming service, link import, internet access or self-update. The text must not name any service the app does not connect to.

**Brand rules:**
- Name **AuraCurve**, slogan *Feel the bass. Rule the sound.*, footer line *Music player, bass enhancement, advanced EQ.*
- Do not advertise "system-wide": the equalizer attaches to apps that report their audio session. Hence "your music apps", with the note that not every app cooperates.
- No numeric sound-quality claims until they have been measured.

The primary language is English (the app is English-only). A German (de-DE) translation of the listing text follows below; it is store copy for German users, not documentation.

---

## English (en-US) · primary

**App name** (max. 30): `AuraCurve: Bass EQ & Player` (27)

**Short description** (max. 80): `Music player, bass enhancement, advanced EQ.` (44)

Alternative: `Shape your bass on a live EQ curve - for your own music and music apps.` (71)

**Full description** (max. 4000):

```text
Feel the bass. Rule the sound.

AuraCurve puts the equalizer curve at the centre: drag the points, watch the frequency response change, hear it at once. Built for powerful, controlled bass - and for people who want to know exactly what their sound is doing.

THE CURVE
- Interactive frequency-response curve with draggable points
- Free parametric bands with their own frequency, width (Q) and gain
- Presets and your own curves, shareable as a link or QR code
- AutoEq correction profiles for about 6,000 headphones, offline, loaded automatically per device

BASS, CONTROLLED
- Bass enhancement (virtual bass) that adds weight without simply turning up the lows
- Subsonic filter for more headroom
- Limiter against distortion at high gain

FOR EVERY OUTPUT
- Profiles for headphones, speakers and the car
- AuraCurve recognises the connected output and switches to its profile
- A separate sound for each music app that reports its audio session

BUILT-IN PLAYER FOR YOUR OWN MUSIC
- Plays audio files from your device, with the full sound chain
- Your own playlists, queue, shuffle and repeat
- Notification, lock screen, Bluetooth controls and Android Auto

WORKS WITH YOUR MUSIC APPS
AuraCurve attaches its equalizer to apps that report their audio session to Android (many music players do). Some apps and some manufacturers' sound enhancers do not cooperate; the app shows you which sessions it can reach.

PRIVATE BY DESIGN
No account, no ads, no tracking. Your presets, profiles and playlists stay on your phone.

Music player, bass enhancement, advanced EQ.
```

(About 1,500 characters, leaving room for later features.)

---

## German (de-DE) · translated store copy

**App name** (max. 30): `AuraCurve: Bass-EQ & Player` (27)

**Short description** (max. 80): `Musikplayer, Bassverstärkung, präziser Equalizer.` (49)

**Full description:**

```text
Feel the bass. Rule the sound.

Bei AuraCurve steht die Equalizer-Kurve im Mittelpunkt: Punkte ziehen, den Frequenzgang live sehen, sofort hören. Gebaut für kräftigen, kontrollierten Bass - und für alle, die genau wissen wollen, was ihr Klang gerade macht.

DIE KURVE
- Interaktiver Frequenzgang mit verschiebbaren Punkten
- Freie parametrische Bänder mit eigener Frequenz, Breite (Q) und Verstärkung
- Presets und eigene Kurven, teilbar als Link oder QR-Code
- AutoEq-Korrekturprofile für rund 6.000 Kopfhörer, offline und automatisch pro Gerät

BASS UNTER KONTROLLE
- Bassverstärkung (Virtual Bass), die Fülle gibt, ohne nur die Tiefen hochzudrehen
- Subsonic-Filter für mehr Reserve
- Limiter gegen Verzerrungen bei hoher Verstärkung

FÜR JEDEN AUSGANG
- Profile für Kopfhörer, Lautsprecher und Auto
- AuraCurve erkennt den angeschlossenen Ausgang und wechselt in sein Profil
- Eigener Klang für jede Musik-App, die ihre Audio-Sitzung meldet

EINGEBAUTER PLAYER FÜR DEINE MUSIK
- Spielt Audiodateien von deinem Gerät, mit der ganzen Klangkette
- Eigene Playlists, Warteschlange, Zufall und Wiederholen
- Benachrichtigung, Sperrbildschirm, Bluetooth-Tasten und Android Auto

ARBEITET MIT DEINEN MUSIK-APPS
AuraCurve hängt seinen Equalizer an Apps, die ihre Audio-Sitzung an Android melden (das tun viele Musikplayer). Manche Apps und manche Klangverbesserer der Hersteller machen nicht mit; die App zeigt dir, welche Sitzungen sie erreicht.

PRIVAT VON ANFANG AN
Kein Konto, keine Werbung, kein Tracking. Presets, Profile und Playlists bleiben auf deinem Telefon.

Musikplayer, Bassverstärkung, präziser Equalizer.
```

**Note:** The app itself is English-only. Google Play still shows the German listing; nothing in the text promises a German interface.

---

## Graphics

| Asset | Requirement | Status |
|---|---|---|
| Feature graphic | 1024 × 500 px, no border, with "Bass you can see and steer." | open |
| App icon | 512 × 512 px, 32-bit PNG | draft: `docs/store/icon-512.png` |
| Screenshots | at least 2, phone, from this build | open; after the device test |

## Check before submitting

- [ ] Every feature in the text seen on a device in this build (especially "profile per output", "sound per app", Android Auto, AutoEQ import).
- [x] Languages (decision): the app stays English-only (`localeFilters += "en"`, 396 English strings, no `values-de`). Adding `de` would only bundle library translations without translating the app. The German listing and privacy text are store copy for German users and say nothing about a German interface. Revisit when the app itself is translated.
- [ ] Link to the privacy policy (draft: [`PRIVACY_POLICY_DRAFT.md`](PRIVACY_POLICY_DRAFT.md)) publicly reachable.
- [ ] Category *Music & Audio*, content rating and target audience filled in.
- [ ] Upload an Android App Bundle (`./gradlew :app:bundleRelease`), not an APK, signed with the upload key; Play App Signing on.
- [ ] Foreground services: Play Console › App content › Foreground service permissions. The player uses `mediaPlayback` (plays audio); `AudioSessionForegroundService` uses `specialUse` (waits for other apps' audio sessions to attach the equalizer; plays nothing itself). Describe both and add a short video; the wording, the video script and the fallback are in [`FGS_DECLARATION.md`](FGS_DECLARATION.md).
- [ ] Android Auto: the manifest declares a media app for cars. Either opt in under Advanced settings › Form factors and pass the Android Auto review (the car browses the playlists on the device), or remove the `com.google.android.gms.car.application` entry before release.
- [ ] Data safety: "No data collected, no data shared" (see the privacy policy draft); the release APK must not declare `INTERNET` (CI checks the debug APK).
- [ ] Permissions shown in the store match section 4 of the privacy policy (notifications, foreground service for media playback, wake lock; no `INTERNET`, `ACCESS_NETWORK_STATE` or `DUMP`, which `scripts/check_store_apk.py` enforces).
- [ ] Screenshots and the feature graphic show only this build: no streaming-service names or logos.
