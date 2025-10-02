package com.synaptix.capetowncoffees.ui.savedLists

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.UserList
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import com.synaptix.capetowncoffees.domain.repository.IUserListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ListDetailsViewModel @Inject constructor(
    private val savedRepo: IUserListRepository,
    private val placesRepo: IPlacesApiRepository
) : ViewModel() {

    private val _listId = MutableLiveData<String>()
    val listId: LiveData<String> get() = _listId

    fun setListId(id: String) {
        if (_listId.value == id) return
        _listId.value = id
    }

    fun observeList(id: String): LiveData<UserList?> = savedRepo.observeList(id).asLiveData()

    private val _places = MutableLiveData<kotlin.collections.List<CoffeePlaceFull>>()
    val places: LiveData<kotlin.collections.List<CoffeePlaceFull>> get() = _places

    fun loadPlacesForIds(ids: kotlin.collections.List<String>) {
        if (ids.isEmpty()) {
            _places.value = emptyList()
            return
        }
        viewModelScope.launch {
            Timber.d("Loading place details for ${ids.size} ids")
            val results = mutableListOf<CoffeePlaceFull>()
            for (pid in ids) {
                val res = placesRepo.getCoffeePlaceDetails(pid)
                res.onSuccess { results.add(it) }
                    .onFailure { Timber.w(it, "Failed to fetch place $pid") }
            }
            _places.postValue(results)
            Timber.d("Loaded ${results.size} place details")
        }
    }

    fun deleteListAndReturn(id: String, onDone: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                savedRepo.deleteList(id)
                onDone()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete list $id")
                onError(e)
            }
        }
    }
}
