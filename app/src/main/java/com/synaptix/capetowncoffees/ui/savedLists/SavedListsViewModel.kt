package com.synaptix.capetowncoffees.ui.savedLists

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.synaptix.capetowncoffees.domain.model.UserList
import com.synaptix.capetowncoffees.domain.repository.IUserListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SavedListsViewModel @Inject constructor(
    repo: IUserListRepository
) : ViewModel() {

    // Real-time stream of lists
    val lists: LiveData<kotlin.collections.List<UserList>> = repo.observeLists().asLiveData()

    // Optional: expose loading/error later if needed
}
