package com.auracurve.app.autoeq

/**
 * Matches a detected Bluetooth/USB device name (AudioRoute.name in :app, read from
 * AudioDeviceInfo.productName) against [BuiltInAutoEqCatalog] to suggest a correction profile, and
 * searches the catalogue by name for the manual picker.
 *
 * Only ever a *suggestion* the user confirms once: wired headphones report no product name at all,
 * and a Bluetooth name can be renamed or abbreviated. With thousands of models in the catalogue,
 * "no suggestion" is always preferred over a wrong one, so the rules are deliberately strict:
 *
 * - Names are compared as tokens ("WH-1000XM4" -> wh 1000 xm 4), so spacing, hyphens and case do
 *   not matter ("WH1000XM4", "wh 1000 xm4").
 * - A BLE "LE_" prefix, a hex id ("Galaxy Buds2 Pro (1E3F)", "Buds 2A4F"), generic trailing
 *   words ("Wireless", "BT", "Stereo") and AutoEq's measurement variants ("(ANC on)") are ignored;
 *   "QC" reads as "QuietComfort".
 * - The model must be at the *end* of the device name ("Basti's WH-1000XM4" works), or the device
 *   name the end of the model name (the brand left out: "WH-1000XM4" for "Sony WH-1000XM4").
 *   Extra words after the model are a different model ("Galaxy Buds Core" is not "Galaxy Buds").
 * - Numbers, Roman ones included, must agree exactly: "WH-1000XM5" never suggests the XM4.
 * - A name that fits several different models ("Buds Pro") suggests none.
 */
object AutoEqCatalogMatcher {
    private const val EXACT = 1.0
    private const val SUFFIX_BASE = 0.8
    private const val FUZZY_BASE = 0.6
    private const val MAX_FUZZY_DISTANCE_RATIO = 0.15
    private const val MIN_FUZZY_LENGTH = 6
    private const val VARIANT_PENALTY = 0.005
    private const val TIE = 0.02
    private const val MAX_PREFIX_TOKENS = 3

    // Roman model numbers count as numbers: "Major IV" is not "Major II".
    private val ROMAN = setOf("ii", "iii", "iv", "vi", "vii", "viii", "ix")

    // Abbreviations devices advertise for names AutoEq spells out ("Bose QC45").
    private val SYNONYMS = mapOf("qc" to "quietcomfort")

    private val GENERIC_TRAILING = setOf("wireless", "true", "bluetooth", "bt", "tws", "stereo", "le", "anc")
    private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")
    private val TOKEN = Regex("""\p{L}+|\p{N}+""")
    private val PARENTHESIZED = Regex("""\([^)]*\)|\[[^]]*]""")
    private val PARENTHESIZED_HEX_ID = Regex("""[\s_-]*\((?=[0-9a-f]*\d)[0-9a-f]{2,6}\)""", RegexOption.IGNORE_CASE)
    private val LE_PREFIX = Regex("""^\s*le[\s_-]+""", RegexOption.IGNORE_CASE)
    private val TRAILING_HEX_ID = Regex("""[\s_-]+(?=[0-9a-f]*\d)(?=[0-9a-f]*[a-f])[0-9a-f]{4}\s*$""", RegexOption.IGNORE_CASE)

    // Sources AutoEq itself trusts most, for the rare duplicate under two spellings.
    private val SOURCE_RANK = listOf("oratory1990", "crinacle", "Rtings", "Innerfidelity", "HypetheSonics")

    fun findBestMatch(
        deviceName: String,
        catalog: List<AutoEqCatalogEntry> = BuiltInAutoEqCatalog.entries,
    ): AutoEqCatalogEntry? {
        val device = deviceTokens(deviceName)
        if (device.isEmpty()) return null
        val scored =
            indexFor(catalog).candidates.mapNotNull { candidate ->
                val match = candidate.aliases.mapNotNull { score(device, it) }.maxByOrNull { it.score } ?: return@mapNotNull null
                Scored(candidate, match.score - if (candidate.variant) VARIANT_PENALTY else 0.0, match.deviceIsSuffix)
            }
        if (scored.isEmpty()) return null
        val best =
            scored.maxWithOrNull(
                compareBy<Scored> { it.score }
                    .thenByDescending { sourceRank(it.candidate.entry.source) }
                    .thenByDescending { it.candidate.order },
            ) ?: return null

        // A bare model name that ends several different models ("Buds Pro" for Samsung, OnePlus, ...)
        // or two different models scoring alike: ask nobody rather than guess.
        val rivals =
            scored.filter {
                it.candidate.base != best.candidate.base &&
                    (it.score >= best.score - TIE || (best.deviceIsSuffix && it.deviceIsSuffix))
            }
        return if (rivals.isEmpty()) best.candidate.entry else null
    }

    /**
     * Catalogue entries matching every word of [query]: a word matches the start of a word of the
     * name ("hd 6" finds "HD 600"), or, from three letters on, any part of one ("xm4" finds
     * "WH-1000XM4"). Names starting with the query come first, measurement variants last.
     */
    fun search(
        query: String,
        catalog: List<AutoEqCatalogEntry> = BuiltInAutoEqCatalog.entries,
        limit: Int = 100,
    ): List<AutoEqCatalogEntry> {
        val words = query.split(Regex("""\s+""")).map(::squash).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val joined = words.joinToString("")
        return indexFor(catalog)
            .candidates
            .asSequence()
            .filter { candidate ->
                (words.size == 1 && candidate.squashed.contains(joined)) ||
                    words.all { word -> candidate.words.any { it.startsWith(word) || (word.length >= 3 && it.contains(word)) } }
            }.sortedWith(
                compareBy<Candidate> { !it.squashed.startsWith(joined) && !it.squashedWithoutBrand.startsWith(joined) }
                    .thenBy { it.variant }
                    .thenBy { it.entry.displayName.lowercase() },
            ).take(limit)
            .map { it.entry }
            .toList()
    }

    private class Candidate(
        val entry: AutoEqCatalogEntry,
        val order: Int,
        val aliases: List<List<String>>,
        val variant: Boolean,
        // The model without its variant in parentheses: "Sony WH-1000XM4 (ANC on)" and
        // "Sony WH-1000XM4" are one model, never rivals of each other.
        val base: String,
        val squashed: String,
        val squashedWithoutBrand: String,
        // The name split at spaces and punctuation, for the search: "wh", "1000xm4".
        val words: List<String>,
    )

    private class Index(
        val catalog: List<AutoEqCatalogEntry>,
        val candidates: List<Candidate>,
    )

    private class Match(
        val score: Double,
        val deviceIsSuffix: Boolean,
    )

    private class Scored(
        val candidate: Candidate,
        val score: Double,
        val deviceIsSuffix: Boolean,
    )

    @Volatile
    private var cachedIndex: Index? = null

    private fun indexFor(catalog: List<AutoEqCatalogEntry>): Index {
        cachedIndex?.takeIf { it.catalog === catalog }?.let { return it }
        val index =
            Index(
                catalog,
                catalog.mapIndexed { order, entry ->
                    val names = listOf(entry.displayName) + entry.aliases
                    val aliasTokens =
                        names
                            .flatMap { name ->
                                listOfNotNull(
                                    modelTokens(name),
                                    modelTokens(withoutBrand(name)).takeIf(::distinctive),
                                    // "Nothing ear (2)": here the parentheses are the model, not a variant.
                                    withoutGenericTail(tokens(name)),
                                    withoutGenericTail(tokens(withoutBrand(name))).takeIf(::distinctive),
                                )
                            }.filter { it.isNotEmpty() }
                            .distinct()
                    val withoutVariant = PARENTHESIZED.replace(entry.displayName, " ")
                    Candidate(
                        entry = entry,
                        order = -order,
                        aliases = aliasTokens,
                        variant = withoutVariant.length != entry.displayName.length,
                        base = squash(withoutVariant),
                        squashed = squash(entry.displayName),
                        squashedWithoutBrand = squash(withoutBrand(entry.displayName)),
                        words =
                            entry.displayName
                                .lowercase()
                                .split(NON_ALPHANUMERIC)
                                .filter { it.isNotEmpty() },
                    )
                },
            )
        cachedIndex = index
        return index
    }

    private fun score(
        device: List<String>,
        alias: List<String>,
    ): Match? {
        if (device == alias) return Match(EXACT, deviceIsSuffix = false)
        val deviceChars = device.sumOf { it.length }
        val aliasChars = alias.sumOf { it.length }

        val common = commonSuffix(device, alias)
        if (common == minOf(device.size, alias.size)) {
            val matched = if (device.size < alias.size) device else alias
            val longer = if (device.size < alias.size) alias else device
            if (distinctive(matched) && longer.size - common <= MAX_PREFIX_TOKENS) {
                val coverage = matched.sumOf { it.length }.toDouble() / maxOf(deviceChars, aliasChars)
                return Match(SUFFIX_BASE + 0.15 * coverage, deviceIsSuffix = device.size < alias.size)
            }
            return null
        }

        // Typos only ("Senheiser HD 599"): same words, same numbers, a letter or two off.
        if (device.size != alias.size || numbers(device) != numbers(alias)) return null
        val a = device.joinToString("")
        val b = alias.joinToString("")
        if (maxOf(a.length, b.length) < MIN_FUZZY_LENGTH) return null
        val ratio = levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
        if (ratio > MAX_FUZZY_DISTANCE_RATIO) return null
        return Match(FUZZY_BASE + 0.1 * (1 - ratio), deviceIsSuffix = false)
    }

    // Enough of a name to stand for one model: a number, or two words with some length.
    private fun distinctive(tokens: List<String>): Boolean =
        tokens.isNotEmpty() &&
            tokens.sumOf { it.length } >= 4 &&
            (tokens.any(::isNumber) || (tokens.size >= 2 && tokens.sumOf { it.length } >= 6))

    private fun commonSuffix(
        a: List<String>,
        b: List<String>,
    ): Int {
        var n = 0
        while (n < a.size && n < b.size && a[a.size - 1 - n] == b[b.size - 1 - n]) n++
        return n
    }

    private fun isNumber(token: String) = token[0].isDigit() || token in ROMAN

    private fun numbers(tokens: List<String>) = tokens.filter(::isNumber)

    private fun deviceTokens(name: String): List<String> {
        var cleaned = name.replace("​", "")
        cleaned = PARENTHESIZED_HEX_ID.replace(cleaned, " ")
        cleaned = LE_PREFIX.replace(cleaned, "")
        cleaned = TRAILING_HEX_ID.replace(cleaned, "")
        return withoutGenericTail(tokens(cleaned))
    }

    private fun modelTokens(name: String): List<String> = withoutGenericTail(tokens(PARENTHESIZED.replace(name.replace("​", ""), " ")))

    private fun tokens(text: String): List<String> =
        TOKEN
            .findAll(text.lowercase())
            .map { SYNONYMS[it.value] ?: it.value }
            .toList()

    private fun withoutGenericTail(tokens: List<String>): List<String> {
        var end = tokens.size
        while (end > 1 && tokens[end - 1] in GENERIC_TRAILING) end--
        return tokens.subList(0, end)
    }

    private fun withoutBrand(name: String): String = name.trim().substringAfter(' ', missingDelimiterValue = "")

    private fun squash(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private fun sourceRank(source: String?): Int {
        val index = SOURCE_RANK.indexOf(source)
        return if (index < 0) 0 else SOURCE_RANK.size - index
    }

    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                current[j] =
                    if (a[i - 1] == b[j - 1]) {
                        previous[j - 1]
                    } else {
                        1 + minOf(previous[j], current[j - 1], previous[j - 1])
                    }
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
