package com.synaptix.capetowncoffees.ui.profile

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import com.synaptix.capetowncoffees.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
public class ProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    // ─────────── State ───────────
    // Exposes user resource to the UI; starts as loading.
    private val _userState = MutableStateFlow<Resource<CoffeeUser>>(Resource.Loading)
    public val userState: StateFlow<Resource<CoffeeUser>> = _userState.asStateFlow()

    // Tracks a simple derived field for quick access (display name).
    private val _userName = MutableLiveData("")

    // ─────────── Public API ───────────
    // Lightweight getter for current cached name.
    public fun getUserName(): String = _userName.value ?: ""

    // Allows manual name override if needed by the UI.
    public fun setUserName(value: String) {
        if (_userName.value != value) _userName.value = value
    }

    // ─────────── Actions ───────────
    // Loads the profile and maps it to UI state; keeps a friendly display name.
    public fun loadUserProfile() {
        viewModelScope.launch {
            _userState.value = Resource.Loading
            try {
                val result = getUserProfileUseCase()
                result
                    .onSuccess { user ->
                        val fullName = user.fullName.trim()
                        Timber.d("Full name: $fullName")
                        _userName.value = fullName.ifEmpty { "User" }
                        Timber.d("User name set to: ${_userName.value}")
                        _userState.value = Resource.Success(user)
                    }
                    .onFailure { e ->
                        _userState.value = Resource.Error(e.message ?: "Failed to load profile")
                    }
            } catch (e: Exception) {
                _userState.value = Resource.Error(
                    "Failed to load profile: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }
}
