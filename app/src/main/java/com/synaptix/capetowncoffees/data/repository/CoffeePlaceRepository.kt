package com.synaptix.capetowncoffees.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.mapper.CoffeePlaceMapper
import com.synaptix.capetowncoffees.data.model.CoffeePlaceDTO
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// Temporary global flag used to simulate/drive offline mode behaviour.
// In future this can be replaced by a proper network / settings service.
var offlineModeEnabled: Boolean = false

@Singleton
class CoffeePlaceRepository @Inject constructor(
    firestore: FirebaseFirestore,
    private val placesApiRepository: IPlacesApiRepository,
    private val coffeePlaceUtilsUseCase: Lazy<CoffeePlaceUtilsUseCase>
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
    // Fetch details (offline-aware: DB or API + cache + merge)
    // -----------------------------
    override suspend fun getCoffeePlaceDetails(placeId: String): Result<CoffeePlaceFull> {
        // ───── OFFLINE MODE: DB ONLY ─────
        if (offlineModeEnabled) {
            Timber.d("Offline mode enabled, loading coffee place from DB only (id=$placeId)")

            val dbResult = getById(placeId)
            dbResult.onFailure {
                Timber.e(it, "Offline: failed to read coffee place from DB (id=$placeId)")
            }

            val dto = dbResult.getOrNull()
            return if (dto != null) {
                val fullFromCache = CoffeePlaceMapper.toFull(dto)
                Result.success(fullFromCache)
            } else {
                Result.failure(
                    NoSuchElementException("Coffee place not found in offline cache (id=$placeId)")
                )
            }
        }

        // ───── ONLINE MODE: API FIRST, THEN DB + CACHE + MERGE ─────
        Timber.d("Online mode: fetching coffee place from API (id=$placeId)")

        val apiResult = placesApiRepository.getCoffeePlaceDetails(placeId)
        if (apiResult.isFailure) {
            Timber.e(
                apiResult.exceptionOrNull(),
                "Failed to fetch coffee place from API (id=$placeId)"
            )
            return apiResult
        }

        val apiPlace = apiResult.getOrThrow()

        // Check DB for stored app data (ratings, in-app reviews, etc.)
        val dbResult = getById(placeId)
        dbResult.onFailure {
            Timber.w(it, "Failed to read coffee place from DB (id=$placeId)")
        }

        val appPlaceDTO = dbResult.getOrNull()

        return if (appPlaceDTO != null) {
            // Already have local data → merge API + app data
            val appPlace = CoffeePlaceMapper.toFull(appPlaceDTO)
            Timber.d("Merging API and DB data for coffee place (id=$placeId)")
            val merged = mergeFullPlace(apiPlace, appPlace)

            // We *can* still ensure cache is up to date, but do it async.
            ensureCoffeePlaceCachedAsync(placeId, sourceFull = merged)

            Result.success(merged)
        } else {
            // Not in DB yet → start async caching, but don't wait for it.
            Timber.d("Coffee place not cached yet. Start Caching of API result (id=$placeId)")

            ensureCoffeePlaceCachedAsync(placeId, sourceFull = apiPlace)

            // return the API result as-is (API is source of truth here)
            apiResult
        }
    }

    // -----------------------------
    // Search nearby (Google + Firestore merge + async caching)
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
                    .onFailure {
                        Timber.w(
                            it,
                            "Local DB lookup failed, continuing with API results (nearby search)"
                        )
                    }
                    .getOrElse { emptyList() }
                    .associateBy { it.id }

                // Fire-and-forget caching for ALL nearby places
                apiPlaces.forEach { lite ->
                    ensureCoffeePlaceCachedAsync(lite.id)
                }

                // Merge any local app data into the lite models
                apiPlaces.map { apiPlace ->
                    localMap[apiPlace.id]?.let { app ->
                        mergeLitePlace(apiPlace, CoffeePlaceMapper.toLite(app))
                    } ?: apiPlace
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
            .map { it }

    // -----------------------------
    // Shared caching helper (async / fire-and-forget)
    // -----------------------------
    private fun ensureCoffeePlaceCachedAsync(
        placeId: String,
        sourceFull: CoffeePlaceFull? = null
    ) {
        // Fire-and-forget background caching on IO dispatcher.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1) Check if we already have this place
                val existingResult = getById(placeId)
                val existing = existingResult.getOrNull()
                if (existing != null) {
                    Timber.d("ensureCoffeePlaceCachedAsync: place already cached (id=$placeId)")
                    return@launch
                }

                // 2) Determine which full model to use for caching
                val fullPlace = sourceFull ?: run {
                    Timber.d("ensureCoffeePlaceCachedAsync: fetching full details for caching (id=$placeId)")
                    val apiResult = placesApiRepository.getCoffeePlaceDetails(placeId)
                    if (apiResult.isFailure) {
                        Timber.w(
                            apiResult.exceptionOrNull(),
                            "ensureCoffeePlaceCachedAsync: failed to fetch details from API (id=$placeId)"
                        )
                        return@launch
                    }
                    apiResult.getOrThrow()
                }

                // 3) Fetch image URL
                if (!fullPlace.images.isNullOrEmpty()) {
                    Timber.d("ensureCoffeePlaceCachedAsync: fetching image URL for caching (id=$placeId)")
                    val uri = coffeePlaceUtilsUseCase.get().getPhotoUriFromMetadata(fullPlace.images.first())

                    fullPlace.cachedImageUrl = uri.toString()
                }


                // 4) Convert and write to Firestore
                val dtoToCache = CoffeePlaceDTO.fromFull(fullPlace)

                addCoffeePlace(dtoToCache, placeId = fullPlace.id)
                    .onSuccess {
                        Timber.d("ensureCoffeePlaceCachedAsync: cached coffee place (id=$placeId)")
                    }
                    .onFailure {
                        Timber.w(it, "ensureCoffeePlaceCachedAsync: failed to cache coffee place (id=$placeId)")
                    }
            } catch (t: Throwable) {
                Timber.w(t, "ensureCoffeePlaceCachedAsync: unexpected error (id=$placeId)")
            }
        }
    }

    // -----------------------------
    // Merge helpers
    // -----------------------------
    private fun mergeFullPlace(apiPlace: CoffeePlaceFull, appPlace: CoffeePlaceFull): CoffeePlaceFull {
        return apiPlace.copy(
            cachedImageUrl = appPlace.cachedImageUrl,
            appRating = appPlace.appRating,
            appRatingCount = appPlace.appRatingCount,
            isCached = true
        )
    }

    private fun mergeLitePlace(apiPlace: CoffeePlaceLite, appPlace: CoffeePlaceLite): CoffeePlaceLite {
        return apiPlace.copy(
            cachedImageUrl = appPlace.cachedImageUrl,
            appRating = appPlace.appRating,
            appRatingCount = appPlace.appRatingCount,
            isCached = true
        )
    }
}
