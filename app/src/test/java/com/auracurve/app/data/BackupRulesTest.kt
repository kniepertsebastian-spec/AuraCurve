package com.auracurve.app.data

import com.auracurve.app.profile.DataStoreCorrectionSuggestionDismissals
import com.auracurve.app.profile.DataStoreDeviceAliasStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// Roadmap 2, M-4: what goes into the backup. Unit tests run in the module directory.
class BackupRulesTest {
    private data class Rule(
        val kind: String,
        val domain: String,
        val path: String,
    )

    private fun rules(section: Element): Set<Rule> {
        val children = section.childNodes
        return (0 until children.length)
            .map { children.item(it) }
            .filterIsInstance<Element>()
            .map { Rule(it.tagName, it.getAttribute("domain"), it.getAttribute("path")) }
            .toSet()
    }

    private fun document(name: String): Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/$name"))
            .documentElement

    private val deviceSpecific =
        listOf(DataStoreDeviceAliasStore.PREFS_NAME, DataStoreCorrectionSuggestionDismissals.PREFS_NAME)
            .flatMap { name ->
                listOf(
                    Rule("exclude", "file", "datastore/$name.preferences_pb"),
                    Rule("exclude", "sharedpref", "$name.xml"),
                )
            }

    @Test
    fun `device-specific data is excluded, presets settings and playlists are included`() {
        val legacy = rules(document("backup_rules.xml"))
        assertTrue(legacy.containsAll(deviceSpecific))
        assertTrue(Rule("include", "database", ".") in legacy)
        assertTrue(Rule("include", "file", "datastore") in legacy)
    }

    @Test
    fun `cloud backup and device transfer follow the same rules as the old backup`() {
        val legacy = rules(document("backup_rules.xml"))
        val root = document("data_extraction_rules.xml")
        listOf("cloud-backup", "device-transfer").forEach { tag ->
            val section = root.getElementsByTagName(tag).item(0) as Element
            assertEquals(tag, legacy, rules(section))
        }
    }
}
