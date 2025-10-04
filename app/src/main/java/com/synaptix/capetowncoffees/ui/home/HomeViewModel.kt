package com.synaptix.capetowncoffees.ui.home

import android.os.Bundle
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.SearchNearbyCoffeePlacesUseCase
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Effect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.SimpleViewModel
import com.synaptix.capetowncoffees.ui._simple.viewmodel.fetchResultInto
import com.synaptix.capetowncoffees.ui._simple.viewmodel.loadableState
import com.synaptix.capetowncoffees.ui._simple.viewmodel.state
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchNearby: SearchNearbyCoffeePlacesUseCase,     // suspend (params, userLatLng) -> Result<List<CoffeePlaceLite>>
    private val locationUtil: LocationUtil
) : SimpleViewModel() {

    /** UI bits the Fragment binds to (kept local). */
    data class Ui(
        val categories: List<Category> = DEFAULT_CATEGORIES,
        val selectedCategory: Category = DEFAULT_CATEGORIES.first(),
        val currentLocation: LatLng? = null,
        val isRefreshing: Boolean = false
    )

    val ui = state(Ui())

    // Sectional/secondary UI: show skeletons & errors independently
    val nearMe   = loadableState<List<CoffeePlaceLite>>()   // filtered/sorted by category
    val featured = loadableState<List<CoffeePlaceLite>>()   // top-rated picks

    // Backing cache of last fetch
    private var allPlaces: List<CoffeePlaceLite> = emptyList()

    override fun start(args: Bundle?) {
        super.start(args)
        // Typically we wait for location from Fragment → onUserLocation()
        // But you could kick a fetch with a fallback zone if you want.
    }

    /** Fragment passes device location here once permissions are handled. */
    fun onUserLocation(loc: LatLng) {
        ui.update { it.copy(currentLocation = loc) }
        refresh() // trigger initial load with the new location
    }

    fun refresh() {
        val loc = ui.value.currentLocation ?: run {
            main { send(Effect.Message("Location not available yet")) }
            return
        }

        // Drive both lists from a single search
        ui.update { it.copy(isRefreshing = true) }

        val params = CoffeeSearchParameters.Builder()
            .radiusMeters(5_000)
            .maxResults(32)
            .build()

        fetchResultInto(
            target = nearMe, // we’ll fill & then also compute featured
            call = {
                searchNearby(params = params, userLatLng = loc).map { places ->
                    // Side-effect: cache, compute sections; return near-me filtered list
                    allPlaces = places
                    computeFeaturedAndNearMe()
                    // nearMe Loadable will be set in computeFeaturedAndNearMe(), but we must return something;
                    // return the filtered list for nearMe as well (keeps semantics tight)
                    filterByCategoryInternal(ui.value.selectedCategory, places, ui.value.currentLocation)
                }
            },
            label = "home-search"
        )

        // ensure spinner off after transitions
        main { ui.update { it.copy(isRefreshing = false) } }
    }

    fun onCategorySelected(category: Category) {
        ui.update { it.copy(selectedCategory = category) }
        if (allPlaces.isNotEmpty()) {
            // Re-derive lists from cache
            computeFeaturedAndNearMe()
        }
    }

    // ───────────────────────────────── helpers ─────────────────────────────────

    private fun computeFeaturedAndNearMe() {
        val loc = ui.value.currentLocation
        val selected = ui.value.selectedCategory
        val filtered = filterByCategoryInternal(selected, allPlaces, loc)

        // Featured: highest rated first, >= 4.0 (tweak as you like)
        val featuredList = allPlaces
            .filter { (it.rating ?: 0.0) >= 4.0 }
            .sortedWith(
                compareByDescending<CoffeePlaceLite> { it.rating ?: 0.0 }
                    .thenByDescending { it.ratingCount ?: 0 }
            )
            .take( max(3, 0) )

        // Push to Loadables (replace skeletons/errors)
        nearMe.data(filtered)
        featured.data(featuredList)
    }

    private fun filterByCategoryInternal(
        category: Category,
        source: List<CoffeePlaceLite>,
        user: LatLng?
    ): List<CoffeePlaceLite> = when (category.name.lowercase()) {
        "popular" -> source.sortedByDescending { it.ratingCount ?: 0 }
        "rated"   -> source.sortedByDescending { it.rating ?: 0.0 }
        "nearby"  -> {
            if (user == null) source else {
                source.sortedBy { place ->
                    place.location?.let { locationUtil.distanceMeters(user, it).toFloat() } ?: Float.MAX_VALUE
                }
            }
        }
        "dates"   -> source.sortedByDescending { (it.rating ?: 0.0) + ((it.ratingCount ?: 0) / 100f) }
        // "all" or unknown
        else      -> source
    }

    companion object {
        private val DEFAULT_CATEGORIES = listOf(
            Category(1, "All",       com.synaptix.capetowncoffees.R.drawable.ic_medal),
            Category(2, "Popular",   com.synaptix.capetowncoffees.R.drawable.ic_star),
            Category(3, "Pet Friendly", com.synaptix.capetowncoffees.R.drawable.baseline_pets_24),
            Category(4, "Nearby",    com.synaptix.capetowncoffees.R.drawable.ic_location),
            Category(5, "Dates",     com.synaptix.capetowncoffees.R.drawable.ic_heart)
        )
    }
}
