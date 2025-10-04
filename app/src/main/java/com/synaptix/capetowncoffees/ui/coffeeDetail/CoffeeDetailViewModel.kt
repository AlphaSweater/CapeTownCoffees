package com.synaptix.capetowncoffees.ui.coffeeDetail

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
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
        val ratingText: String? = null,        // "4.6"
        val ratingCountText: String? = null,   // "(123)"
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

        val ratingText = place.rating?.let { String.format("%.1f", it) }
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
                ratingText = ratingText,
                ratingCountText = ratingCountText,
                showRating = ratingText != null,
                imageAvailable = imageAvailable,
                // distance gets filled by maybeUpdateDistance()
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

    /** Pure formatter moved from Fragment. Returns null if nothing meaningful. */
    private fun formatOpeningHours(hours: List<String>?): String? {
        hours ?: return null
        if (hours.isEmpty()) return null

        val dayAbbrev = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val hoursByDay = hours.mapIndexed { index, full ->
            val timePart = full.substringAfter(": ", full)
            dayAbbrev[index] to timePart
        }

        val result = mutableListOf<String>()
        val currentRange = mutableListOf<String>()
        var currentHours = ""

        for ((day, hour) in hoursByDay) {
            if (currentHours != hour) {
                if (currentRange.isNotEmpty()) {
                    result.add(formatDayRange(currentRange, currentHours))
                    currentRange.clear()
                }
                currentHours = hour
            }
            currentRange.add(day)
        }
        if (currentRange.isNotEmpty()) result.add(formatDayRange(currentRange, currentHours))
        return result.joinToString("\n")
    }

    private fun formatDayRange(days: List<String>, hours: String): String = when (days.size) {
        1 -> "${days[0]}: $hours"
        2 -> "${days[0]} & ${days[1]}: $hours"
        else -> "${days.first()} - ${days.last()}: $hours"
    }
}
