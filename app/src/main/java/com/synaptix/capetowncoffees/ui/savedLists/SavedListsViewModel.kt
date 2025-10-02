package com.synaptix.capetowncoffees.ui.savedLists

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.synaptix.capetowncoffees.domain.model.List
import com.synaptix.capetowncoffees.domain.repository.IListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SavedListsViewModel @Inject constructor(
    repo: IListRepository
) : ViewModel() {

    // Real-time stream of lists
    val lists: LiveData<kotlin.collections.List<List>> = repo.observeLists().asLiveData()

    // Optional: expose loading/error later if needed
}
