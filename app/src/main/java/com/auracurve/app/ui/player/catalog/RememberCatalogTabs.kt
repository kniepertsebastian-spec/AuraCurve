package com.auracurve.app.ui.player.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.auracurve.app.player.model.TrackItem

// No online catalogs in this build: each one needs its own permission check with the provider
// before it can be added.
@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberCatalogTabs(
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
): CatalogTabs = remember { CatalogTabs(emptyList()) {} }
