package com.synaptix.capetowncoffees.data.repository

import com.google.android.gms.common.api.ApiException
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
                val includedPrimaries =
                    if (params.strictCoffeeOnly) IPlacesApiRepository.BaseSearchParams.strictPrimaryAllowed
                    else IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed

                val excludedPrimaries = IPlacesApiRepository.BaseSearchParams.primaryBlacklist

                setIncludedPrimaryTypes(includedPrimaries.toList())
                setExcludedPrimaryTypes(excludedPrimaries.toList())
                setRankPreference(
                    if (params.sortByDistance) SearchNearbyRequest.RankPreference.DISTANCE
                    else SearchNearbyRequest.RankPreference.POPULARITY
                )
                setMaxResultCount(params.maxResults.coerceAtMost(20))

                // Include coffee subtypes in relaxed mode
                if (!params.strictCoffeeOnly) {
                    val coffeeSubTypes = IPlacesApiRepository.BaseSearchParams.coffeeSubTypes
                    val excludedSubTypes = IPlacesApiRepository.BaseSearchParams.excludedSubtypes
                    setIncludedTypes(coffeeSubTypes.toList())
                    if (excludedSubTypes.isNotEmpty()) setExcludedTypes(excludedSubTypes.toList())
                }
            }
            .build()
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

    // -----------------------------
    // Autocomplete suggestions
    // -----------------------------
    override suspend fun getSuggestions(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceSuggestion>> {
        return try {
            val request = buildAutocompleteRequest(params, userLatLng)
            val response = placesClient.findAutocompletePredictions(request).await()

            val suggestions = response.autocompletePredictions
                .asSequence()
                .filter { isCoffeeRelatedPrediction(it, params) }
                .take(params.maxResults.coerceIn(1, 5))
                .map { prediction ->
                    CoffeePlaceSuggestion(
                        id = prediction.placeId,
                        name = prediction.getPrimaryText(null).toString(),
                        address = prediction.getSecondaryText(null).toString()
                    )
                }
                .toList()

            Result.success(suggestions)
        }  catch (e: ApiException) {
            Timber.e(e, "Places API error ${e.statusCode} during autocomplete")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get autocomplete suggestions")
            Result.failure(e)
        }
    }

    /**
     * Coffee relevance: allow relaxed primaries, coffee subtypes, or coffee-y names;
     * exclude obvious nightlife/alcoholic venues via excluded subtypes.
     */
    private fun isCoffeeRelatedPrediction(
        prediction: AutocompletePrediction,
        params: CoffeeSearchParameters
    ): Boolean {
        val allTypes = prediction.types.orEmpty().map { it.toString().lowercase() }

        val allowedPrimaries =
            if (params.strictCoffeeOnly)
                IPlacesApiRepository.BaseSearchParams.strictPrimaryAllowed
            else
                IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed

        var allowedSubTypes = emptySet<String>()
        var excludedSubTypes = emptySet<String>()
        // Include coffee subtypes in relaxed mode
        if (!params.strictCoffeeOnly) {
            allowedSubTypes = IPlacesApiRepository.BaseSearchParams.coffeeSubTypes
            excludedSubTypes = IPlacesApiRepository.BaseSearchParams.excludedSubtypes
        }

        val isExcluded = allTypes.any { it in excludedSubTypes }
        val hasAllowedPrimary = allTypes.any { it in allowedPrimaries }
        val hasCoffeeSubtype = allTypes.any { it in allowedSubTypes }

        return !isExcluded && (hasAllowedPrimary || hasCoffeeSubtype)
    }

    /**
     * Build an autocomplete request using CoffeeSearchParameters and user location.
     */
    private fun buildAutocompleteRequest(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): FindAutocompletePredictionsRequest {
        val includedPrimaries =
            if (params.strictCoffeeOnly) IPlacesApiRepository.BaseSearchParams.strictPrimaryAllowed
            else IPlacesApiRepository.BaseSearchParams.relaxedPrimaryAllowed

        val builder = FindAutocompletePredictionsRequest.builder()
            .setQuery(params.query ?: CoffeeSearchParameters.DEFAULT_QUERY)
            .setCountries("ZA") // restrict to South Africa
            .setOrigin(userLatLng)

        // Prefer explicit primary types to avoid internal mapping issues.
        if (includedPrimaries.isNotEmpty()) {
            // ✅ use the setter, keep ≤5 in your base lists
            builder.typesFilter = includedPrimaries.toList()
            Timber.d("Autocomplete primary types (strict=${params.strictCoffeeOnly}): $includedPrimaries")
        }

        val r = params.radiusMeters
        if (r <= 50_000) {
            // ✅ inclusive within radius
            builder.setLocationRestriction(CircularBounds.newInstance(userLatLng, r.toDouble()))
        } else {
            // fallback: bias capped to 50km (Autocomplete can't restrict >50km)
            builder.setLocationBias(CircularBounds.newInstance(userLatLng, 50_000.0))
        }

        return builder.build()
    }
}
