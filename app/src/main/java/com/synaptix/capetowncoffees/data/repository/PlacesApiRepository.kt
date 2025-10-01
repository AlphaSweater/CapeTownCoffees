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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesApiRepository @Inject constructor(
    private val placesClient: PlacesClient
) : IPlacesApiRepository {

    // ✅ 1. Search coffee places (Nearby Search - new SDK)
    override suspend fun searchNearbyCoffeePlaces(
        params: CoffeeSearchParams
    ): Result<List<CoffeePlaceLite>> {
        return try {
            val userLatLng = LatLng(params.location.latitude, params.location.longitude)
            val searchArea = CircularBounds.newInstance(userLatLng, params.radiusMeters.toDouble())

            // Use strict/relaxed allowed types and blacklist from interface
            val allowedPrimaries = if (params.strictCoffeeOnly) {
                IPlacesApiRepository.BaseSearchParams.strictPrimaryAllowed.toList()
            } else {
                IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed.toList()
            }
            val excludedPrimaries = IPlacesApiRepository.BaseSearchParams.primaryBlacklist.toList()

            // Use relevant coffee subtypes from interface for lax mode
            val coffeeSubTypes = IPlacesApiRepository.BaseSearchParams.coffeeSubTypes.toList()
            val excludedSubTypes = IPlacesApiRepository.BaseSearchParams.excludedSubtypes.toList()

            val requestBuilder = SearchNearbyRequest.builder(searchArea, liteFields)
                .setIncludedPrimaryTypes(allowedPrimaries)
                .setExcludedPrimaryTypes(excludedPrimaries)
                .setRankPreference(
                    if (params.sortByDistance) SearchNearbyRequest.RankPreference.DISTANCE
                    else SearchNearbyRequest.RankPreference.POPULARITY
                )
                .setMaxResultCount(params.maxResults.coerceAtMost(20))

            // In lax mode, also include relevant subtypes from interface
            if (!params.strictCoffeeOnly) {
                requestBuilder.setIncludedTypes(coffeeSubTypes)
                if (excludedSubTypes.isNotEmpty()) {
                    requestBuilder.setExcludedTypes(excludedSubTypes)
                }
            }

            val request = requestBuilder.build()
            val response = placesClient.searchNearby(request).await()

            val results = response.places.mapNotNull { place ->
                val primaryType = place.primaryType
                val allTypes = place.placeTypes ?: emptyList()
                if (IPlacesApiRepository.isCoffeeRelevant(primaryType, allTypes, params.strictCoffeeOnly)) {
                    CoffeePlaceLite.fromPlace(place)
                } else {
                    null
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
    ): Result<CoffeePlaceFull> {
        return try {
            val request = FetchPlaceRequest.builder(placeId, fullFields).build()
            val response = placesClient.fetchPlace(request).await()
            val details = CoffeePlaceFull.fromPlace(response.place)
            Result.success(details)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ 3. Get autocomplete suggestions (unchanged, still supported in new SDK)
    override suspend fun getSuggestions(
        query: String,
        location: LatLng?
    ): Result<List<CoffeePlaceSuggestion>> {
        return try {
            // Lax primary types from your BaseSearchParams
            val laxPrimaries = IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed.toList()
            val coffeeSubTypes = listOf("cafe", "coffee_shop") // allowed subtypes for lax matches

            val builder = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .setCountries("ZA") // restrict to South Africa
                .setTypesFilter(laxPrimaries) // always include lax primaries

            // Bias results toward user's location if provided
            location?.let { builder.setOrigin(it) }

            val request = builder.build()
            val response = placesClient.findAutocompletePredictions(request).await()

            val suggestions = response.autocompletePredictions
                .filter { prediction ->
                    val primaryType = prediction.types.firstOrNull() // single primary type
                    val allTypes = prediction.types ?: emptyList()

                    // Accept if primary type is lax, OR primary type is not lax but has coffee/cafe subtype
                    primaryType in laxPrimaries || allTypes.any { it in coffeeSubTypes }
                }
                .take(5) // limit to max 5 results
                .map { prediction ->
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
