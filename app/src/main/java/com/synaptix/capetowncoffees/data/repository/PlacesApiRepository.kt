package com.synaptix.capetowncoffees.data.repository

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.LocationBias
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.net.kotlin.awaitSearchNearby
import com.google.android.libraries.places.ktx.api.net.awaitFetchPlace
import com.google.android.libraries.places.ktx.api.net.awaitFindAutocompletePredictions
import com.synaptix.capetowncoffees.domain.model.CoffeePlace
import com.synaptix.capetowncoffees.domain.model.CoffeePlace.Companion.fromPlace
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlacesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoffeePlacesRepository @Inject constructor(
    private val placesClient: PlacesClient
) : ICoffeePlacesRepository {

    override suspend fun searchNearbyCoffeePlaces(
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        includedTypes: List<String>,
        maxResults: Int
    ): Result<List<CoffeePlace>> = runCatching {
        val center = LatLng(lat, lng)
        val circular = CircularBounds.newInstance(center, radiusMeters.toDouble())
        val fields = ICoffeePlacesRepository.BASE_COFFEE_FIELDS
        val requestBuilder = SearchNearbyRequest.builder(circular, fields)
            .setMaxResultCount(maxResults)
        val request = requestBuilder.build()
        val response = placesClient.searchNearby(request).await()
        val placeList = response.places ?: emptyList()
        placeList.filterNotNull().map { place ->
            fromPlace(place)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun autocompleteCoffeePlace(
        query: String,
        locationBias: LocationBias?,
        countries: List<String>,
        includedTypes: List<String>
    ): Result<List<CoffeePlaceSuggestion>> = runCatching {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .apply { if (locationBias != null) setLocationBias(locationBias) }
            .apply { if (countries.isNotEmpty()) setCountries(countries) }
            .apply { setTypesFilter(includedTypes) }
            .build()
        val response = placesClient.awaitFindAutocompletePredictions(request)
        response.autocompletePredictions.map { p ->
            CoffeePlaceSuggestion(
                id = p.placeId,
                description = p.getFullText(null).toString()
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun fetchCoffeePlaceDetails(
        placeId: String,
        fields: List<Place.Field>
    ): Result<CoffeePlace> = runCatching {
        val response = placesClient.awaitFetchPlace(placeId, fields)
        val place = response.place
        fromPlace(place)
    }
}
