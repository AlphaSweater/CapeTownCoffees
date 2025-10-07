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
