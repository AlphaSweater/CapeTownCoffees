package com.synaptix.capetowncoffees.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.synaptix.capetowncoffees.data.mapper.toDomainList
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.model.GooglePlaceReview
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull.Companion.fields as fullFields
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite.Companion.fields as liteFields

@Singleton
class PlacesApiRepository @Inject constructor(
    private val placesClient: PlacesClient
) : IPlacesApiRepository {

    // -----------------------------
    // Search nearby coffee places
    // -----------------------------
    // Search nearby coffee places using the user's current location.
    override suspend fun searchNearbyCoffeePlaces(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceLite>> {
        return try {
            val request = buildNearbyRequest(params, userLatLng)
            val response = placesClient.searchNearby(request).await()
            val results = response.places.orEmpty()
                .map { place -> CoffeePlaceLite.fromPlace(place) }
            Result.success(results)
        } catch (e: Exception) {
            Timber.e(e, "Failed to search nearby coffee places")
            Result.failure(e)
        }
    }

    /**
     * Build a SearchNearbyRequest for coffee places using user's location.
     */
    private fun buildNearbyRequest(params: CoffeeSearchParameters, userLatLng: LatLng): SearchNearbyRequest {
        val searchArea = CircularBounds.newInstance(userLatLng, params.radiusMeters.toDouble())
        return SearchNearbyRequest.builder(searchArea, liteFields)
            .apply {
                val includedPrimaries = if (params.strictCoffeeOnly)
                    IPlacesApiRepository.BaseSearchParams.strictPrimaryAllowed
                else
                    IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed

                val excludedPrimaries = IPlacesApiRepository.BaseSearchParams.primaryBlacklist

                setIncludedPrimaryTypes(includedPrimaries.toList())
                setExcludedPrimaryTypes(excludedPrimaries.toList())
                setRankPreference(
                    if (params.sortByDistance) SearchNearbyRequest.RankPreference.DISTANCE
                    else SearchNearbyRequest.RankPreference.POPULARITY
                )
                setMaxResultCount(params.maxResults.coerceAtMost(20))

                // Include coffee subtypes in lax mode
                if (!params.strictCoffeeOnly) {
                    val coffeeSubTypes = IPlacesApiRepository.BaseSearchParams.coffeeSubTypes
                    val excludedSubTypes = IPlacesApiRepository.BaseSearchParams.excludedSubtypes
                    setIncludedTypes(coffeeSubTypes.toList())
                    if (excludedSubTypes.isNotEmpty()) setExcludedTypes(excludedSubTypes.toList())
                }
            }.build()
    }

    // -----------------------------
    // Get full coffee place details
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
    // Get coffee place reviews
    // -----------------------------
    override suspend fun getCoffeePlaceReviews(placeId: String): Result<List<GooglePlaceReview>> {
        return try {
            val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.REVIEWS)).build()
            val response = placesClient.fetchPlace(request).await()
            val reviews = response.place.reviews?.toDomainList(placeId) ?: emptyList()
            Result.success(reviews)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch reviews for placeId=$placeId")
            Result.failure(e)
        }
    }

    // TODO: Make sure results somewhat follow Base params
    // -----------------------------
    // Autocomplete suggestions
    // -----------------------------
    // Get autocomplete suggestions using the user's current location.
    override suspend fun getSuggestions(query: String, userLatLng: LatLng): Result<List<CoffeePlaceSuggestion>> {
        return try {
            val request = buildAutocompleteRequest(query, userLatLng)
            val response = placesClient.findAutocompletePredictions(request).await()
            val suggestions = response.autocompletePredictions
                .filter(::isCoffeeRelatedPrediction)
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

    /**
     * Helper to determine if a prediction is coffee-related.
     */
    private fun isCoffeeRelatedPrediction(prediction: AutocompletePrediction): Boolean {
        val primaryType = prediction.types.firstOrNull()
        val allTypes = prediction.types.orEmpty()
        val allowedSubTypes = IPlacesApiRepository.BaseSearchParams.coffeeSubTypes
        return primaryType in IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed ||
                allTypes.any { it in allowedSubTypes }
    }

    /**
     * Build an autocomplete request for coffee places using user's location if available.
     */
    private fun buildAutocompleteRequest(query: String, location: LatLng?): FindAutocompletePredictionsRequest {
        val builder = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setCountries("ZA") // restrict to South Africa
            .setTypesFilter(IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed.toList())

        location?.let { builder.setOrigin(it) }

        return builder.build()
    }
}