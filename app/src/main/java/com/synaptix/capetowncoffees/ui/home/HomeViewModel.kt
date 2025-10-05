package com.synaptix.capetowncoffees.ui.home

import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.SearchNearbyCoffeePlacesUseCase
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Effect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui._simple.viewmodel.SimpleViewModel
import com.synaptix.capetowncoffees.ui._simple.viewmodel.loadableState
import com.synaptix.capetowncoffees.ui._simple.viewmodel.state
import com.synaptix.capetowncoffees.ui._simple.viewmodel.toUiError
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchNearby: SearchNearbyCoffeePlacesUseCase,
    private val locationUtil: LocationUtil
) : SimpleViewModel() {

    data class Ui(
        val categories: List<Category> = DEFAULT_CATEGORIES,
        val selectedCategory: Category = DEFAULT_CATEGORIES.first(),
        val currentLocation: LatLng? = null,
        val isRefreshing: Boolean = false
    )

    val ui = state(Ui())

    // Exposed to Fragment
    val nearMe   = loadableState<List<CoffeePlaceLite>>()   // vertical list
    val featured = loadableState<List<CoffeePlaceLite>>()   // horizontal carousel

    fun onUserLocation(loc: LatLng) {
        ui.update { it.copy(currentLocation = loc) }
        refresh()
    }

    fun onCategorySelected(category: Category) {
        ui.update { it.copy(selectedCategory = category) }
        // If data already present, just re-sort/trim without refetch:
        val current = (nearMe.value as? Loadable.Data)?.value
        val loc = ui.value.currentLocation
        if (current != null && loc != null) {
            nearMe.data(filterByCategoryInternal(category, current, loc))
        }
    }

    fun refresh() {
        val loc = ui.value.currentLocation ?: run {
            main { send(Effect.Message("Location not available yet")) }
            return
        }

        // retire global spinner usage, or leave it but DON'T bind it in the fragment
        ui.update { it.copy(isRefreshing = false) }

        // 1) tell UI to draw skeletons right away
        nearMe.loading()
        featured.loading()

        // Run **two network calls in parallel** (fastest wins first paint)
        io {
            val nearbyParams = CoffeeSearchParameters.Builder()
                .radiusMeters(5000)
                .maxResults(32)
                .build()

            val nearDeferred = async {
                searchNearby(params = nearbyParams, userLatLng = loc)
                    .map { list ->
                        filterByCategoryInternal(ui.value.selectedCategory, list, loc)
                    }
            }

            val featuredParams = CoffeeSearchParameters.Builder()
                .radiusMeters(5000)
                .maxResults(5)
                .sortByDistance(false)
                .build()

            val featuredDeferred = async {
                searchNearby(params = featuredParams, userLatLng = loc)
            }

            // publish results independently
            nearDeferred.await()
                .onSuccess { nearMe.data(it) }
                .onFailure { nearMe.error(it.toUiError("Couldn't load nearby")) }

            featuredDeferred.await()
                .onSuccess { featured.data(it) }
                .onFailure { featured.error(it.toUiError("Couldn't load featured")) }

            main { ui.update { it.copy(isRefreshing = false) } }
        }
    }

    private fun filterByCategoryInternal(
        category: Category,
        source: List<CoffeePlaceLite>,
        user: LatLng?
    ): List<CoffeePlaceLite> = when (category.name.lowercase()) {
        "popular" -> source.sortedByDescending { it.ratingCount ?: 0 }
        "rated"   -> source.sortedByDescending { it.rating ?: 0.0 }
        "nearby"  -> if (user == null) source else source.sortedBy {
            it.location?.let { ll -> locationUtil.distanceMeters(user, ll).toFloat() } ?: Float.MAX_VALUE
        }
        "dates"   -> source.sortedByDescending { (it.rating ?: 0.0) + ((it.ratingCount ?: 0) / 100f) }
        else      -> source
    }

    companion object {
        private val DEFAULT_CATEGORIES = listOf(
            Category(1, "All", R.drawable.ic_ctc_medal),
            Category(2, "Popular", R.drawable.ic_ctc_star),
            Category(3, "Pet Friendly", R.drawable.ic_ctc_pet),
            Category(4, "Nearby", R.drawable.ic_ctc_location),
            Category(5, "Dates", R.drawable.ic_ctc_heart)
        )
    }
}
