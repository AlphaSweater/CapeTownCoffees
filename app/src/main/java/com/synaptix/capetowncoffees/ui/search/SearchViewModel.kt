package com.synaptix.capetowncoffees.ui.search

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getSuggestions: GetCoffeePlaceSuggestionsUseCase,
    private val savedState: SavedStateHandle
) : SimpleViewModel() {

    /* ─────────────────── Persisted Keys ─────────────────── */
    private companion object {
        const val K_QUERY   = "search.query"
        const val K_RADIUS  = "search.radiusM"
        const val K_STRICT  = "search.strict"
    }

    /* ─────────────────── Inputs / State (cached) ─────────────────── */
    val query   = state(savedState.get<String>(K_QUERY) ?: "")
    val radiusM = state(savedState.get<Int>(K_RADIUS) ?: 30_000)
    val strict  = state(savedState.get<Boolean>(K_STRICT) ?: true)
    val userLoc = state<LatLng?>(null)

    /* ─────────────────── Outputs ─────────────────── */
    val suggestions = loadableState<List<CoffeePlaceSuggestion>>()

    private var streamJob: Job? = null

    init {
        // Mirror state → SavedStateHandle so it restores automatically
        viewModelScope.launch {
            query.flow.collect { savedState[K_QUERY] = it }
        }
        viewModelScope.launch {
            radiusM.flow.collect { savedState[K_RADIUS] = it }
        }
        viewModelScope.launch {
            strict.flow.collect { savedState[K_STRICT] = it }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(args: Bundle?) {
        super.start(args)
        streamJob?.cancel()
        streamJob = observeInto(
            target = suggestions,
            flow = combinedInput()
                .flatMapLatest { input ->
                    val (params, loc) = input
                    Timber.tag("CTC-Flow").d("Fetching suggestions for: %s", params)
                    if (params.query.isNullOrBlank() || loc == null) {
                        flowOf(emptyList())
                    } else {
                        flow {
                            val result = getSuggestions(params, loc)
                            Timber.tag("CTC-Flow").d("Got %s suggestions", result.getOrNull()?.size ?: "no")
                            emit(result.getOrElse { throw it })
                        }
                    }
                },
            label = "place_suggestions"
        )
    }

    /* Build CoffeeSearchParameters from UI state and emit when any input changes */
    @OptIn(FlowPreview::class)
    private fun combinedInput(): Flow<ParamsAndLoc> =
        combine(query.flow, radiusM.flow, strict.flow, userLoc.flow) { q, rM, isStrict, loc ->
            val meters = rM.coerceIn(100, 50_000)
            val params = CoffeeSearchParameters.builder()
                .query(q.trim())
                .radiusMeters(meters)
                .maxResults(10)
                .sortByDistance(true)
                .strictCoffeeOnly(isStrict)
                .build()
            ParamsAndLoc(params, loc)
        }
            .debounce(220)
            .distinctUntilChanged()

    /* ─────────────── UI events ─────────────── */
    fun onQueryTyping(text: String) = query.set(text)
    fun onRadiusChanged(m: Int) = radiusM.set(m)
    fun onStrictChanged(only: Boolean) = strict.set(only)
    fun setUserLocation(latLng: LatLng?) = userLoc.set(latLng)

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
}

private data class ParamsAndLoc(
    val params: CoffeeSearchParameters,
    val loc: LatLng?
)