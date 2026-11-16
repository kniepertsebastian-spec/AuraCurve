package com.auracurve.app.link

import java.text.Normalizer

// What to look for in search results: an artist (when known) and a title.
data class TrackQuery(
    val artist: String?,
    val title: String,
) {
    val searchText: String get() = listOfNotNull(artist, title).joinToString(" ")

    // For "Artist - Title" style display.
    val label: String get() = if (artist.isNullOrBlank()) title else "$artist - $title"
}

// Ranks search results for a TrackQuery. A streaming service's search is fuzzy and
// returns plenty of loosely related tracks, so the results are scored by how many of
// the query's words they actually contain. Uploader names often differ from the
// artist (labels, promo channels), so the artist may match in the title or the
// uploader.
object TrackMatcher {
    private val stopWords = setOf("the", "a", "an", "and", "of", "feat", "ft", "featuring", "official", "video", "audio", "lyrics")

    // Score at or above which the best result is trusted enough to start playing by
    // itself - only ever with a known artist; title-only queries always ask.
    const val CONFIDENT_SCORE = 0.8

    fun <T> rank(
        query: TrackQuery,
        candidates: List<T>,
        title: (T) -> String,
        uploader: (T) -> String,
    ): List<Pair<T, Double>> =
        candidates
            .map { it to score(query, title(it), uploader(it)) }
            .sortedByDescending { it.second }

    // Whether every word of the artist name appears in the track's title or uploader.
    fun mentionsArtist(
        artist: String,
        title: String,
        uploader: String,
    ): Boolean {
        val wanted = tokens(artist)
        if (wanted.isEmpty()) return false
        return tokens("$title $uploader").containsAll(wanted)
    }

    fun isConfident(
        query: TrackQuery,
        score: Double,
    ): Boolean = !query.artist.isNullOrBlank() && score >= CONFIDENT_SCORE

    fun score(
        query: TrackQuery,
        candidateTitle: String,
        candidateUploader: String,
    ): Double {
        val candidate = tokens("$candidateTitle $candidateUploader")
        val titleTokens = tokens(query.title)
        if (titleTokens.isEmpty() || candidate.isEmpty()) return 0.0

        val titleCoverage = coverage(titleTokens, candidate)
        val artistTokens = tokens(query.artist.orEmpty())
        val artistCoverage = if (artistTokens.isEmpty()) titleCoverage else coverage(artistTokens, candidate)

        // Words the result has that the query does not: a hint it is a different
        // (longer) track, a mix, a compilation.
        val queryTokens = titleTokens + artistTokens
        val extraFraction = (candidate - queryTokens).size.toDouble() / candidate.size
        return (0.65 * titleCoverage + 0.35 * artistCoverage - 0.25 * extraFraction).coerceIn(0.0, 1.0)
    }

    private fun coverage(
        wanted: Set<String>,
        available: Set<String>,
    ): Double = wanted.count { it in available }.toDouble() / wanted.size

    private fun tokens(text: String): Set<String> =
        Normalizer
            .normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in stopWords }
            .toSet()
}
