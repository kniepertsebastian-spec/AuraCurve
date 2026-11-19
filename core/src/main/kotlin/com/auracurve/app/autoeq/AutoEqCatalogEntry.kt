package com.auracurve.app.autoeq

import com.auracurve.app.correction.CorrectionProfile

/** How a headphone sits on or in the ear, as AutoEq files its measurements. */
enum class HeadphoneForm {
    OVER_EAR,
    IN_EAR,
    EARBUD,
}

/**
 * One headphone of [BuiltInAutoEqCatalog]: its name, the measurement source and the correction
 * curve. AutoEqCatalogMatcher recognizes a Bluetooth/USB device name against [displayName] and
 * [aliases].
 *
 * The curve is computed on first use only: the catalogue holds thousands of models, and only the
 * one a user actually picks ever needs its filters evaluated.
 */
class AutoEqCatalogEntry(
    val id: String,
    val displayName: String,
    val aliases: List<String> = emptyList(),
    /** Who measured it, e.g. "oratory1990"; null when unknown. */
    val source: String? = null,
    val form: HeadphoneForm? = null,
    profileFactory: () -> CorrectionProfile,
) {
    constructor(
        id: String,
        displayName: String,
        aliases: List<String> = emptyList(),
        profile: CorrectionProfile,
    ) : this(id = id, displayName = displayName, aliases = aliases, profileFactory = { profile })

    val profile: CorrectionProfile by lazy(profileFactory)

    override fun toString(): String = "AutoEqCatalogEntry($id, $displayName)"
}
