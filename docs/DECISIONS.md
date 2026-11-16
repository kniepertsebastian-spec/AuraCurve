# Decisions

## SoundCloud integration (status: waiting for SoundCloud's answer)

**Date:** 16 Nov 2026

AuraCurve ships without any streaming service. SoundCloud is the planned first one, and only
through the official SoundCloud API, once SoundCloud has approved the use case in writing.

**Use case asked for** (written request to SoundCloud):

- Sign-in with the user's own SoundCloud account via OAuth 2.1 with PKCE in the system browser
  (Custom Tab); no client secret in the app.
- Search, the user's likes and playlists, opening SoundCloud links shared into the app.
- Playback in the built-in player with real-time equalizer/DSP processing on the device. No
  downloading, recording or caching beyond what playback needs.
- Optional: saving playlists to the user's account (private), a weekly search for new uploads.
- Background playback, media controls and Android Auto.
- Attribution as SoundCloud specifies; content the user may not stream is shown as unavailable or
  as a preview and never worked around.

**Where it plugs in:** `OnlineSources` (account, search, library, links), `LinkImport` and the
player's `StreamResolver`. This build provides the "not available" versions (`NoOnlineSources`,
`NoLinkImport`, `StreamResolvers`); the screens hide what needs a service via
`BuildConfig.ONLINE_SOURCES`.

**Guard:** `scripts/check_store_apk.py` runs in CI and fails if the APK contains SoundCloud access
outside the official API, Spotify/YouTube link resolution, a self-update or uncleared catalogs.

**Open until SoundCloud answers:** approved scope and conditions, Go+ handling, attribution
requirements, OAuth client model, distribution. Record the answer here (date, sender, approved
scope, conditions, where the original is archived) before any implementation starts. If SoundCloud
declines, no replacement through unofficial endpoints is built.
