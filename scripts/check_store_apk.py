#!/usr/bin/env python3
"""Checks that the store APK contains no online access that is not cleared for this build.

AuraCurve is published without a streaming service until the provider has approved the use case
(SoundCloud only through its official API). This script fails if the DEX files or the binary
manifest of an APK contain markers of access that must not ship: SoundCloud's non-public web API,
web client ids, an embedded web sign-in, Spotify/YouTube link resolution, a self-update or online
catalogs that are not cleared. Not hidden, but absent from the code.

    check_store_apk.py app.apk    # fails if any marker is present
"""

import sys
import zipfile

# Markers in the compiled code (class names and string constants).
DEX_MARKERS = [
    "api-v2.soundcloud.com",  # SoundCloud's non-public web API (only the official API is allowed)
    "client_id=",  # a client id in a request URL
    "SoundCloudLoginActivity",  # sign-in in an embedded WebView instead of the system browser
    "SpotifyLoginActivity",
    "SpotifyApiClient",
    "youtube.com/oembed",  # YouTube link resolution
    "ReleaseUpdateClient",  # self-update from GitHub releases
    "open.spotify.com",  # Spotify link parsing
    "SpotifyImportPlan",  # Spotify playlist import
    "SoundCloudLinkImport",  # link import outside the official API
    "api.audius.co",  # Audius catalog (not cleared)
    "radio-browser.info",  # Radio Browser directory (not cleared)
]

# Markers in AndroidManifest.xml (binary XML stores its strings as UTF-8 or UTF-16).
MANIFEST_MARKERS = [
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "SoundCloudLoginActivity",
    "SpotifyLoginActivity",
    "spotify-callback",
]


def found_markers(apk_path):
    hits = []
    with zipfile.ZipFile(apk_path) as apk:
        dex = b"".join(apk.read(n) for n in apk.namelist() if n.endswith(".dex"))
        manifest = apk.read("AndroidManifest.xml")
    for marker in DEX_MARKERS:
        if marker.encode("utf-8") in dex:
            hits.append(f"dex: {marker}")
    for marker in MANIFEST_MARKERS:
        if marker.encode("utf-8") in manifest or marker.encode("utf-16-le") in manifest:
            hits.append(f"manifest: {marker}")
    return hits


def main(argv):
    paths = [a for a in argv if not a.startswith("--")]
    if len(paths) != 1:
        print(__doc__)
        return 2
    hits = found_markers(paths[0])
    if hits:
        print(f"{paths[0]}: contains online access that is not cleared for this build:")
        print("\n".join(f"  {h}" for h in hits))
        return 1
    print(f"{paths[0]}: no online-access markers")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
