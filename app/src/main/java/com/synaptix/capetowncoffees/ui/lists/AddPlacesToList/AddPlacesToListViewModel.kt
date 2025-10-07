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
//* ChatGPT provided assistance in designing ViewModel logic, LiveData handling, and
//implementing clean MVVM architecture principles.
//* It also helped refine data flow between repositories and UI layers.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.lists.AddPlacesToList

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.usecase.coffeeList.AddPlacesToListUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeList.AddToListsResult
import com.synaptix.capetowncoffees.domain.usecase.coffeeList.GetAllListsByUidUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────── UI State ───────────
// Models one-shot loading, data, error, and completion for the add-to-lists flow.
public sealed class AddListsUiState {
    public data object Loading : AddListsUiState()
    public data class Loaded(val lists: List<CoffeeList>) : AddListsUiState()
    public data class Error(val message: String) : AddListsUiState()
    public data object Done : AddListsUiState()
}

// ─────────── ViewModel ───────────
// Fetches user's lists and submits add requests; exposes a simple state stream.
@HiltViewModel
public class AddPlacesToListViewModel @Inject constructor(
    private val getAllLists: GetAllListsByUidUseCase,
    private val addPlaceToLists: AddPlacesToListUseCase
) : ViewModel() {

    // We keep state in MutableLiveData and expose it as read-only LiveData to the UI.
    private val _state: MutableLiveData<AddListsUiState> =
        MutableLiveData(AddListsUiState.Loading)
    public val state: LiveData<AddListsUiState> = _state

    // ─────────── Intents ───────────
    // Loads all lists for the current user; errors are surfaced with a friendly message.
    public fun load() = viewModelScope.launch {
        _state.value = AddListsUiState.Loading
        val res = getAllLists()
        _state.value = res.fold(
            onSuccess = { lists -> AddListsUiState.Loaded(lists) },
            onFailure = { e -> AddListsUiState.Error(e.localizedMessage ?: "Failed to load lists") }
        )
    }

    // Adds a place to the selected lists; we finish the flow on success.
    public fun confirm(placeId: String, selectedIds: List<String>) = viewModelScope.launch {
        when (val r = addPlaceToLists(placeId, selectedIds)) {
            is AddToListsResult.Success -> _state.value = AddListsUiState.Done
            is AddToListsResult.Error   -> _state.value = AddListsUiState.Error(r.message)
        }
    }
}
