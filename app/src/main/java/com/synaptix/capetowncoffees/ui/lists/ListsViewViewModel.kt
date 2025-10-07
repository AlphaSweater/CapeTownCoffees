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
class ListsViewViewModel @Inject constructor(
    internal val repository: ICoffeeListRepository,
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    // Real-time stream of lists
    fun getLists(): LiveData<List<CoffeeList>> {
        val uid = userId.value
        return if (uid != null) {
            repository.observeLists(uid).asLiveData()
        } else {
            androidx.lifecycle.MutableLiveData(emptyList())
        }
    }

    private val _userId = androidx.lifecycle.MutableLiveData<String?>()
    val userId: LiveData<String?> get() = _userId

    init {
        viewModelScope.launch {
            val result = getUserProfileUseCase()
            val user = result.getOrNull()
            _userId.postValue(user?.id)
        }
    }
}
