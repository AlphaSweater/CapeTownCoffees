package com.synaptix.capetowncoffees.ui.savedLists.AddPlacesToList

import androidx.lifecycle.*
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.usecase.coffeeList.AddPlacesToListUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeList.AddToListsResult
import com.synaptix.capetowncoffees.domain.usecase.coffeeList.GetAllListsByUidUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AddListsUiState {
    object Loading : AddListsUiState()
    data class Loaded(val lists: List<CoffeeList>) : AddListsUiState()
    data class Error(val message: String) : AddListsUiState()
    object Done : AddListsUiState()
}

@HiltViewModel
class AddPlacesToListViewModel @Inject constructor(
    private val getAllLists: GetAllListsByUidUseCase,
    private val addPlaceToLists: AddPlacesToListUseCase
) : ViewModel() {

    private val _state = MutableLiveData<AddListsUiState>(AddListsUiState.Loading)
    val state: LiveData<AddListsUiState> = _state

    fun load() = viewModelScope.launch {
        _state.value = AddListsUiState.Loading
        val res = getAllLists()
        _state.value = res.fold(
            onSuccess = { AddListsUiState.Loaded(it) },
            onFailure = { AddListsUiState.Error(it.localizedMessage ?: "Failed to load lists") }
        )
    }

    fun confirm(placeId: String, selectedIds: List<String>) = viewModelScope.launch {
        when (val r = addPlaceToLists(placeId, selectedIds)) {
            is AddToListsResult.Success -> _state.value = AddListsUiState.Done
            is AddToListsResult.Error   -> _state.value = AddListsUiState.Error(r.message)
        }
    }
}