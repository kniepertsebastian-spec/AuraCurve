package com.auracurve.app.ui.player

import com.auracurve.app.R
import com.auracurve.app.text.FakeTextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// No streaming service in this build: a link that still reaches the import is refused with a
// plain message and nothing is looked up.
class NoLinkImportTest {
    private val texts = FakeTextProvider()

    @Test
    fun `a link is refused with the not available message`() {
        val importState = MutableStateFlow(ImportUiState())
        val import = NoLinkImport(texts)
        import.attach(LinkImportSession(TestScope(), importState))

        import.importText("https://example.com/artist/track")

        assertTrue(importState.value.isError)
        assertEquals(texts.get(R.string.online_sources_unavailable), importState.value.message)
    }
}
