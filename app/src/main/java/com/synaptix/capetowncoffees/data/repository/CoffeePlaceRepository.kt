package com.synaptix.capetowncoffees.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.model.CoffeePlaceDTO
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlacesRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoffeePlaceRepository @Inject constructor(
    firestore: FirebaseFirestore,
    private val placesApiRepository: IPlacesApiRepository // depend on the interface, not impl
) : BaseRepository<CoffeePlaceDTO>(
    firestore = firestore,
    childCollection = "coffee_places"
), ICoffeePlacesRepository {

    override fun getType(): Class<CoffeePlaceDTO> = CoffeePlaceDTO::class.java

    // -----------------------------
    // Firestore management
    // -----------------------------
    override suspend fun addCoffeePlace(coffeePlace: CoffeePlaceDTO, placeId: String?): Result<String> {
        return try {
            if (placeId != null){
                create(coffeePlace, id = placeId)
            } else {
                create(coffeePlace)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteCoffeePlace(id: String): Result<Unit> {
        return try {
            delete(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -----------------------------
    // Fetch details (Google + Firestore merge)
    // -----------------------------
    override suspend fun getCoffeePlaceDetails(placeId: String): Result<CoffeePlaceFull> = coroutineScope {
        val apiDeferred = async { placesApiRepository.getCoffeePlaceDetails(placeId) }
        val dbDeferred = async { getById(placeId) }

        val apiResult = apiDeferred.await()
        val dbResult = dbDeferred.await()

        if (apiResult.isFailure) return@coroutineScope apiResult
        val googlePlace = apiResult.getOrNull() ?: return@coroutineScope Result.failure(Exception("No place found from API"))
        val appPlaceDTO = dbResult.getOrNull()

        val mergedPlace = if (appPlaceDTO != null) {
            mergeFullPlace(googlePlace, appPlaceDTO)
        } else googlePlace

        Result.success(mergedPlace)
    }

    // -----------------------------
    // Search nearby (Google + Firestore merge)
    // -----------------------------
    override suspend fun searchNearbyCoffeePlaces(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceLite>> {
        return try {
            // Step 1: Fetch from Google API
            val apiResult = placesApiRepository.searchNearbyCoffeePlaces(params, userLatLng)
            if (apiResult.isFailure) return apiResult
            val apiPlaces = apiResult.getOrNull().orEmpty()

            // Step 2: Collect API place IDs and fetch all matching CoffeePlaces in our DB
            val apiIds = apiPlaces.map { it.id }
            val localResult = getItemsByIds(apiIds)
            val localPlaces = localResult.getOrNull().orEmpty()
            val localMap = localPlaces.associateBy { it.id }

            // Step 3: Merge API places with local data if available
            val mergedPlaces = apiPlaces.map { apiPlace ->
                val appData = localMap[apiPlace.id]
                if (appData != null) mergeLitePlace(apiPlace, appData) else apiPlace
            }

            Result.success(mergedPlaces)
        } catch (e: Exception) {
            Timber.e(e, "Failed to search nearby coffee places")
            Result.failure(e)
        }
    }

    // -----------------------------
    // Autocomplete suggestions (Google-only for now)
    // -----------------------------
    override suspend fun getSuggestions(
        query: String,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceSuggestion>> {
        return try {
            val apiResult = placesApiRepository.getSuggestions(query, userLatLng)
            if (apiResult.isFailure) return apiResult
            val apiSuggestions = apiResult.getOrNull().orEmpty()

            Result.success(apiSuggestions)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get suggestions")
            Result.failure(e)
        }
    }

    // -----------------------------
    // Merge helpers
    // -----------------------------
    private fun mergeFullPlace(apiPlace: CoffeePlaceFull, appData: CoffeePlaceDTO): CoffeePlaceFull {
        // TODO: merge any relevant app data fields such as ratings, review counts and reviews
        return apiPlace.copy(
            // keeps api data by default
        )
    }

    private fun mergeLitePlace(apiPlace: CoffeePlaceLite, appData: CoffeePlaceDTO): CoffeePlaceLite {
        // TODO: merge any relevant app data fields such as ratings and review counts
        return apiPlace.copy(
            // keeps api data by default
        )
    }
}
