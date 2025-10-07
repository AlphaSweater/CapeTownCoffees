package com.synaptix.capetowncoffees.ui.lists.createSavedList

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

// ─────────── UI State ───────────
// Represents the create-list flow; Fragment reacts to these simple states.
public sealed class CreateListUiState {
    public object Idle : CreateListUiState()
    public object Loading : CreateListUiState()
    public data class Success(val id: String) : CreateListUiState()
    public data class Error(val message: String) : CreateListUiState()
    public data class ValidationError(val nameError: String? = null) : CreateListUiState()
}

// ─────────── ViewModel ───────────
// Validates input, calls use case, and exposes a single LiveData state.
@HiltViewModel
public class CreateListViewModel @Inject constructor(
    private val createList: CreateListUseCase
) : ViewModel() {

    // Config kept close; avoids sprinkling magic numbers across the file.
    private companion object {
        private const val MIN_NAME_LEN = 2
    }

    private val _state: MutableLiveData<CreateListUiState> =
        MutableLiveData(CreateListUiState.Idle)
    public val state: LiveData<CreateListUiState> get() = _state

    // ─────────── Validation ───────────
    // Quick client-side guard to keep UX responsive.
    private fun validateName(name: String): String? = when {
        name.isBlank()          -> "List name is required"
        name.length < MIN_NAME_LEN -> "List name must be at least $MIN_NAME_LEN characters"
        else                    -> null
    }

    // ─────────── Actions ───────────
    // Creates a list when validation passes; posts state transitions as we go.
    public fun saveList(name: String, description: String?, isPublic: Boolean) {
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
                is CreateListResult.Error   -> _state.value = CreateListUiState.Error(res.message)
            }
        }
    }

    // Resets UI to Idle so the screen can be reused without stale errors.
    public fun resetState() {
        _state.value = CreateListUiState.Idle
    }
}
