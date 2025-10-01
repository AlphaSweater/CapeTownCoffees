package com.synaptix.capetowncoffees.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.*
import com.synaptix.capetowncoffees.domain.model.*
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite.Companion.fields as liteFields
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull.Companion.fields as fullFields
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository.CoffeeSearchParams
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesApiRepository @Inject constructor(
    private val placesClient: PlacesClient
) : IPlacesApiRepository {

    // -----------------------------
    // 1. Search nearby coffee places
    // -----------------------------
    override suspend fun searchNearbyCoffeePlaces(
        params: CoffeeSearchParams
    ): Result<List<CoffeePlaceLite>> {
        return try {
            val request = buildNearbyRequest(params)
            val response = placesClient.searchNearby(request).await()

            val results = response.places.orEmpty()
                .mapNotNull { place ->
                    CoffeePlaceLite.fromPlace(place)
                }

            Result.success(results)
        } catch (e: Exception) {
            Timber.e(e, "Failed to search nearby coffee places")
            Result.failure(e)
        }
    }

    private fun buildNearbyRequest(params: CoffeeSearchParams): SearchNearbyRequest {
        val userLatLng = params.location
        val searchArea = CircularBounds.newInstance(userLatLng, params.radiusMeters.toDouble())

        return SearchNearbyRequest.builder(searchArea, liteFields)
            .apply {
                val includedPrimaries = if (params.strictCoffeeOnly)
                    IPlacesApiRepository.BaseSearchParams.strictPrimaryAllowed.toList()
                else
                    IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed.toList()

                val excludedPrimaries = IPlacesApiRepository.BaseSearchParams.primaryBlacklist.toList()

                setIncludedPrimaryTypes(includedPrimaries)
                setExcludedPrimaryTypes(excludedPrimaries)
                setRankPreference(
                    if (params.sortByDistance) SearchNearbyRequest.RankPreference.DISTANCE
                    else SearchNearbyRequest.RankPreference.POPULARITY
                )
                setMaxResultCount(params.maxResults.coerceAtMost(20))

                // Include coffee subtypes in lax mode
                if (!params.strictCoffeeOnly) {
                    val coffeeSubTypes = IPlacesApiRepository.BaseSearchParams.coffeeSubTypes.toList()
                    val excludedSubTypes = IPlacesApiRepository.BaseSearchParams.excludedSubtypes.toList()

                    setIncludedTypes(coffeeSubTypes)
                    if (excludedSubTypes.isNotEmpty()) setExcludedTypes(excludedSubTypes)
                }
            }.build()
    }

    // -----------------------------
    // 2. Get full coffee place details
    // -----------------------------
    override suspend fun getCoffeePlaceDetails(placeId: String): Result<CoffeePlaceFull> {
        return try {
            val request = FetchPlaceRequest.builder(placeId, fullFields).build()
            val response = placesClient.fetchPlace(request).await()
            val details = CoffeePlaceFull.fromPlace(response.place)

            Result.success(details)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch coffee place details for placeId=$placeId")
            Result.failure(e)
        }
    }

    // -----------------------------
    // 3. Get autocomplete suggestions
    // -----------------------------
    override suspend fun getSuggestions(query: String, location: LatLng?): Result<List<CoffeePlaceSuggestion>> {
        return try {
            val request = buildAutocompleteRequest(query, location)
            val response = placesClient.findAutocompletePredictions(request).await()

            val suggestions = response.autocompletePredictions
                .filter { prediction ->
                    val primaryType = prediction.types.firstOrNull()
                    val allTypes = prediction.types.orEmpty()
                    val allowedSubTypes = IPlacesApiRepository.BaseSearchParams.coffeeSubTypes.toList()

                    primaryType in IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed || allTypes.any { it in allowedSubTypes }
                }
                .take(5)
                .map { prediction ->
                    CoffeePlaceSuggestion(
                        id = prediction.placeId,
                        name = prediction.getPrimaryText(null).toString()
                    )
                }

            Result.success(suggestions)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch autocomplete suggestions for query='$query'")
            Result.failure(e)
        }
    }

    private fun buildAutocompleteRequest(query: String, location: LatLng?): FindAutocompletePredictionsRequest {
        val builder = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setCountries("ZA") // restrict to South Africa
            .setTypesFilter(IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed.toList())

        location?.let { builder.setOrigin(it) }

        return builder.build()
    }
}