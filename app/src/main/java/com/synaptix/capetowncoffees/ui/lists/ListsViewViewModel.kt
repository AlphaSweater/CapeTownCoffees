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

package com.synaptix.capetowncoffees.ui.lists

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
public class ListsViewViewModel @Inject constructor(
    internal val repository: ICoffeeListRepository,
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    // ─────────── State ───────────
    // We expose the resolved userId; lists stream depends on this being non-null.
    private val _userId: MutableLiveData<String?> = MutableLiveData(null)
    public val userId: LiveData<String?> get() = _userId

    // ─────────── Public API ───────────
    // Returns a live stream of lists for the current user; falls back to empty while uid is unknown.
    public fun getLists(): LiveData<List<CoffeeList>> {
        val id = _userId.value ?: return MutableLiveData(emptyList())
        return repository.observeLists(id).asLiveData()
    }

    // ─────────── Init ───────────
    // Resolve the current user profile once; downstream flows react when userId appears.
    init {
        viewModelScope.launch {
            val user = getUserProfileUseCase().getOrNull()
            _userId.postValue(user?.id)
        }
    }
}
