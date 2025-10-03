package com.synaptix.capetowncoffees.ui.savedLists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CafeDetailViewModel @Inject constructor(
    private val placesRepository: IPlacesApiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CafeDetailUiState>(CafeDetailUiState.Loading)
    val uiState: StateFlow<CafeDetailUiState> = _uiState.asStateFlow()

    private val _selectedLocation = MutableStateFlow<LatLng?>(null)
    val selectedLocation: StateFlow<LatLng?> = _selectedLocation.asStateFlow()

    /**
     * Loads full details for a coffee place, either from a lite object or by ID
     */
    fun loadCoffeePlace(placeId: String? = null, litePlace: CoffeePlaceLite? = null) {
        viewModelScope.launch {
            _uiState.value = CafeDetailUiState.Loading
            
            try {
                val result = when {
                    litePlace != null -> {
                        // If we have a lite place, use its ID to fetch full details
                        placesRepository.getCoffeePlaceDetails(litePlace.id ?: "")
                    }
                    !placeId.isNullOrEmpty() -> {
                        // If we only have an ID, use that to fetch details
                        placesRepository.getCoffeePlaceDetails(placeId)
                    }
                    else -> {
                        throw IllegalArgumentException("Either placeId or litePlace must be provided")
                    }
                }
                
                result.fold(
                    onSuccess = { fullPlace ->
                        _uiState.value = CafeDetailUiState.Success(fullPlace)
                        // If we have a lite place with location, use that as fallback
                        _selectedLocation.value = litePlace?.location
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to load coffee place details")
                        _uiState.value = CafeDetailUiState.Error(
                            message = "Failed to load coffee place details: ${exception.message}",
                            throwable = exception
                        )
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error in loadCoffeePlace")
                _uiState.value = CafeDetailUiState.Error(
                    message = "An unexpected error occurred: ${e.message}",
                    throwable = e
                )
            }
        }
    }

    /**
     * Updates the selected location (e.g., from map interaction)
     */
    fun updateSelectedLocation(location: LatLng) {
        _selectedLocation.value = location
    }

    /**
     * Clears the current coffee place and any related state
     */
    fun clearSelectedCafe() {
        _uiState.value = CafeDetailUiState.Loading
        _selectedLocation.value = null
    }
}

/**
 * UI state for the CafeDetail screen
 */
sealed class CafeDetailUiState {
    data object Loading : CafeDetailUiState()
    data class Success(val coffeePlace: CoffeePlaceFull) : CafeDetailUiState()
    data class Error(val message: String, val throwable: Throwable? = null) : CafeDetailUiState()
}