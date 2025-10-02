package com.synaptix.capetowncoffees.ui.savedLists

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SavedListsViewModel @Inject constructor(
    repo: ICoffeeListRepository
) : ViewModel() {

    // Real-time stream of lists
    val lists: LiveData<List<CoffeeList>> = repo.observeLists().asLiveData()

    // Optional: expose loading/error later if needed
}
