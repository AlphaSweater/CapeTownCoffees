package com.synaptix.capetowncoffees.ui.savedLists

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedListsViewModel @Inject constructor(
    internal val repository: ICoffeeListRepository
) : ViewModel() {

    // Real-time stream of lists
    val lists: LiveData<List<CoffeeList>> = repository.observeLists().asLiveData()
}
