package com.auracurve.app.ui.player

import com.auracurve.app.R
import com.auracurve.app.text.TextProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

// Where an import reports to: the player screen's message line. Owned by PlayerViewModel,
// which hands it over once.
class LinkImportSession(
    val scope: CoroutineScope,
    val importState: MutableStateFlow<ImportUiState>,
)

// Pasted and shared links. This build has no streaming service and no link parser
// (NoLinkImport); a planned integration through the official SoundCloud API would open
// SoundCloud track and playlist links here.
interface LinkImport {
    fun attach(session: LinkImportSession)

    fun importText(text: String)
}

// No link import: the screens hide the link field, so this only answers a stray call.
class NoLinkImport(
    private val texts: TextProvider? = null,
) : LinkImport {
    private var session: LinkImportSession? = null

    override fun attach(session: LinkImportSession) {
        this.session = session
    }

    override fun importText(text: String) {
        val message = texts?.get(R.string.online_sources_unavailable).orEmpty()
        session?.importState?.value = ImportUiState(message = message, isError = true)
    }
}
