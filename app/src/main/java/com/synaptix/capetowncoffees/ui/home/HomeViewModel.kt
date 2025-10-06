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

    // ─────────── Config ───────────
    private val MIN_REQUERY_DISTANCE_M = 20.0        // refresh if user moved this much
    private val TTL_MILLIS = 10 * 60 * 1000L         // 10 minutes

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

    // ─────────── In-memory cache ───────────
    private data class CacheEntry(
        val baseItems: List<CoffeePlaceLite>,   // unfiltered source list
        val center: LatLng,                     // location used for the request
        val timestamp: Long                     // when fetched
    )
    private var nearCache: CacheEntry? = null
    private var featuredCache: CacheEntry? = null

    // Public API from Fragment
    fun onUserLocation(loc: LatLng) {
        val prev = ui.value.currentLocation
        ui.update { it.copy(currentLocation = loc) }

        // If we have cache and movement is small, just paint cached data; else refetch
        if (shouldUseNearCacheFor(loc)) {
            nearCache?.let { cache ->
                nearMe.data(filterByCategoryInternal(ui.value.selectedCategory, cache.baseItems, loc))
            }
        }
        if (shouldUseFeaturedCacheFor(loc)) {
            featuredCache?.let { cache ->
                featured.data(cache.baseItems)
            }
        }

        if (!hasFreshEnough(nearCache, loc) || !hasFreshEnough(featuredCache, loc)) {
            refresh(force = true) // moved far or stale
        }
    }

    fun onCategorySelected(category: Category) {
        ui.update { it.copy(selectedCategory = category) }

        // Re-use near cache instantly if present
        val loc = ui.value.currentLocation
        val base = nearCache?.baseItems
        if (base != null && loc != null) {
            nearMe.data(filterByCategoryInternal(category, base, loc))
        }
    }

    fun pullToRefresh() = refresh(force = true)

    fun refresh(force: Boolean = false) {
        val loc = ui.value.currentLocation ?: run {
            main { send(Effect.Message("Location not available yet")) }
            return
        }

        // If not forced and caches are valid, just paint and bail (no network)
        if (!force) {
            var served = false
            if (hasFreshEnough(nearCache, loc)) {
                nearCache?.let { cache ->
                    nearMe.data(filterByCategoryInternal(ui.value.selectedCategory, cache.baseItems, loc))
                    served = true
                }
            }
            if (hasFreshEnough(featuredCache, loc)) {
                featuredCache?.let { cache ->
                    featured.data(cache.baseItems)
                    served = true
                }
            }
            if (served) return
        }

        // Show skeletons only if we have nothing to show yet
        if (nearMe.value !is Loadable.Data) nearMe.loading()
        if (featured.value !is Loadable.Data) featured.loading()

        ui.update { it.copy(isRefreshing = true) }

        io {
            val now = System.currentTimeMillis()

            val nearbyParams = CoffeeSearchParameters.Builder()
                .radiusMeters(5000)
                .maxResults(32)
                .build()

            val featuredParams = CoffeeSearchParameters.Builder()
                .radiusMeters(5000)
                .maxResults(5)
                .sortByDistance(false)
                .build()

            val nearDeferred = async {
                searchNearby(params = nearbyParams, userLatLng = loc).map { list -> list }
            }
            val featuredDeferred = async {
                searchNearby(params = featuredParams, userLatLng = loc).map { list -> list }
            }

            // NEAR
            nearDeferred.await()
                .onSuccess { list ->
                    nearCache = CacheEntry(baseItems = list, center = loc, timestamp = now)
                    val filtered = filterByCategoryInternal(ui.value.selectedCategory, list, loc)
                    nearMe.data(filtered)
                }
                .onFailure { nearMe.error(it.toUiError("Couldn't load nearby")) }

            // FEATURED
            featuredDeferred.await()
                .onSuccess { list ->
                    featuredCache = CacheEntry(baseItems = list, center = loc, timestamp = now)
                    featured.data(list)
                }
                .onFailure { featured.error(it.toUiError("Couldn't load featured")) }

            main { ui.update { it.copy(isRefreshing = false) } }
        }
    }

    // ─────────── Cache policy helpers ───────────

    private fun hasFreshEnough(entry: CacheEntry?, loc: LatLng): Boolean {
        if (entry == null) return false
        val ageOk = (System.currentTimeMillis() - entry.timestamp) <= TTL_MILLIS
        val moved = distance(entry.center, loc) >= MIN_REQUERY_DISTANCE_M
        return ageOk && !moved
    }

    private fun shouldUseNearCacheFor(loc: LatLng): Boolean = hasFreshEnough(nearCache, loc)
    private fun shouldUseFeaturedCacheFor(loc: LatLng): Boolean = hasFreshEnough(featuredCache, loc)

    private fun distance(a: LatLng, b: LatLng): Double =
        locationUtil.distanceMeters(a, b).toDouble()

    // ─────────── Local filtering/sorting (no network) ───────────
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
