package com.auracurve.app.autoeq

import com.auracurve.app.correction.CorrectionProfile
import com.auracurve.app.preset.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqCatalogMatcherTest {
    private val testCatalog =
        listOf(
            entry("sony_wh1000xm4", "Sony WH-1000XM4", listOf("WH-1000XM4", "WH1000XM4")),
            entry("sennheiser_hd599", "Sennheiser HD 599", listOf("HD 599", "HD599")),
        )

    @Test
    fun exactDisplayNameMatches() {
        val match = AutoEqCatalogMatcher.findBestMatch("Sennheiser HD 599", testCatalog)
        assertEquals("sennheiser_hd599", match?.id)
    }

    @Test
    fun bluetoothAdvertisedAliasMatches() {
        // Many devices advertise just the model code over Bluetooth, not the
        // full "Brand + model" display name.
        val match = AutoEqCatalogMatcher.findBestMatch("WH-1000XM4", testCatalog)
        assertEquals("sony_wh1000xm4", match?.id)
    }

    @Test
    fun caseAndPunctuationInsensitive() {
        val match = AutoEqCatalogMatcher.findBestMatch("wh1000xm4", testCatalog)
        assertEquals("sony_wh1000xm4", match?.id)
    }

    @Test
    fun deviceNameContainingModelStillMatches() {
        // Some stacks report a longer string around the actual model name.
        val match = AutoEqCatalogMatcher.findBestMatch("Sony WH-1000XM4 Stereo", testCatalog)
        assertEquals("sony_wh1000xm4", match?.id)
    }

    @Test
    fun typoStillMatchesViaEditDistance() {
        // Normalization already strips spaces/hyphens, so a genuine edit-
        // distance test needs an actual character difference, not just
        // different spacing - "Senheiser" (missing one 'n') isn't a substring
        // of "Sennheiser" either way, so this only passes via Levenshtein.
        val match = AutoEqCatalogMatcher.findBestMatch("Senheiser HD 599", testCatalog)
        assertEquals("sennheiser_hd599", match?.id)
    }

    @Test
    fun unrelatedNameReturnsNoMatch() {
        val match = AutoEqCatalogMatcher.findBestMatch("Generic Bluetooth Speaker XR200", testCatalog)
        assertNull(match)
    }

    @Test
    fun blankNameReturnsNoMatch() {
        assertNull(AutoEqCatalogMatcher.findBestMatch("", testCatalog))
        assertNull(AutoEqCatalogMatcher.findBestMatch("   ", testCatalog))
    }

    @Test
    fun emptyCatalogReturnsNoMatch() {
        assertNull(AutoEqCatalogMatcher.findBestMatch("Sony WH-1000XM4", emptyList()))
    }

    @Test
    fun picksTheCloserOfTwoCandidatesWhenBothAreSimilar() {
        val ambiguousCatalog =
            listOf(
                entry("model_a", "Foo Headphone A", emptyList()),
                entry("model_b", "Foo Headphone B", emptyList()),
            )
        val match = AutoEqCatalogMatcher.findBestMatch("Foo Headphone A", ambiguousCatalog)
        assertEquals("model_a", match?.id)
    }

    @Test
    fun realCatalogFindsKnownModelByAlias() {
        val match = AutoEqCatalogMatcher.findBestMatch("Galaxy Buds2 Pro")
        assertEquals("autoeq_samsung_galaxy_buds2_pro", match?.id)
    }

    // --- The bundled catalogue (about 6,000 models): how real Bluetooth names behave ---

    @Test
    fun realBluetoothNamesFindTheirModel() {
        val expected =
            mapOf(
                "WH-1000XM4" to "Sony WH-1000XM4",
                "LE_WH-1000XM4" to "Sony WH-1000XM4",
                "Basti's WH-1000XM4" to "Sony WH-1000XM4",
                "Galaxy Buds2 Pro (1E3F)" to "Samsung Galaxy Buds2 Pro",
                "Galaxy Buds Live 4C2D" to "Samsung Galaxy Buds Live",
                "AirPods Pro" to "Apple AirPods Pro",
                "AirPods Max" to "Apple AirPods Max",
                "MOMENTUM 4" to "Sennheiser Momentum 4 Wireless",
                "Bose QC45" to "Bose QuietComfort 45",
                "Bose QC35 II" to "Bose QuietComfort 35 II",
                "JBL TUNE510BT" to "JBL Tune 510BT",
                "Nothing Ear (2)" to "Nothing ear (2)",
                "Pixel Buds Pro" to "Google Pixel Buds Pro",
            )
        for ((device, model) in expected) {
            assertEquals(device, model, AutoEqCatalogMatcher.findBestMatch(device)?.displayName)
        }
    }

    @Test
    fun aMeasurementVariantNeverWinsOverThePlainModel() {
        // The catalogue also holds "Sony WH-1000XM4 (ANC on)" and "(ANC Off)".
        assertEquals("Sony WH-1000XM4", AutoEqCatalogMatcher.findBestMatch("Sony WH-1000XM4")?.displayName)
    }

    @Test
    fun aDifferentModelNumberIsNeverSuggested() {
        // Neither is in the catalogue; their neighbours (XM6, Buds3 Pro, Major III) are.
        assertNull(AutoEqCatalogMatcher.findBestMatch("WH-1000XM7"))
        assertNull(AutoEqCatalogMatcher.findBestMatch("AirPods Pro 3"))
        assertNull(AutoEqCatalogMatcher.findBestMatch("Major IV"))
    }

    @Test
    fun extraWordsAfterTheModelMeanAnotherModel() {
        assertNull(AutoEqCatalogMatcher.findBestMatch("Galaxy Buds Core"))
    }

    @Test
    fun aNameThatFitsSeveralModelsSuggestsNone() {
        assertNull(AutoEqCatalogMatcher.findBestMatch("Buds Pro"))
        assertNull(AutoEqCatalogMatcher.findBestMatch("AirPods"))
    }

    @Test
    fun speakersAndCarsSuggestNothing() {
        assertNull(AutoEqCatalogMatcher.findBestMatch("JBL Flip 6"))
        assertNull(AutoEqCatalogMatcher.findBestMatch("Car Multimedia"))
    }

    @Test
    fun searchFindsModelsByAnyPartOfTheirName() {
        val results = AutoEqCatalogMatcher.search("hd 6")
        assertTrue(results.any { it.displayName == "Sennheiser HD 600" })
        assertTrue(results.all { it.displayName.lowercase().let { name -> name.contains("hd") && name.contains("6") } })
    }

    @Test
    fun searchListsThePlainModelBeforeItsVariants() {
        val results = AutoEqCatalogMatcher.search("wh-1000xm4").map { it.displayName }
        assertEquals("Sony WH-1000XM4", results.first())
        assertTrue(results.contains("Sony WH-1000XM4 (ANC on)"))
    }

    @Test
    fun blankSearchReturnsNothing() {
        assertTrue(AutoEqCatalogMatcher.search("  ").isEmpty())
    }

    private fun entry(
        id: String,
        displayName: String,
        aliases: List<String>,
    ): AutoEqCatalogEntry =
        AutoEqCatalogEntry(
            id = id,
            displayName = displayName,
            aliases = aliases,
            profile =
                CorrectionProfile(
                    id = id,
                    name = displayName,
                    curve = listOf(TargetPoint(1000f, 0f)),
                ),
        )
}
