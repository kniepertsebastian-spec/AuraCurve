# Decisions

## No streaming service in the store build

**Date:** 19 Nov 2026 (replaces the entry of 16 Nov 2026 on a planned SoundCloud integration)

The Google Play build (`com.auracurve.app`) ships without any streaming service: no SoundCloud,
no Spotify, no link import, no online catalogs and no self-update. It is an equalizer and sound
chain for the user's music apps plus a player for audio files on the device.

**Removed:** the seams that were kept for a later integration (`OnlineSources`, `LinkImport`,
`DiscoverySource` and the weekly "new uploads" worker, the "Linked services" screen, search, the
account library, sending playlists to an account, the Likes and new-uploads folders in Android
Auto, the Spotify equalizer hint) and the `INTERNET` permission. Coil's network module, OkHttp and
WorkManager are no longer dependencies.

**Kept, generic:** the player's `StreamResolver` seam and network-retry handling in
`AudioPlayerService`. In this build every track is a file on the device, so neither is reached;
removing them is a separate, tested step.

**Guard:** `scripts/check_store_apk.py` runs in CI and fails if the APK declares the `INTERNET`
permission or contains SoundCloud/Spotify addresses, Spotify/YouTube link resolution, a
self-update or online catalogs.

**If a streaming service is ever added:** only through the provider's official API and with its
written approval, recorded here (date, sender, approved scope, conditions). The privacy policy and
the Play Console data-safety answers must be updated before that version is released.
