package com.synaptix.capetowncoffees.ui.home

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.SearchNearbyCoffeePlacesUseCase
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchNearbyCoffeePlacesUseCase: SearchNearbyCoffeePlacesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val _selectedCafe = MutableSharedFlow<Pair<CoffeePlaceLite, LatLng?>>(replay = 1)
    val selectedCafe: SharedFlow<Pair<CoffeePlaceLite, LatLng?>> = _selectedCafe.asSharedFlow()

    private var currentLocation: LatLng? = null
    private var allPlaces: List<CoffeePlaceLite> = emptyList()
    private var currentCategory: Category? = null

    fun setCurrentLocation(location: LatLng) {
        currentLocation = location
        loadData()
    }

    fun filterByCategory(category: Category) {
        currentCategory = category
        filterPlaces()
    }

    private fun filterPlaces() {
        try {
            val filteredPlaces = when (currentCategory?.name?.lowercase()) {
                "popular" -> allPlaces.sortedByDescending { it.ratingCount ?: 0 }
                "rated" -> allPlaces.sortedByDescending { it.rating ?: 0.0 }
                "nearby" -> {
                    currentLocation?.let { location ->
                        allPlaces.sortedBy { place ->
                            try {
                                val results = FloatArray(1)
                                Location.distanceBetween(
                                    location.latitude, location.longitude,
                                    place.location?.latitude ?: 0.0,
                                    place.location?.longitude ?: 0.0,
                                    results
                                )
                                results[0]
                            } catch (e: Exception) {
                                Timber.e(e, "Error calculating distance")
                                Float.MAX_VALUE
                            }
                        }
                    } ?: allPlaces
                }
                "new" -> allPlaces.sortedByDescending { it.id } // Assuming newer items have higher IDs
                else -> allPlaces // "All" or unknown category
            }

            // Always show top 3 highly rated places as featured
            val featuredPlaces = allPlaces
                .filter { (it.rating ?: 0.0) >= 4.0 }
                .take(3)

            _uiState.value = HomeUiState.Success(
                places = filteredPlaces,
                featuredPlaces = featuredPlaces
            )
        } catch (e: Exception) {
            Timber.e(e, "Error updating UI with filtered places")
            _uiState.value = HomeUiState.Error("Error displaying places. Please try again.")
        }
    }

    private fun updateUiWithFilteredPlaces() {
        filterPlaces()
    }
    
    fun setSelectedCafe(cafe: CoffeePlaceLite, location: LatLng?) {
        viewModelScope.launch {
            _selectedCafe.emit(cafe to location)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                _uiState.value = HomeUiState.Loading
                val latLng = currentLocation ?: throw IllegalStateException("Location not available")
                _uiState.value = HomeUiState.Success(
                    places = emptyList(),
                    featuredPlaces = emptyList()
                )
                val params = CoffeeSearchParameters.Builder()
                    .radiusMeters(5000)
                    .maxResults(10)
                    .build()
                allPlaces = runCatching {
                    searchNearbyCoffeePlacesUseCase(
                        params = params,
                        userLatLng = latLng
                    ).getOrThrow()
                }.fold(
                    onSuccess = { it },
                    onFailure = {
                        _uiState.value = HomeUiState.Error(
                            it.message ?: "Failed to load coffee places. Please check your internet connection."
                        )
                        emptyList()
                    }
                )
                if (allPlaces.isEmpty()) {
                    _uiState.value = HomeUiState.Error(
                        "No coffee places found nearby. Try moving to a different location."
                    )
                } else {
                    updateUiWithFilteredPlaces()
                }
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error occurred.")
            }
        }
    }

    sealed class HomeUiState {
        object Loading : HomeUiState()
        data class Error(val message: String) : HomeUiState() {
            // Helper function to check if the error is due to network issues
            fun isNetworkError(): Boolean {
                return message.contains("network", ignoreCase = true) ||
                        message.contains("internet", ignoreCase = true) ||
                        message.contains("connection", ignoreCase = true)
            }
        }

        data class Success(
            val places: List<CoffeePlaceLite>,
            val featuredPlaces: List<CoffeePlaceLite>
        ) : HomeUiState() {
            // Helper function to check if we have places to show
            fun hasPlaces(): Boolean = places.isNotEmpty()
        }
    }
}
