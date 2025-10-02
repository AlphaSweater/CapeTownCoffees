package com.synaptix.capetowncoffees.ui.home

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val placesApiRepository: IPlacesApiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentLocation: LatLng? = null
    private var allPlaces: List<CoffeePlaceLite> = emptyList()
    private var currentCategory: Category? = null

    fun setCurrentLocation(location: LatLng) {
        currentLocation = location
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                _uiState.value = HomeUiState.Loading

                val latLng = currentLocation ?: throw IllegalStateException("Location not available")
                val params = IPlacesApiRepository.CoffeeSearchParams(
                    radiusMeters = 5000, // 5km radius
                    maxResults = 10
                )

                // Get nearby coffee places
                allPlaces = runCatching {
                    placesApiRepository.searchNearbyCoffeePlaces(
                        params = params,
                        userLatLng = latLng
                    ).getOrThrow()
                }.fold(
                    onSuccess = { it },
                    onFailure = {
                        _uiState.value = HomeUiState.Error(
                            it.message ?: "Failed to load coffee places"
                        )
                        emptyList()
                    }
                )

                updateUiWithFilteredPlaces()
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load data")
            }
        }
    }

    sealed class HomeUiState {
        object Loading : HomeUiState()
        data class Error(val message: String) : HomeUiState()
        data class Success(
            val places: List<CoffeePlaceLite>,
            val featuredPlaces: List<CoffeePlaceLite>
        ) : HomeUiState()
    }
    
    fun filterByCategory(category: Category) {
        currentCategory = category
        updateUiWithFilteredPlaces()
    }
    
    private fun updateUiWithFilteredPlaces() {
        val filteredPlaces = when (currentCategory?.name?.lowercase()) {
            "popular" -> allPlaces.sortedByDescending { it.ratingCount ?: 0 }
            "rated" -> allPlaces.sortedByDescending { it.rating ?: 0.0 }
            "nearby" -> {
                currentLocation?.let { location ->
                    allPlaces.sortedBy { place ->
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude, location.longitude,
                            place.location?.latitude ?: 0.0,
                            place.location?.longitude ?: 0.0,
                            results
                        )
                        results[0]
                    }
                } ?: allPlaces
            }
            "new" -> allPlaces.sortedByDescending { it.id } // Assuming newer items have higher IDs
            else -> allPlaces // "All" or unknown category
        }
        
        _uiState.value = HomeUiState.Success(
            places = filteredPlaces,
            featuredPlaces = allPlaces.take(3) // Keep featured places as is
        )
    }
}

