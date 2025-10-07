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

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getSuggestions: GetCoffeePlaceSuggestionsUseCase
) : SimpleViewModel() {

    // ── Inputs/State ───────────────────────────────────────────────────────────
    val query   = state("")
    val radiusM = state(30_000)
    val strict  = state(true)
    val userLoc = state<LatLng?>(null)

    // Outputs
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
                    Timber.d("Fetching suggestions for: %s", params)
                    if (params.query.isNullOrBlank() || loc == null) {
                        flowOf(emptyList())
                    } else {
                        flow {
                            val result = getSuggestions(params, loc)
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
            val meters = rM.coerceIn(100, 50_000)
            val params = CoffeeSearchParameters.builder()
                .query(q.trim())
                .radiusMeters(meters)
                .maxResults(5)       // ≤5 suggestions
                .sortByDistance(true)
                .strictCoffeeOnly(isStrict)
                .build()
            ParamsAndLoc(params, loc)
        }
            .debounce(220)    // debounce typing/slider/toggle noise
            .distinctUntilChanged()        // avoid duplicate fetches when inputs unchanged

    // ── UI events ──────────────────────────────────────────────────────────────
    fun onQueryTyping(text: String) = query.set(text)
    fun onRadiusChanged(m: Int)     = radiusM.set(m)
    fun onStrictChanged(only: Boolean) = strict.set(only)
    fun setUserLocation(latLng: LatLng?) = userLoc.set(latLng)

    // Suggestion selected → navigate to detail with ONLY the placeId
    fun onSuggestionClicked(item: CoffeePlaceSuggestion) {
        val id = item.id
        main {
            send(
                Effect.Navigate(
                    route = "search.openPlace",
                    args = Bundle().apply { putString("placeId", id) }
                )
            )
        }
    }

    // Optional: keep IME Search behavior (if you still want to deep-link to detail using a query result)
    fun submitSearch() {
        val q   = query.value.trim()
        val loc = userLoc.value
        if (q.isEmpty() || loc == null) return

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