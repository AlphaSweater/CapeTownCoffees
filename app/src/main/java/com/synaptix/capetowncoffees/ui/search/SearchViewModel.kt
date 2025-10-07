package com.synaptix.capetowncoffees.ui.search

import android.os.Bundle
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.GetCoffeePlaceSuggestionsUseCase
import com.synaptix.capetowncoffees.ui.common.viewmodel.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * SearchViewModel
 * - Holds query, radius, strict flags
 * - Debounces query and streams suggestions via use case
 * - Emits navigation effect on submit
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getSuggestions: GetCoffeePlaceSuggestionsUseCase
) : SimpleViewModel() {

    // ── Inputs/State ───────────────────────────────────────────────────────────
    val query    = state("")
    val radiusKm = state(5)        // kept for future filtering if you pass it down later
    val strict   = state(false)
    val userLoc  = state<LatLng?>(null)

    // Suggestions list (Loadable for skeleton/error)
    val suggestions = loadableState<List<CoffeePlaceSuggestion>>()

    private var streamJob: Job? = null


    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(args: Bundle?) {
        super.start(args)

        // (Optional) prefill:
        // args?.getString(HomeFragment.ARG_PREFILL_QUERY)?.let { query.set(it) }

        // Stream: query + filters + location → suggestions
        streamJob?.cancel()
        streamJob = observeInto(
            target = suggestions,
            flow = combinedInput()
                .flatMapLatest { (q, loc, _radius, _strict) ->
                    if (q.isBlank() || loc == null) {
                        flowOf(emptyList())
                    } else {
                        flow {
                            val result = getSuggestions(q, loc)
                            emit(result.getOrElse { throw it })
                        }
                    }
                }
                .distinctUntilChanged(),
            label = "place_suggestions"
        )
    }

    /** Combine inputs and debounce query typing. */
    @OptIn(FlowPreview::class)
    private fun combinedInput(): Flow<Quadruple<String, LatLng?, Int, Boolean>> =
        combine(query.flow, userLoc.flow, radiusKm.flow, strict.flow) { q, loc, r, s ->
            Quadruple(q, loc, r, s)
        }.debounce(220)

    // ── UI events ──────────────────────────────────────────────────────────────

    fun onQueryTyping(text: String) = query.set(text)
    fun onRadiusChanged(km: Int)    = radiusKm.set(km)
    fun onStrictChanged(only: Boolean) = strict.set(only)
    fun setUserLocation(latLng: LatLng?) = userLoc.set(latLng)

    fun submitSearch() {
        val q = query.value.trim()
        val loc = userLoc.value
        if (q.isEmpty() || loc == null) return

        // hop to Main and call the suspend send()
        main {
            send(
                Effect.Navigate(
                    route = "search.submit",
                    args = Bundle().apply {
                        putString("q", q)
                        putInt("radiusKm", radiusKm.value)
                        putBoolean("strictOnly", strict.value)
                        putDouble("lat", loc.latitude)
                        putDouble("lng", loc.longitude)
                    }
                )
            )
        }
    }

}

//** Tiny value holder since Kotlin doesn’t have a built-in Quadruple. */
private data class Quadruple<A,B,C,D>(val first: A, val second: B, val third: C, val fourth: D)