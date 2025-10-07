package com.synaptix.capetowncoffees.ui.search

import android.os.Bundle
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.GetCoffeePlaceSuggestionsUseCase
import com.synaptix.capetowncoffees.ui.common.viewmodel.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import timber.log.Timber
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
    val radiusM = state(30_000)
    val strict   = state(true)
    val userLoc  = state<LatLng?>(null)

    // Output
    val suggestions = loadableState<List<CoffeePlaceSuggestion>>()

    private var streamJob: Job? = null


    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(args: Bundle?) {
        super.start(args)

        // Stream: query + filters + location → suggestions
        streamJob?.cancel()
        streamJob = observeInto(
            target = suggestions,
            flow = combinedInput()
                .flatMapLatest { input ->
                    val (params, loc) = input
                    Timber.d("Fetching suggestions for: $params")
                    if (params.query.isNullOrBlank() || loc == null) {
                        flowOf(emptyList())
                    } else {
                        flow {
                            Timber.d("Fetching suggestions for: $params")
                            val result = getSuggestions(params, loc)
                            Timber.d("Suggestions: $result")
                            emit(result.getOrElse { throw it })
                        }
                    }
                },
            label = "place_suggestions"
        )
    }

    // Build CoffeeSearchParameters from UI state and emit when any input changes
    @OptIn(FlowPreview::class)
    private fun combinedInput(): Flow<ParamsAndLoc> =
        combine(query.flow, radiusM.flow, strict.flow, userLoc.flow) { q, rM, isStrict, loc ->
            val meters = (rM ).coerceIn(100, 50_000)
            val params = CoffeeSearchParameters.builder()
                .query(q.trim())
                .radiusMeters(meters)
                .maxResults(5)          // UI wants ≤5 suggestions
                .sortByDistance(true)   // good default for suggestions
                .strictCoffeeOnly(isStrict)
                .build()
            ParamsAndLoc(params, loc)
        }
            .debounce(220)                   // debounce typing
            .distinctUntilChanged()          // uses data-class equality

    // ── UI events ──────────────────────────────────────────────────────────────

    fun onQueryTyping(text: String) = query.set(text)
    fun onRadiusChanged(m: Int) = radiusM.set(m)
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
                        putInt("radiusM", radiusM.value)
                        putBoolean("strictOnly", strict.value)
                        putDouble("lat", loc.latitude)
                        putDouble("lng", loc.longitude)
                    }
                )
            )
        }
    }

}

private data class ParamsAndLoc(
    val params: CoffeeSearchParameters,
    val loc: LatLng?
)