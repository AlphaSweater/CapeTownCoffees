// ui/saved/savelist/CreateListViewModel.kt
package com.synaptix.capetowncoffees.ui.savedLists.createSavedList

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.usecase.coffeeList.CreateListResult
import com.synaptix.capetowncoffees.domain.usecase.coffeeList.CreateListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CreateListUiState {
    object Idle : CreateListUiState()
    object Loading : CreateListUiState()
    data class Success(val id: String) : CreateListUiState()
    data class Error(val message: String) : CreateListUiState()
    data class ValidationError(val nameError: String? = null) : CreateListUiState()
}

@HiltViewModel
class CreateListViewModel @Inject constructor(
    private val createList: CreateListUseCase
) : ViewModel() {

    private val _state = MutableLiveData<CreateListUiState>(CreateListUiState.Idle)
    val state: LiveData<CreateListUiState> get() = _state

    private fun validateName(name: String): String? = when {
        name.isBlank() -> "List name is required"
        name.length < 2 -> "List name must be at least 2 characters"
        else -> null
    }

    fun saveList(name: String, description: String?, isPublic: Boolean) {
        val nameError = validateName(name)
        if (nameError != null) {
            _state.value = CreateListUiState.ValidationError(nameError)
            return
        }

        val newCoffeeList = CoffeeList(
            name = name,
            description = description,
            isPublic = isPublic
        )

        viewModelScope.launch {
            _state.value = CreateListUiState.Loading
            when (val res = createList(newCoffeeList)) {
                is CreateListResult.Success -> _state.value = CreateListUiState.Success(res.id)
                is CreateListResult.Error -> _state.value = CreateListUiState.Error(res.message)
            }
        }

    }
    fun resetState() {
        _state.value = CreateListUiState.Idle
    }
}
