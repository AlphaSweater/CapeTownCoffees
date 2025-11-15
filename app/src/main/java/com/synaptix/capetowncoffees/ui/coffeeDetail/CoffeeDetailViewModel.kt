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
//* ChatGPT provided assistance in designing ViewModel logic, LiveData handling, and
//implementing clean MVVM architecture principles.
//* It also helped refine data flow between repositories and UI layers.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.coffeeDetail

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.GetCoffeePlaceDetailsUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeReview.GetCoffeeReviewsForPlaceUseCase
import com.synaptix.capetowncoffees.ui.common.viewmodel.Effect
import com.synaptix.capetowncoffees.ui.common.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui.common.viewmodel.SimpleViewModel
import com.synaptix.capetowncoffees.ui.common.viewmodel.fetchResultInto
import com.synaptix.capetowncoffees.ui.common.viewmodel.loadableState
import com.synaptix.capetowncoffees.ui.common.viewmodel.state
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils
import com.synaptix.capetowncoffees.util.LocationFormattingUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class CafeDetailViewModel @Inject constructor(
    private val getCoffeePlaceDetailsUseCase: GetCoffeePlaceDetailsUseCase,
    private val getCoffeeReviewsForPlaceUseCase: GetCoffeeReviewsForPlaceUseCase
) : SimpleViewModel() {

    // ─────────── Screen Args & Route Keys ───────────
    // Centralized keys to avoid typos across Fragment/VM.
    public object ScreenArgs { public const val PLACE_ID: String = "placeId" }
    private companion object {
        private const val ROUTE_OPEN_MAP = "action_open_external_map"
        private const val ROUTE_DIAL_PHONE = "action_dial_phone"
        private const val ROUTE_OPEN_REVIEW_GALLERY = "action_open_review_gallery"
    }

    // ─────────── UI Model ───────────
    // Flattened, render-ready fields; compute-heavy work stays out of the Fragment.
    public data class Ui(
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

    // ─────────── State ───────────
    // Primary entity and reviews use Loadable for skeleton/error handling.
    public val place = loadableState<CoffeePlaceFull>()
    public val reviews = loadableState<List<CoffeeReview>>()
    public val ui = state(Ui())

    // ─────────── Internals ───────────
    // Keep inputs needed for derived data (e.g., distance).
    private var placeId: String? = null
    private var userLocation: LatLng? = null
    private var placeLocation: LatLng? = null

    // ─────────── Lifecycle ───────────
    // Called once the Fragment hands us arguments; kick off initial loads.
    public override fun start(args: Bundle?) {
        super.start(args)

        val id = args?.getString(ScreenArgs.PLACE_ID)
        if (id.isNullOrBlank()) {
            main { send(Effect.Message("Missing placeId for Café Detail")) }
            return
        }
        placeId = id

        fetchResultInto(place, { getCoffeePlaceDetailsUseCase(placeId!!) }, label = "place")
        fetchResultInto(reviews, { getCoffeeReviewsForPlaceUseCase(placeId!!) }, label = "reviews")

        viewModelScope.launch {
            place.flow.collect { loadable ->
                if (loadable is Loadable.Data) updateUiFrom(loadable.value)
            }
        }
    }

    // ─────────── Actions (Pull-to-refresh / Retry) ───────────
    public fun refresh() {
        val id = placeId ?: return
        fetchResultInto(place, { getCoffeePlaceDetailsUseCase(id) }, label = "place-refresh")
        fetchResultInto(reviews, { getCoffeeReviewsForPlaceUseCase(id) }, label = "reviews-refresh")
    }

    public fun retryReviews() {
        val id = placeId ?: return
        fetchResultInto(reviews, { getCoffeeReviewsForPlaceUseCase(id) }, label = "reviews-retry")
    }

    // ─────────── Inputs from Fragment ───────────
    // Location arrival toggles distance rendering once both points exist.
    public fun onUserLocation(loc: LatLng) {
        userLocation = loc
        maybeUpdateDistance()
    }

    // Address/phone taps emit effects; Fragment handles the Android intents.
    public fun onAddressClicked() {
        val placeData = place.value
        if (placeData is Loadable.Data) {
            val mapUrl = placeData.value.googleMapsUrl ?: return
            val b = Bundle().apply { putString("map_url", mapUrl) }
            main { send(Effect.Navigate(ROUTE_OPEN_MAP, b)) }
        }
    }

    public fun onPhoneClicked() {
        val phone = ui.value.phoneNumber ?: return
        val b = Bundle().apply { putString("phone", phone) }
        main { send(Effect.Navigate(ROUTE_DIAL_PHONE, b)) }
    }

    // ─────────── Review Interactions (Adapter → VM) ───────────
    // For now we show feedback only; wire to domain when ready.
    public fun onReviewLike(reviewId: String) {
        Timber.i("Like review: $reviewId")
        main { send(Effect.Message("Thanks for the feedback!")) }
    }

    public fun onReviewDislike(reviewId: String) {
        Timber.i("Dislike review: $reviewId")
        main { send(Effect.Message("We'll keep improving!")) }
    }

    public fun onOpenPhoto(reviewId: String, startIndex: Int, urls: List<String>) {
        if (urls.isEmpty()) return
        val b = Bundle().apply {
            putString("review_id", reviewId)
            putInt("start", startIndex)
            putStringArrayList("urls", ArrayList(urls))
        }
        main { send(Effect.Navigate(ROUTE_OPEN_REVIEW_GALLERY, b)) }
    }

    // ─────────── Private Helpers ───────────
    // Translate domain model → UI model; compute derived fields here.
    private fun updateUiFrom(place: CoffeePlaceFull) {
        placeLocation = place.location

        val rating = place.googleRating
        val ratingCountText = place.googleRatingCount?.let { "($it)" }
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

    // Update distance/ETA label only when both ends are known; otherwise hide.
    private fun maybeUpdateDistance() {
        val user = userLocation
        val cafe = placeLocation
        if (user != null && cafe != null) {
            val distance = LocationFormattingUtil.distanceMeters(user, cafe)
            val text = LocationFormattingUtil.distanceAndEtaLabel(distance)
            ui.update { it.copy(showDistance = true, distanceText = text) }
        } else {
            ui.update { it.copy(showDistance = false, distanceText = null) }
        }
    }

    // ─────────── Opening Hours Formatting ───────────
    // Normalizes provider-specific strings into a concise weekly schedule.
    public fun formatOpeningHours(
        hours: List<String>?,
        prefs: CoffeeTimeUtils.DisplayPrefs = CoffeeTimeUtils.defaultPrefsProvider(),
        context: Context? = null
    ): String? {
        if (hours.isNullOrEmpty()) return null

        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val zone: ZoneId = prefs.zone
        val anchor: (LocalTime) -> Long = { lt -> LocalDate.now(zone).atTime(lt).atZone(zone).toEpochSecond() }
        val fmt: (LocalTime) -> String = { lt -> CoffeeTimeUtils.formatShortTime(anchor(lt), prefs, context) }

        // Normalize each day's text to either "Closed" or "hh:mm – hh:mm[, hh:mm – hh:mm]"
        val normalized: List<Pair<String, String>> = days.zip(
            hours.map { raw ->
                val cleaned = raw
                    .replace('\u202F', ' ')
                    .replace('\u00A0', ' ')
                    .replace('—', '-')
                    .replace('–', '-')
                    .replace('.', ':')
                    .replace("\\s+".toRegex(), " ")
                    .trim()

                val content = cleaned.substringAfter(':', missingDelimiterValue = "").trim()
                if (content.equals("closed", true) || content.isEmpty()) {
                    "Closed"
                } else {
                    val ranges = content.split(Regex("\\s*,\\s*|\\s*;\\s*|\\s*[、，]\\s*"))
                    val formatted = ranges.mapNotNull { r ->
                        val parts = r.split('-', limit = 2).map { it.trim() }
                        val pair = if (parts.size == 2) parts[0] to parts[1] else null
                        val t1 = CoffeeTimeUtils.parseTimeToLocalTime(pair?.first ?: return@mapNotNull null)
                        val t2 = CoffeeTimeUtils.parseTimeToLocalTime(pair.second)
                        if (t1 != null && t2 != null) "${fmt(t1)} – ${fmt(t2)}" else null
                    }
                    if (formatted.isEmpty()) "Closed" else formatted.joinToString(", ")
                }
            }
        )

        // Collapse consecutive days sharing the same hours into ranges.
        val out = mutableListOf<String>()
        var start = 0
        var cur = normalized[0].second
        for (i in normalized.indices) {
            val last = i == normalized.lastIndex
            val sameNext = !last && normalized[i + 1].second == cur
            if (!sameNext) {
                val label = if (start == i) days[i] else "${days[start]} – ${days[i]}"
                out += "$label: $cur"
                if (!last) {
                    start = i + 1
                    cur = normalized[i + 1].second
                }
            }
        }
        return out.joinToString("\n")
    }
}
