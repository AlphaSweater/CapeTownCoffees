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

package com.synaptix.capetowncoffees.ui.home

import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.SearchNearbyCoffeePlacesUseCase
import com.synaptix.capetowncoffees.domain.usecase.search.SearchParamsUseCase
import com.synaptix.capetowncoffees.ui.common.viewmodel.Effect
import com.synaptix.capetowncoffees.ui.common.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui.common.viewmodel.SimpleViewModel
import com.synaptix.capetowncoffees.ui.common.viewmodel.loadableState
import com.synaptix.capetowncoffees.ui.common.viewmodel.state
import com.synaptix.capetowncoffees.ui.common.viewmodel.toUiError
import com.synaptix.capetowncoffees.util.LocationFormattingUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchNearby: SearchNearbyCoffeePlacesUseCase,
    private val searchParams: SearchParamsUseCase
) : SimpleViewModel() {

    // ─────────── Config ───────────
    // Tunables for caching, sorting, and section sizes.
    private companion object {
        private const val MIN_REQUERY_DISTANCE_M = 20.0
        private const val TTL_MILLIS = 10 * 60 * 1000L

        private const val NEAR_MAX_RESULTS = 32
        private const val NEAR_SORT_BY_DISTANCE = true

        private const val FEATURED_MAX_RESULTS = 20
        private const val FEATURED_SORT_BY_DISTANCE = false

        private val DEFAULT_CATEGORIES = listOf(
            Category(1, "All", R.drawable.ic_ctc_medal),
            Category(2, "Popular", R.drawable.ic_ctc_star),
            Category(3, "Pet Friendly", R.drawable.ic_ctc_pet),
            Category(4, "Nearby", R.drawable.ic_ctc_location),
            Category(5, "Dates", R.drawable.ic_ctc_heart)
        )
    }

    // ─────────── UI State ───────────
    // Single source of truth for simple flags and selection.
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

    // Lists bound by the Fragment; Loadable wraps loading/error/data.
    val nearMe = loadableState<List<CoffeePlaceLite>>()
    val featured = loadableState<List<CoffeePlaceLite>>()

    // ─────────── Caches ───────────
    // We keep raw results + the center and a timestamp to gate re-queries.
    private data class CacheEntry(
        val baseItems: List<CoffeePlaceLite>,
        val center: LatLng,
        val timestamp: Long
    )
    private var nearCache: CacheEntry? = null
    private var featuredCache: CacheEntry? = null

    // ─────────── In-flight Jobs ───────────
    // Each section cancels its previous query before starting another.
    private var nearJob: Job? = null
    private var featuredJob: Job? = null

    // ─────────── Public API ───────────
    // Location arrival paints from cache (if valid) and kicks refreshes independently.
    fun onUserLocation(loc: LatLng) {
        ui.update { it.copy(currentLocation = loc) }

        if (hasFreshEnough(nearCache, loc)) {
            nearCache?.let { cache ->
                nearMe.data(filterByCategory(ui.value.selectedCategory, cache.baseItems, loc))
            }
        }
        if (hasFreshEnough(featuredCache, loc)) {
            featuredCache?.let { cache -> featured.data(cache.baseItems) }
        }

        if (!hasFreshEnough(nearCache, loc)) refreshNear(force = true)
        if (!hasFreshEnough(featuredCache, loc)) refreshFeatured(force = true)
    }

    // Category selection filters the NEAR list locally; Featured stays as-is.
    fun onCategorySelected(category: Category) {
        ui.update { it.copy(selectedCategory = category) }
        val loc = ui.value.currentLocation ?: return
        nearCache?.let { cache ->
            nearMe.data(filterByCategory(category, cache.baseItems, loc))
        }
    }

    // Pull-to-refresh always refetches both sections.
    fun pullToRefresh() {
        refreshNear(force = true)
        refreshFeatured(force = true)
    }

    // External refresh entry point; respects force flag.
    fun refresh(force: Boolean = false) {
        refreshNear(force)
        refreshFeatured(force)
    }

    // Detects changes in shared search params (e.g., radius/strict) and remembers them.
    fun shouldRefreshForSearchParamsChange(): Boolean {
        val keyNow = paramsKeyFromUseCase()
        val changed = lastAppliedKey != keyNow
        if (changed) lastAppliedKey = keyNow
        return changed
    }

    // ─────────── Refresh: NEAR ───────────
    // Uses current location + shared params; filters by current category locally.
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

        if (nearMe.value !is Loadable.Data) nearMe.loading()
        ui.update { it.copy(isRefreshingNear = true) }

        io {
            nearJob?.cancelAndJoin()
            nearJob = launch {
                val now = System.currentTimeMillis()
                val params = searchParams.forNear(
                    maxResults = NEAR_MAX_RESULTS,
                    sortByDistance = NEAR_SORT_BY_DISTANCE
                )

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

    // ─────────── Refresh: FEATURED ───────────
    // Uses current location + shared params; renders as returned (server-driven).
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
                val params = searchParams.forFeatured(
                    maxResults = FEATURED_MAX_RESULTS,
                    sortByDistance = FEATURED_SORT_BY_DISTANCE
                )

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

    // ─────────── Helpers & Policy ───────────
    // Cache is valid only if not expired and user hasn't moved too far.
    private fun hasFreshEnough(entry: CacheEntry?, loc: LatLng): Boolean {
        if (entry == null) return false
        val fresh = (System.currentTimeMillis() - entry.timestamp) <= TTL_MILLIS
        val moved = distance(entry.center, loc) >= MIN_REQUERY_DISTANCE_M
        return fresh && !moved
    }

    private fun distance(a: LatLng, b: LatLng): Double =
        LocationFormattingUtil.distanceMeters(a, b).toDouble()

    // Local sorting/filtering applied to NEAR section based on the selected category.
    private fun filterByCategory(
        category: Category,
        source: List<CoffeePlaceLite>,
        user: LatLng?
    ): List<CoffeePlaceLite> = when (category.name.lowercase()) {
        "popular" -> source.sortedByDescending { it.ratingCount ?: 0 }
        "rated"   -> source.sortedByDescending { it.rating ?: 0.0 }
        "nearby"  -> if (user == null) source else source.sortedBy {
            it.location?.let { ll -> LocationFormattingUtil.distanceMeters(user, ll) } ?: Float.MAX_VALUE
        }
        "dates"   -> source.sortedByDescending {
            (it.rating ?: 0.0) + ((it.ratingCount ?: 0) / 100f)
        }
        else      -> source
    }

    // Track the last applied (radius, strict) to decide if we need a refetch.
    private data class ParamsKey(val radiusM: Int, val strict: Boolean)
    private var lastAppliedKey: ParamsKey? = null
    private fun paramsKeyFromUseCase(): ParamsKey {
        val p = searchParams.current()
        return ParamsKey(p.radiusMeters, p.strictCoffeeOnly)
    }
}
