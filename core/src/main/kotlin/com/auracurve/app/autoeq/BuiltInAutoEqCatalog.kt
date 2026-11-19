package com.auracurve.app.autoeq

import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.dsp.ParametricCurve
import com.auracurve.app.dsp.ParametricFilter
import com.auracurve.app.dsp.ParametricFilterType
import java.io.BufferedReader
import java.util.zip.GZIPInputStream

/**
 * Every headphone AutoEq recommends a correction for (about 6,000 models), bundled with the app so a
 * headphone can be corrected without network access - the store build has no INTERNET permission.
 *
 * Data: the "recommended results" of the AutoEq project (https://github.com/jaakkopasanen/AutoEq,
 * MIT License, Copyright (c) 2018-2022 Jaakko Pasanen), one result per model from the most accurate
 * measurement source AutoEq lists (oratory1990, crinacle, Rtings, ...). Stored as the ten parametric
 * filters of each `ParametricEQ.txt`, which keeps the whole catalogue at about 360 KB compressed.
 * `scripts/build_autoeq_catalog.py` regenerates `autoeq/headphones.tsv.gz` from an AutoEq checkout;
 * the licence text ships next to it as `autoeq/LICENSE.txt`.
 *
 * Reading the file takes a moment, so [entries] is loaded on first access: callers on the main
 * thread should touch it from a background dispatcher.
 */
object BuiltInAutoEqCatalog {
    const val RESOURCE_PATH = "/autoeq/headphones.tsv.gz"

    /** Shown with every correction from this catalogue, together with the measurement source. */
    const val SOURCE_LABEL_PREFIX = "AutoEq"

    val entries: List<AutoEqCatalogEntry> by lazy { load() }

    fun findById(id: String): AutoEqCatalogEntry? = byId[id]

    private val byId: Map<String, AutoEqCatalogEntry> by lazy { entries.associateBy { it.id } }

    private fun load(): List<AutoEqCatalogEntry> {
        val stream =
            BuiltInAutoEqCatalog::class.java.getResourceAsStream(RESOURCE_PATH)
                ?: error("AutoEq catalogue $RESOURCE_PATH is missing from the build")
        return GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).use(::parse)
    }

    /** Parses the catalogue format written by `scripts/build_autoeq_catalog.py`; bad lines are skipped. */
    internal fun parse(reader: BufferedReader): List<AutoEqCatalogEntry> {
        val usedIds = HashSet<String>()
        val result = ArrayList<AutoEqCatalogEntry>(6_100)
        reader.lineSequence().forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val columns = line.split('\t')
            if (columns.size != 4) return@forEach
            val name = columns[0].trim()
            val filters = parseFilters(columns[3]) ?: return@forEach
            if (name.isEmpty()) return@forEach
            val source = columns[2].trim().ifEmpty { null }
            val id = uniqueId(name, usedIds)
            result +=
                AutoEqCatalogEntry(
                    id = id,
                    displayName = name,
                    source = source,
                    form = formOf(columns[1]),
                ) {
                    CorrectionProfile(
                        id = id,
                        name = name,
                        curve = ParametricCurve.toCurve(filters),
                        sourceLabel = sourceLabel(source),
                        builtIn = false,
                        deviceName = name,
                    )
                }
        }
        return result
    }

    fun sourceLabel(source: String?): String = if (source == null) SOURCE_LABEL_PREFIX else "$SOURCE_LABEL_PREFIX · $source"

    private fun formOf(code: String): HeadphoneForm? =
        when (code.trim()) {
            "O" -> HeadphoneForm.OVER_EAR
            "I" -> HeadphoneForm.IN_EAR
            "E" -> HeadphoneForm.EARBUD
            else -> null
        }

    // "Sony WH-1000XM4" -> "autoeq_sony_wh_1000xm4". Stable across catalogue updates as long as the
    // model keeps its name, so a route binding saved today still finds the same profile later.
    private fun uniqueId(
        name: String,
        used: MutableSet<String>,
    ): String {
        val base = "autoeq_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        var id = base
        var n = 2
        while (!used.add(id)) id = "${base}_${n++}"
        return id
    }

    private fun parseFilters(column: String): List<ParametricFilter>? {
        val filters =
            column.split(',').mapNotNull { token ->
                val parts = token.trim().split(' ')
                if (parts.size != 3 || parts[0].length < 2) return@mapNotNull null
                val type =
                    when (parts[0][0]) {
                        'P' -> ParametricFilterType.PEAK
                        'L' -> ParametricFilterType.LOW_SHELF
                        'H' -> ParametricFilterType.HIGH_SHELF
                        else -> return@mapNotNull null
                    }
                val frequency = parts[0].substring(1).toFloatOrNull() ?: return@mapNotNull null
                val gain = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val q = parts[2].toFloatOrNull() ?: return@mapNotNull null
                if (frequency <= 0f || q <= 0f) return@mapNotNull null
                ParametricFilter(type = type, frequencyHz = frequency, gainDb = gain, q = q)
            }
        return filters.ifEmpty { null }
    }
}
