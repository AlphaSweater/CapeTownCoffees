//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.common.viewmodel

import android.os.Bundle
import androidx.core.os.bundleOf

/* ──────────────────────────────────────────────────────────────────────────────
 * SIMPLE Screen Args
 * ────────────────────────────────────────────────────────────────────────────── */

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