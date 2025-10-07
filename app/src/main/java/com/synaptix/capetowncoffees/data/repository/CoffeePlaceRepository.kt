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
//* ChatGPT was used to clarify repository patterns, data source integration, and best
//practices for separating data access logic from UI components.
//* It also provided suggestions to improve maintainability and consistency.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.model.CoffeePlaceDTO
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoffeePlaceRepository @Inject constructor(
    firestore: FirebaseFirestore,
    private val placesApiRepository: IPlacesApiRepository
) : BaseRepository<CoffeePlaceDTO>(
    firestore = firestore,
    childCollection = "coffee_places"
), ICoffeePlaceRepository {

    override fun getType(): Class<CoffeePlaceDTO> = CoffeePlaceDTO::class.java

    // -----------------------------
    // Firestore management
    // -----------------------------
    override suspend fun checkCoffeePlaceExists(id: String): Result<Boolean> =
        getById(id).map { it != null }

    override suspend fun addCoffeePlace(
        coffeePlace: CoffeePlaceDTO,
        placeId: String?
    ): Result<String> =
        if (placeId != null) create(coffeePlace, id = placeId) else create(coffeePlace)

    override suspend fun deleteCoffeePlace(id: String): Result<Unit> = delete(id)

    // -----------------------------
    // Fetch details (Google + Firestore merge)
    // -----------------------------
    override suspend fun getCoffeePlaceDetails(placeId: String): Result<CoffeePlaceFull> = coroutineScope {
        val apiDeferred = async { placesApiRepository.getCoffeePlaceDetails(placeId) }
        val dbDeferred  = async { getById(placeId) }

        val apiResult = apiDeferred.await()
        val dbResult  = dbDeferred.await()

        // If API failed, just return that failure as-is.
        apiResult.mapCatching { googlePlace ->
            val appPlaceDTO = dbResult.getOrNull()
            if (appPlaceDTO != null) mergeFullPlace(googlePlace, appPlaceDTO) else googlePlace
        }
    }

    // -----------------------------
    // Search nearby (Google + Firestore merge)
    // -----------------------------
    override suspend fun searchNearbyCoffeePlaces(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceLite>> =
        placesApiRepository.searchNearbyCoffeePlaces(params, userLatLng)
            .onFailure { Timber.e(it, "Failed to search nearby coffee places (API)") }
            .mapCatching { apiPlaces ->
                val apiIds = apiPlaces.map { it.id }
                val localMap = getItemsByIds(apiIds)
                    .onFailure { Timber.w(it, "Local DB lookup failed, continuing with API results") }
                    .getOrElse { emptyList() }
                    .associateBy { it.id }

                apiPlaces.map { apiPlace ->
                    localMap[apiPlace.id]?.let { app -> mergeLitePlace(apiPlace, app) } ?: apiPlace
                }
            }

    // -----------------------------
    // Autocomplete suggestions (Google-only for now)
    // -----------------------------
    override suspend fun getSuggestions(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceSuggestion>> =
        placesApiRepository.getSuggestions(params, userLatLng)
            .onFailure { Timber.e(it, "Failed to get suggestions") }
            .map { it.orEmpty() }

    // -----------------------------
    // Merge helpers
    // -----------------------------
    private fun mergeFullPlace(apiPlace: CoffeePlaceFull, appData: CoffeePlaceDTO): CoffeePlaceFull {
        // TODO: merge relevant app data (ratings, review counts, flags, etc.)
        return apiPlace.copy(
            // keep API as source of truth by default; overlay appData fields as needed
        )
    }

    private fun mergeLitePlace(apiPlace: CoffeePlaceLite, appData: CoffeePlaceDTO): CoffeePlaceLite {
        // TODO: merge relevant app data (ratings, review counts, "liked"/"saved" flags, etc.)
        return apiPlace.copy(
            // keep API as source of truth by default; overlay appData fields as needed
        )
    }
}