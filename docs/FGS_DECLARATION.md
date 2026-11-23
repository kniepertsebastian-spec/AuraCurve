# Foreground-service declaration for Google Play

Play Console › App content › Foreground service permissions. The store build declares two services
with `foregroundServiceType="mediaPlayback"`.

| Service | Plays audio itself | Declaration |
|---|---|---|
| `AudioPlayerService` (`:player`) | yes | Ordinary media playback: a media-library session with notification, lock screen, Bluetooth and Android Auto. |
| `AudioSessionForegroundService` (`:app`) | no | Needs the explanation below; it is the likelier review question. |

## Decision (roadmap 02, step 2)

**Option B: keep the service, declare it and prove the user benefit.** Option A (tie the listener to
`AudioPlayerService`) would remove the product's central feature: the equalizer for *other* apps'
audio. Those apps announce their session by broadcast (`OPEN_AUDIO_EFFECT_CONTROL_SESSION`) once,
when playback starts. Nothing is attached if no process is listening at that moment, and the
player service is not running while the user listens to another app.

What keeps the service honest (already in the code):

- It stops itself after ten minutes without an audio session while the app is not on screen
  (`SessionIdleShutdown`); it counts as busy while any app is playing (`AudioPlaybackMonitor`).
- `START_NOT_STICKY`: the system never recreates it in the background.
- It shows an ongoing, low-importance notification that says what it does and when it stops.
- It starts from `Application.onCreate` / `MainActivity` only, and a rejected background start is
  caught (`ForegroundServiceStartNotAllowedException`) and ends the service cleanly.

## Text for the Play Console form

> AuraCurve is a system-wide equalizer. While the user listens to music in another app, the service
> receives that app's audio-session broadcast and attaches the user's equalizer to that session. If it
> were not in the foreground, the broadcast would be missed and the sound would be unprocessed. The
> service processes the audio the user is listening to (media playback); it ends itself after ten
> minutes without an audio session, and shows a persistent notification while it runs. The user can
> turn the equalizer off at any time.

## Video (30–45 s), to be recorded on a device with the release build

1. Start music in a different app (not AuraCurve), equalizer off: flat sound.
2. Open AuraCurve, switch the equalizer on, drag a band: the sound changes in the other app.
3. Pull down the notification shade: the AuraCurve notification is visible.
4. Close AuraCurve, music keeps playing with the equalizer applied.
5. Stop the music, wait: the service notification disappears after the idle timeout (shortened build
   or cut in the video).

## Fallback if Play rejects `mediaPlayback` for this service

Ready as a branch: **`claude/pensive-keller-hkwhga-tr1-fgs-special-use`** (draft pull request, do not
merge unless Play rejects `mediaPlayback`). It changes the type of `AudioSessionForegroundService` to
`specialUse` with the permission `FOREGROUND_SERVICE_SPECIAL_USE`, the property
`android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` ("Attaches the user's equalizer to the audio session
of the app that is playing") and `promoteToForegroundSafely` to match; the player service keeps
`mediaPlayback`. Use the same text and video in the declaration. `specialUse` is reviewed by hand,
so allow extra review time.

## Verify before the production release

Upload the release bundle to a **closed testing** track early (Play Console › Testing › Closed
testing) and fill in the foreground-service declaration there. The review of that upload is the
real answer to whether `mediaPlayback` is accepted for a service that plays nothing itself; switch
to the fallback branch above if it is rejected.
