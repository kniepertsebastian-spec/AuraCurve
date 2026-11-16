package com.auracurve.app.autoeq

// Feature idea from the Nothing/Dirac Opteo chat ("My headphones", item 8): matches a
// detected Bluetooth/USB device name (see AudioRoute.name in :app - AndroidAudioRouteRepository
// already reads device.productName there) against BuiltInAutoEqCatalog to suggest a fitting
// correction profile. Deliberately only a *suggestion*, no automatic import: wired headphones
// almost never report a product name through Android (only the generic type), and even for
// Bluetooth devices with a real name fuzzy matching is never reliable enough for a silent
// automatic import - the caller has to ask for confirmation itself.
object AutoEqCatalogMatcher {
    // Below this confidence no suggestion is made - a wrong suggestion is worse than
    // none at all.
    private const val MIN_CONFIDENCE = 0.6

    fun findBestMatch(
        deviceName: String,
        catalog: List<AutoEqCatalogEntry> = BuiltInAutoEqCatalog.entries,
    ): AutoEqCatalogEntry? {
        if (deviceName.isBlank()) return null
        val normalizedDeviceName = normalize(deviceName)
        if (normalizedDeviceName.isBlank()) return null

        return catalog
            .map { entry -> entry to bestScoreFor(normalizedDeviceName, entry) }
            .filter { (_, score) -> score >= MIN_CONFIDENCE }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun bestScoreFor(
        normalizedDeviceName: String,
        entry: AutoEqCatalogEntry,
    ): Double {
        val candidates = listOf(entry.displayName) + entry.aliases
        return candidates.maxOf { candidate -> similarity(normalizedDeviceName, normalize(candidate)) }
    }

    private fun normalize(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]"), "")

    // Containment first - covers both "the model is a substring of the (often longer or
    // differently formatted) Bluetooth name" and the reverse case. Otherwise falls back to
    // Levenshtein similarity, for spellings that are slightly off (OEM firmware likes to
    // mangle names in its own way).
    private fun similarity(
        a: String,
        b: String,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.9

        val distance = levenshtein(a, b)
        val maxLength = maxOf(a.length, b.length)
        return 1.0 - (distance.toDouble() / maxLength)
    }

    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        val distances = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) distances[i][0] = i
        for (j in 0..b.length) distances[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                distances[i][j] =
                    if (a[i - 1] == b[j - 1]) {
                        distances[i - 1][j - 1]
                    } else {
                        1 + minOf(distances[i - 1][j], distances[i][j - 1], distances[i - 1][j - 1])
                    }
            }
        }
        return distances[a.length][b.length]
    }
}
