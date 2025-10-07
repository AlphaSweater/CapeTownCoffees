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

package com.synaptix.capetowncoffees.ui.search

import android.os.Bundle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.GetCoffeePlaceSuggestionsUseCase
import com.synaptix.capetowncoffees.domain.usecase.search.SearchParamsUseCase
import com.synaptix.capetowncoffees.ui.common.viewmodel.Effect
import com.synaptix.capetowncoffees.ui.common.viewmodel.SimpleViewModel
import com.synaptix.capetowncoffees.ui.common.viewmodel.loadableState
import com.synaptix.capetowncoffees.ui.common.viewmodel.state
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getSuggestions: GetCoffeePlaceSuggestionsUseCase,
    private val searchParams: SearchParamsUseCase
) : SimpleViewModel() {

    /* ─────────────────── Inputs / State (UI-facing) ─────────────────── */
    private val seed = searchParams.current()

    val query   = state(seed.query.orEmpty())
    val radiusM = state(seed.radiusMeters)
    val strict  = state(seed.strictCoffeeOnly)
    val userLoc = state<LatLng?>(null)

    /* ─────────────────── Outputs ─────────────────── */
    val suggestions = loadableState<List<CoffeePlaceSuggestion>>()

    private var streamJob: Job? = null

    init {
        // Mirror UI state → shared use case
        viewModelScope.launch { query.flow.collect   { searchParams.setQuery(it) } }
        viewModelScope.launch { radiusM.flow.collect { searchParams.setRadius(it) } }
        viewModelScope.launch { strict.flow.collect  { searchParams.setStrict(it) } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(args: Bundle?) {
        super.start(args)
        streamJob?.cancel()
        streamJob = observeInto(
            target = suggestions,
            flow = combinedInput()
                .flatMapLatest { (params, loc) ->
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
            .debounce(220) // OK on a combined Flow; not applied directly to a StateFlow

    /* ─────────────── UI events ─────────────── */
    fun onQueryTyping(text: String)            = query.set(text)
    fun onRadiusChanged(m: Int)                = radiusM.set(m)
    fun onStrictChanged(only: Boolean)         = strict.set(only)
    fun setUserLocation(latLng: LatLng?)       = userLoc.set(latLng)

    fun onSuggestionClicked(item: CoffeePlaceSuggestion) {
        main {
            send(
                Effect.Navigate(
                    route = "search.openPlace",
                    args = Bundle().apply { putString("placeId", item.id) }
                )
            )
        }
    }
}

private data class ParamsAndLoc(
    val params: CoffeeSearchParameters,
    val loc: LatLng?
)