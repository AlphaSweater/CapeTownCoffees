package com.synaptix.capetowncoffees.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.*
import com.synaptix.capetowncoffees.domain.model.*
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository.CoffeeSearchParams
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class PlacesApiRepository @Inject constructor(
    private val placesClient: PlacesClient
) : IPlacesApiRepository {

    // ✅ 1. Search coffee places (Lite Models)
    override suspend fun searchCoffeePlaces(
        params: CoffeeSearchParams
    ): Result<List<CoffeePlaceLite>> {
        return try {
            val locationBias: LocationBias = RectangularBounds.newInstance(
                LatLng(params.location.latitude - 0.01, params.location.longitude - 0.01),
                LatLng(params.location.latitude + 0.01, params.location.longitude + 0.01)
            )
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(params.query ?: "coffee")
                .setLocationBias(locationBias)
                .setCountries("ZA")
                .setTypesFilter(listOf("establishment"))
                .build()
            val response = placesClient.findAutocompletePredictions(request).await()
            // Fetch Place details for each prediction and map using CoffeePlaceLite.fromPlace
            val results = response.autocompletePredictions.take(params.maxResults).mapNotNull { prediction ->
                try {
                    val placeRequest = FetchPlaceRequest.builder(
                        prediction.placeId,
                        CoffeePlaceLite.fields
                    ).build()
                    val placeResponse = placesClient.fetchPlace(placeRequest).await()
                    CoffeePlaceLite.fromPlace(placeResponse.place)
                } catch (e: Exception) {
                    null // skip failed fetches
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ 2. Get full coffee place details
    override suspend fun getCoffeePlaceDetails(
        placeId: String,
        fields: List<Place.Field>
    ): Result<CoffeePlaceFull> {
        return try {
            val request = FetchPlaceRequest.builder(placeId, fields).build()
            val response = placesClient.fetchPlace(request).await()
            val place = response.place
            val details = CoffeePlaceFull.fromPlace(place)
            Result.success(details)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ 3. Get autocomplete suggestions
    override suspend fun getSuggestions(
        query: String,
        location: LatLng?,
        maxResults: Int
    ): Result<List<CoffeePlaceSuggestion>> {
        return try {
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .setTypesFilter(listOf("establishment"))
                .setCountries("ZA")
                .build()
            val response = placesClient.findAutocompletePredictions(request).await()
            val suggestions = response.autocompletePredictions.take(maxResults).map { prediction ->
                CoffeePlaceSuggestion(
                    id = prediction.placeId,
                    name = prediction.getPrimaryText(null).toString()
                )
            }
            Result.success(suggestions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}