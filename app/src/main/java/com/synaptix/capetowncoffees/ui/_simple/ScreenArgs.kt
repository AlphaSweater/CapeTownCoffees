package com.synaptix.capetowncoffees.ui._simple

import android.os.Bundle
import androidx.core.os.bundleOf

/**
 * Central place for navigation argument keys + Bundle builders.
 * Avoids hard-coded string keys across the app.
 *
 * ### Example
 * ```
 * val args = ScreenArgs.placeDetails("123")
 * findNavController().navigate(R.id.placeDetailsFragment, args)
 * ```
 */
object ScreenArgs {
    const val PLACE_ID = "placeId"

    /** Build args for PlaceDetails screen. */
    fun placeDetails(placeId: String): Bundle = bundleOf(PLACE_ID to placeId)
}