package com.auracurve.app.autoeq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class BuiltInAutoEqCatalogTest {
    @Test
    fun theWholeAutoEqCatalogueIsBundled() {
        assertTrue(BuiltInAutoEqCatalog.entries.size > 5_000)
    }

    @Test
    fun everyEntryIdIsUnique() {
        val ids = BuiltInAutoEqCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyEntryKnowsItsSourceAndForm() {
        for (entry in BuiltInAutoEqCatalog.entries) {
            assertNotNull("${entry.id} has no source", entry.source)
            assertNotNull("${entry.id} has no form", entry.form)
        }
    }

    @Test
    fun sampledCurvesAreNonEmptyAndWithinBounds() {
        // Evaluating all ~6,000 curves would take a while; every 25th covers all sources.
        BuiltInAutoEqCatalog.entries.filterIndexed { i, _ -> i % 25 == 0 }.forEach { entry ->
            val curve = entry.profile.curve
            assertTrue("${entry.id} has an empty curve", curve.isNotEmpty())
            for (point in curve) {
                assertTrue("${entry.id} is out of range at ${point.frequencyHz} Hz", point.gainDb in -24f..24f)
            }
        }
    }

    @Test
    fun aCatalogProfileNamesItsHeadphoneAndMeasurementSource() {
        val entry = BuiltInAutoEqCatalog.findById("autoeq_sennheiser_hd_600")
        assertNotNull(entry)
        val profile = entry!!.profile
        assertEquals("Sennheiser HD 600", profile.name)
        assertEquals("Sennheiser HD 600", profile.deviceName)
        assertEquals("AutoEq · oratory1990", profile.sourceLabel)
        assertEquals(HeadphoneForm.OVER_EAR, entry.form)
    }

    @Test
    fun theCurveFollowsTheParametricFilters() {
        // HD 600: low shelf +6.5 dB at 105 Hz, high shelf -3.1 dB at 10 kHz.
        val curve = BuiltInAutoEqCatalog.findById("autoeq_sennheiser_hd_600")!!.profile.curve
        val bass = curve.first { it.frequencyHz >= 30f }.gainDb
        val air = curve.last().gainDb
        assertTrue("bass $bass", bass > 4f)
        assertTrue("air $air", air < 0f)
    }

    @Test
    fun theAutoEqLicenceShipsWithTheData() {
        val licence = BuiltInAutoEqCatalog::class.java.getResourceAsStream("/autoeq/LICENSE.txt")
        assertNotNull(licence)
        val text = licence!!.bufferedReader().use { it.readText() }
        assertTrue(text.contains("MIT License"))
        assertTrue(text.contains("Jaakko Pasanen"))
    }

    @Test
    fun parseSkipsCommentsAndBrokenLines() {
        val text =
            """
            # header
            Good One	O	oratory1990	L105 6.5 .7,P125 -2.7 .55
            Missing Columns	O
            Bad Filters	I	crinacle	X12 1 1
            Good One	I	Rtings	P1000 -3 1.5
            """.trimIndent()
        val entries = BuiltInAutoEqCatalog.parse(BufferedReader(StringReader(text)))
        assertEquals(listOf("autoeq_good_one", "autoeq_good_one_2"), entries.map { it.id })
        assertEquals(HeadphoneForm.IN_EAR, entries[1].form)
        assertNull(entries.find { it.displayName == "Bad Filters" })
    }
}
