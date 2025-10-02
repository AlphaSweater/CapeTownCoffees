package com.synaptix.capetowncoffees.ui.profile

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.User
import com.synaptix.capetowncoffees.domain.usecase.user.GetUserProfileUseCase
import com.synaptix.capetowncoffees.BR
import com.synaptix.capetowncoffees.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
import timber.log.Timber

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    private val _userState = MutableStateFlow<Resource<User>>(Resource.Loading)
    val userState: StateFlow<Resource<User>> = _userState.asStateFlow()
    
    private val _userName = MutableLiveData<String>("")
    
    fun getUserName(): String = _userName.value ?: ""
    
    fun setUserName(value: String) {
        if (_userName.value != value) {
            _userName.value = value
        }
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _userState.value = Resource.Loading
            try {
                val result = getUserProfileUseCase()
                result.onSuccess { user ->
                    val fullName = user.fullName.trim()
                    Timber.d("Full name: $fullName")
                    _userName.postValue(fullName.ifEmpty { "User" })
                    Timber.d("User name set to: ${_userName.value}")
                    _userState.value = Resource.Success(user)
                }.onFailure { e ->
                    _userState.value = Resource.Error(e.message ?: "Failed to load profile")
                }
            } catch (e: Exception) {
                _userState.value = Resource.Error("Failed to load profile: ${e.message ?: "Unknown error"}")
            }
        }
    }
}
