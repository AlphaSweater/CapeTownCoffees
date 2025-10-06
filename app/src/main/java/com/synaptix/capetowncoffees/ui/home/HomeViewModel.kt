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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchNearby: SearchNearbyCoffeePlacesUseCase,
    private val locationUtil: LocationUtil
) : SimpleViewModel() {

    /* ╭──────────────────────────── Config ──────────────────────────────╮ */
    private companion object {
        const val MIN_REQUERY_DISTANCE_M = 20.0
        const val TTL_MILLIS = 10 * 60 * 1000L

        // Nearby config
        const val NEAR_RADIUS_M = 5_000
        const val NEAR_MAX_RESULTS = 32
        const val NEAR_SORT_BY_DISTANCE = true

        // Featured config (popular within radius)
        const val FEATURED_RADIUS_M = 5_000
        const val FEATURED_MAX_RESULTS = 20
        const val FEATURED_SORT_BY_DISTANCE = false

        // Categories
        val DEFAULT_CATEGORIES = listOf(
            Category(1, "All", R.drawable.ic_ctc_medal),
            Category(2, "Popular", R.drawable.ic_ctc_star),
            Category(3, "Pet Friendly", R.drawable.ic_ctc_pet),
            Category(4, "Nearby", R.drawable.ic_ctc_location),
            Category(5, "Dates", R.drawable.ic_ctc_heart)
        )
    }
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────────────── UI State ────────────────────────────╮ */
    data class Ui(
        val categories: List<Category> = DEFAULT_CATEGORIES,
        val selectedCategory: Category = DEFAULT_CATEGORIES.first(),
        val currentLocation: LatLng? = null,
        val isRefreshingNear: Boolean = false,
        val isRefreshingFeatured: Boolean = false
    ) {
        val isRefreshing: Boolean get() = isRefreshingNear || isRefreshingFeatured
    }
    val ui = state(Ui())

    // Lists exposed to the Fragment
    val nearMe   = loadableState<List<CoffeePlaceLite>>()  // vertical list (first 20 nearest)
    val featured = loadableState<List<CoffeePlaceLite>>()  // horizontal carousel (popular)
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────────────── Caches ──────────────────────────────╮ */
    private data class CacheEntry(
        val baseItems: List<CoffeePlaceLite>,
        val center: LatLng,
        val timestamp: Long
    )
    private var nearCache: CacheEntry? = null
    private var featuredCache: CacheEntry? = null
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────── In-flight jobs (cancel on re-run) ───────────╮ */
    private var nearJob: Job? = null
    private var featuredJob: Job? = null
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ───────────────────────────── Public API ────────────────────────── */

    fun onUserLocation(loc: LatLng) {
        ui.update { it.copy(currentLocation = loc) }

        // Paint from cache immediately if valid
        if (hasFreshEnough(nearCache, loc)) {
            nearCache?.let { cache ->
                nearMe.data(filterByCategory(ui.value.selectedCategory, cache.baseItems, loc))
            }
        }
        if (hasFreshEnough(featuredCache, loc)) {
            featuredCache?.let { cache -> featured.data(cache.baseItems) }
        }

        // Independently refresh each section if its cache is stale or too far
        if (!hasFreshEnough(nearCache, loc)) refreshNear(force = true)
        if (!hasFreshEnough(featuredCache, loc)) refreshFeatured(force = true)
    }

    fun onCategorySelected(category: Category) {
        ui.update { it.copy(selectedCategory = category) }
        val loc = ui.value.currentLocation ?: return
        nearCache?.let { cache ->
            nearMe.data(filterByCategory(category, cache.baseItems, loc))
        }
    }

    fun pullToRefresh() {
        refreshNear(force = true)
        refreshFeatured(force = true)
    }

    fun refresh(force: Boolean = false) {
        refreshNear(force)
        refreshFeatured(force)
    }

    /* ──────────────────────────── Refresh: NEAR ──────────────────────── */

    fun refreshNear(force: Boolean) {
        val loc = ui.value.currentLocation ?: run {
            main { send(Effect.Message("Location not available yet")) }
            return
        }

        if (!force && hasFreshEnough(nearCache, loc)) {
            nearCache?.let { cache ->
                nearMe.data(filterByCategory(ui.value.selectedCategory, cache.baseItems, loc))
            }
            return
        }

        // Only show loading if nothing is on screen yet
        if (nearMe.value !is Loadable.Data) nearMe.loading()
        ui.update { it.copy(isRefreshingNear = true) }

        // cancel previous and launch fresh
        io {
            nearJob?.cancelAndJoin()
            nearJob = launch {
                val now = System.currentTimeMillis()
                val params = CoffeeSearchParameters.Builder()
                    .radiusMeters(NEAR_RADIUS_M)
                    .maxResults(NEAR_MAX_RESULTS)
                    .sortByDistance(NEAR_SORT_BY_DISTANCE)
                    .build()

                searchNearby(params = params, userLatLng = loc)
                    .onSuccess { list ->
                        nearCache = CacheEntry(list, loc, now)
                        val filtered = filterByCategory(ui.value.selectedCategory, list, loc)
                        nearMe.data(filtered)
                    }
                    .onFailure { err ->
                        if (nearMe.value !is Loadable.Data) {
                            nearMe.error(err.toUiError("Couldn't load nearby"))
                        }
                    }

                main { ui.update { it.copy(isRefreshingNear = false) } }
            }
        }
    }

    /* ───────────────────────── Refresh: FEATURED ─────────────────────── */

    fun refreshFeatured(force: Boolean) {
        val loc = ui.value.currentLocation ?: run {
            main { send(Effect.Message("Location not available yet")) }
            return
        }

        if (!force && hasFreshEnough(featuredCache, loc)) {
            featuredCache?.let { cache -> featured.data(cache.baseItems) }
            return
        }

        if (featured.value !is Loadable.Data) featured.loading()
        ui.update { it.copy(isRefreshingFeatured = true) }

        io {
            featuredJob?.cancelAndJoin()
            featuredJob = launch {
                val now = System.currentTimeMillis()
                val params = CoffeeSearchParameters.Builder()
                    .radiusMeters(FEATURED_RADIUS_M)
                    .maxResults(FEATURED_MAX_RESULTS)
                    .sortByDistance(FEATURED_SORT_BY_DISTANCE) // server provides “popular” sort
                    .build()

                searchNearby(params = params, userLatLng = loc)
                    .onSuccess { list ->
                        featuredCache = CacheEntry(list, loc, now)
                        featured.data(list)
                    }
                    .onFailure { err ->
                        if (featured.value !is Loadable.Data) {
                            featured.error(err.toUiError("Couldn't load featured"))
                        }
                    }

                main { ui.update { it.copy(isRefreshingFeatured = false) } }
            }
        }
    }

    /* ─────────────────────────── Helpers & Policy ────────────────────── */

    private fun hasFreshEnough(entry: CacheEntry?, loc: LatLng): Boolean {
        if (entry == null) return false
        val fresh = (System.currentTimeMillis() - entry.timestamp) <= TTL_MILLIS
        val moved = distance(entry.center, loc) >= MIN_REQUERY_DISTANCE_M
        return fresh && !moved
    }

    private fun distance(a: LatLng, b: LatLng): Double =
        locationUtil.distanceMeters(a, b).toDouble()

    // local sorting/filtering for the NEAR list only (Featured is server-driven)
    private fun filterByCategory(
        category: Category,
        source: List<CoffeePlaceLite>,
        user: LatLng?
    ): List<CoffeePlaceLite> = when (category.name.lowercase()) {
        "popular" -> source.sortedByDescending { it.ratingCount ?: 0 }
        "rated"   -> source.sortedByDescending { it.rating ?: 0.0 }
        "nearby"  -> if (user == null) source else source.sortedBy {
            it.location?.let { ll -> locationUtil.distanceMeters(user, ll).toFloat() } ?: Float.MAX_VALUE
        }
        "dates"   -> source.sortedByDescending {
            (it.rating ?: 0.0) + ((it.ratingCount ?: 0) / 100f)
        }
        else      -> source
    }
}