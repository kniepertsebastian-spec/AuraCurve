package com.auracurve.app.ui.player.catalog

import androidx.compose.foundation.lazy.LazyListScope
import com.auracurve.app.ui.player.PlayerTab

// Player tabs that come from an online catalog. rememberCatalogTabs() provides them; this build
// has none.
class CatalogTabs(
    val tabs: List<PlayerTab>,
    val content: LazyListScope.(PlayerTab) -> Unit,
)
