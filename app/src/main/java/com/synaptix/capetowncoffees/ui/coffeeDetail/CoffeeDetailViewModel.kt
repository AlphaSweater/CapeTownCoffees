package com.synaptix.capetowncoffees.ui.coffeeDetail

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.GetCoffeePlaceDetailsUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeReview.GetCoffeeReviewsForPlaceUseCase
import com.synaptix.capetowncoffees.ui._simple.Effect
import com.synaptix.capetowncoffees.ui._simple.Loadable
import com.synaptix.capetowncoffees.ui._simple.SimpleViewModel
import com.synaptix.capetowncoffees.ui._simple.fetchResultInto
import com.synaptix.capetowncoffees.ui._simple.loadableState
import com.synaptix.capetowncoffees.ui._simple.state
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CafeDetailViewModel @Inject constructor(
    private val getCoffeePlaceDetailsUseCase: GetCoffeePlaceDetailsUseCase,      // suspend (id) -> Result<CoffeePlaceFull>
    private val getCoffeeReviewsForPlaceUseCase: GetCoffeeReviewsForPlaceUseCase, // suspend (id) -> Result<List<CoffeeReview>>
    private val locationUtil: LocationUtil
) : SimpleViewModel() {

    object ScreenArgs { const val PLACE_ID = "placeId" }

    /** UI-ready state the Fragment binds to. */
    data class Ui(
        val name: String = "",
        val address: String = "",
        val addressClickable: Boolean = false,
        val openingHoursText: String = "",
        val hasOpeningHours: Boolean = false,
        val phoneNumber: String? = null,
        val rating: Double? = null,
        val ratingCountText: String? = null,
        val showRating: Boolean = false,
        val showDistance: Boolean = false,
        val distanceText: String? = null,
        val imageAvailable: Boolean = false
    )

    // Primary entity for the screen
    val place = loadableState<CoffeePlaceFull>()
    // Sectional data (shows its own skeleton / error)
    val reviews = loadableState<List<CoffeeReview>>()

    // UI model
    val ui = state(Ui())

    // Internals
    private var placeId: String? = null
    private var userLocation: LatLng? = null
    private var placeLocation: LatLng? = null


    override fun start(args: Bundle?) {
        super.start(args)

        val id = args?.getString(ScreenArgs.PLACE_ID)

        if (id.isNullOrBlank()) {
            main { send(Effect.Message("Missing placeId for Café Detail")) }
            return
        }
        placeId = id

        // One-shot fetch of full details (Result-aware)
        fetchResultInto(place, { getCoffeePlaceDetailsUseCase(placeId!!) }, label = "place")

        // One-shot fetch of reviews (Result-aware)
        fetchResultInto(reviews, { getCoffeeReviewsForPlaceUseCase(placeId!!) }, label = "reviews")

        // Whenever 'place' changes to Data, (re)compute Ui
        viewModelScope.launch {
            place.flow.collect { loadable ->
                when (loadable) {
                    is Loadable.Data -> updateUiFrom(loadable.value)
                    else -> Unit
                }
            }
        }
    }

    fun refresh() {
        val id = placeId ?: return
        fetchResultInto(place, { getCoffeePlaceDetailsUseCase(id) }, label = "place-refresh")
        fetchResultInto(reviews, { getCoffeeReviewsForPlaceUseCase(id) }, label = "reviews-refresh")
    }

    fun retryReviews() {
        val id = placeId ?: return
        fetchResultInto(reviews, { getCoffeeReviewsForPlaceUseCase(id) }, label = "reviews-retry")
    }

    /** Fragment tells us when it has a device location. */
    fun onUserLocation(loc: LatLng) {
        userLocation = loc
        maybeUpdateDistance()
    }

    /** User taps address in the UI. Fragment will perform the Intent using these args. */
    fun onAddressClicked() {
        val placeData = place.value
        if (placeData is Loadable.Data) {
            val mapUrl = placeData.value.googleMapsUrl ?: return
            val b = Bundle().apply {
                putString("map_url", mapUrl)
            }
            main { send(Effect.Navigate("action_open_external_map", b)) }
        }
    }


    /** User taps phone. Fragment will perform the dial Intent using this arg. */
    fun onPhoneClicked() {
        val phone = ui.value.phoneNumber ?: return
        val b = Bundle().apply { putString("phone", phone) }
        main { send(Effect.Navigate("action_dial_phone", b)) }
    }

    // ───────────────────────────────── helpers ─────────────────────────────────

    private fun updateUiFrom(place: CoffeePlaceFull) {
        placeLocation = place.location

        val rating = place.rating
        val ratingCountText = place.ratingCount?.let { "(${it})" }
        val phone = place.nationalPhoneNumber ?: place.internationalPhoneNumber
        val hours = formatOpeningHours(place.currentOpeningHours)
        val imageAvailable = !place.images.isNullOrEmpty()

        ui.set(
            ui.value.copy(
                name = place.name.orEmpty(),
                address = place.address.orEmpty(),
                addressClickable = place.location != null,
                openingHoursText = hours ?: "",
                hasOpeningHours = hours != null,
                phoneNumber = phone,
                rating = rating,
                ratingCountText = ratingCountText,
                showRating = rating != null,
                imageAvailable = imageAvailable,
                showDistance = false,
                distanceText = null
            )
        )
        maybeUpdateDistance()
    }

    private fun maybeUpdateDistance() {
        val user = userLocation
        val cafe = placeLocation
        if (user != null && cafe != null) {
            val distance = locationUtil.distanceMeters(user, cafe)
            val text = locationUtil.distanceAndEtaLabel(distance)
            ui.update { it.copy(showDistance = true, distanceText = text) }
        } else {
            ui.update { it.copy(showDistance = false, distanceText = null) }
        }
    }

    /**
     * Formats weekly opening hours (Mon..Sun). Input examples per line:
     *  - "Mon: 07:00-17:00", "Monday: 7:00 am – 5:00 pm", "Tue: Closed"
     * Supports multiple intervals: "08:00–12:00, 13:00–17:00".
     */
    fun formatOpeningHours(
        hours: List<String>?,
        prefs: CoffeeTimeUtils.DisplayPrefs = CoffeeTimeUtils.defaultPrefsProvider(),
        context: Context? = null
    ): String? {
        if (hours.isNullOrEmpty()) return null

        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val zone: ZoneId = prefs.zone
        val anchor: (LocalTime) -> Long = { LocalDate.now(zone).atTime(it).atZone(zone).toEpochSecond() }
        val fmt: (LocalTime) -> String = { CoffeeTimeUtils.formatShortTime(anchor(it), prefs, context) }

        // Parse & normalize each day's line → "Closed" or "hh:mm–hh:mm[, hh:mm–hh:mm]"
        val normalized = days.zip(hours.map { raw ->
            val cleaned = raw
                .replace('\u202F', ' ') // narrow no-break space
                .replace('\u00A0', ' ') // no-break space
                .replace('—', '-')      // em dash
                .replace('–', '-')      // en dash
                .replace('.', ':')      // "7.00 am" → "7:00 am"
                .replace("\\s+".toRegex(), " ")
                .trim()

            val content = cleaned.substringAfter(':', missingDelimiterValue = "").trim()
            if (content.equals("closed", true) || content.isEmpty()) "Closed" else {
                val ranges = content.split(Regex("\\s*,\\s*|\\s*;\\s*|\\s*[、，]\\s*")) // commas, semicolons, CJK commas
                val formatted = ranges.mapNotNull { r ->
                    val (a, b) = r.split('-', limit = 2).map { it.trim() }.let { if (it.size == 2) it[0] to it[1] else null } ?: return@mapNotNull null
                    val t1 = CoffeeTimeUtils.parseTimeToLocalTime(a)
                    val t2 = CoffeeTimeUtils.parseTimeToLocalTime(b)
                    if (t1 != null && t2 != null) "${fmt(t1)} – ${fmt(t2)}" else null
                }
                if (formatted.isEmpty()) "Closed" else formatted.joinToString(", ")
            }
        })

        // Group consecutive days with identical hours
        val out = mutableListOf<String>()
        var start = 0
        var cur = normalized[0].second
        for (i in normalized.indices) {
            val last = i == normalized.lastIndex
            val sameNext = !last && normalized[i + 1].second == cur
            if (!sameNext) {
                val label = if (start == i) days[i] else "${days[start]} – ${days[i]}"
                out += "$label: $cur"
                if (!last) { start = i + 1; cur = normalized[i + 1].second }
            }
        }
        return out.joinToString("\n")
    }
}
