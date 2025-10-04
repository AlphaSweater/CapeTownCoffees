package com.synaptix.capetowncoffees.ui.coffeeDetail

import android.os.Bundle
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.GetCoffeePlaceDetailsUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeReview.GetCoffeeReviewsForPlaceUseCase
import com.synaptix.capetowncoffees.ui._simple.Effect
import com.synaptix.capetowncoffees.ui._simple.SimpleViewModel
import com.synaptix.capetowncoffees.ui._simple.fetchResultInto
import com.synaptix.capetowncoffees.ui._simple.loadableState
import com.synaptix.capetowncoffees.ui._simple.state
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

object ScreenArgs {
    const val PLACE_ID = "placeId"
}

@HiltViewModel
class CafeDetailViewModel @Inject constructor(
    private val getCoffeePlaceDetailsUseCase: GetCoffeePlaceDetailsUseCase,      // suspend (id) -> Result<CoffeePlaceFull>
    private val getCoffeeReviewsForPlaceUseCase: GetCoffeeReviewsForPlaceUseCase // suspend (id) -> Result<List<CoffeeReview>>
) : SimpleViewModel() {

    // Primary entity for the screen
    val place = loadableState<CoffeePlaceFull>()

    // Sectional data (shows its own skeleton / error)
    val reviews = loadableState<List<CoffeeReview>>()

    // Optional UI-only state
    val selectedLocation = state<LatLng?>(null)

    private var placeId: String? = null

    override fun start(args: Bundle?) {
        super.start(args)

        val argId = args?.getString(ScreenArgs.PLACE_ID)
        if (argId.isNullOrBlank()) {
            // Friendly user message; logs are already handled by SimpleVM
            main { send(Effect.Message("Missing placeId for Café Detail")) }
            return
        }

        placeId = argId

        // One-shot fetch of full details (Result-aware)
        fetchResultInto(place, call = suspend { getCoffeePlaceDetailsUseCase(placeId!!) })

        // One-shot fetch of reviews (Result-aware)
        fetchResultInto(reviews, call = suspend { getCoffeeReviewsForPlaceUseCase(placeId!!) })
    }

    fun refresh() {
        val id = placeId ?: return
        fetchResultInto(place, call = suspend { getCoffeePlaceDetailsUseCase(id) })
        fetchResultInto(reviews, call = suspend { getCoffeeReviewsForPlaceUseCase(id) })
    }

    fun retryReviews() {
        val id = placeId ?: return
        fetchResultInto(reviews, call = suspend { getCoffeeReviewsForPlaceUseCase(id) })
    }

    fun updateSelectedLocation(location: LatLng) {
        selectedLocation.set(location)
    }
}
