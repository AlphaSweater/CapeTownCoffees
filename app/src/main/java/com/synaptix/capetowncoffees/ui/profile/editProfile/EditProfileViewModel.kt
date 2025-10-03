package com.synaptix.capetowncoffees.ui.profile.editProfile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.UpdateUserProfileUseCase
import com.synaptix.capetowncoffees.util.Resource
import com.synaptix.capetowncoffees.util.errorOf
import com.synaptix.capetowncoffees.util.loadingResource
import com.synaptix.capetowncoffees.util.successOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.UpdateAuthCredentialsUseCase
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val updateProfileUseCase: UpdateUserProfileUseCase,
    private val updateAuthCredentialsUseCase: UpdateAuthCredentialsUseCase
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _photoBase64 = MutableStateFlow<String?>(null)
    val photoBase64: StateFlow<String?> = _photoBase64.asStateFlow()

    private val _uiState = MutableStateFlow<Resource<Boolean>>(loadingResource())
    val uiState: StateFlow<Resource<Boolean>> = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow<Resource<String>>(loadingResource())
    val updateState: StateFlow<Resource<String>> = _updateState.asStateFlow()

    private val _fullName = MutableStateFlow("")
    val fullName: StateFlow<String> = _fullName.asStateFlow()

    private var currentUser: CoffeeUser? = null

    private var profilePictureUri: Uri? = null

    init {
        loadUserProfile()
    }

    /**
     * Call this from your UI when the user selects a new profile picture.
     */
    fun setProfilePictureUri(uri: Uri?) {
        profilePictureUri = uri
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = loadingResource()
            getUserProfileUseCase()
                .onSuccess { user ->
                    currentUser = user
                    _fullName.value = user.fullName
                    _email.value = user.email
                    _photoBase64.value = user.photoBase64
                    _uiState.value = successOf(true)
                }
                .onFailure { e -> _uiState.value = errorOf(e.message ?: "Failed to load profile") }
        }
    }

    fun updateProfile(
        currentPassword: String?,
        newPassword: String?,
        context: Context? = null
    ) {
        viewModelScope.launch {
            _updateState.value = loadingResource()
            val updatedUser = currentUser?.copy(
                fullName = _fullName.value.ifEmpty { null } ?: currentUser!!.fullName,
                email = _email.value.ifEmpty { null } ?: currentUser!!.email,
                updatedAt = CoffeeTimeUtils.nowSeconds()
            ) ?: return@launch

            val safeEmail = _email.value
            val safeNewPassword = newPassword ?: ""

            val authChanges = if (!currentPassword.isNullOrBlank() && (safeEmail.isNotBlank() || safeNewPassword.isNotBlank())) {
                updateAuthCredentialsUseCase(currentPassword, safeEmail, safeNewPassword)
            } else Result.success(Unit)

            if (authChanges.isFailure) {
                _updateState.value = errorOf(authChanges.exceptionOrNull()?.message ?: "Auth update failed")
                return@launch
            }

            updateProfileUseCase.execute(updatedUser, profilePictureUri, context)
                .onSuccess {
                    currentUser = updatedUser.copy(photoBase64 = if (profilePictureUri != null && context != null) currentUser?.photoBase64 else updatedUser.photoBase64)
                    _updateState.value = successOf("Profile updated successfully")
                }
                .onFailure { e -> _updateState.value = errorOf(e.message ?: "Failed to update profile") }
        }
    }

    fun resetUpdateState() {
        _updateState.value = loadingResource()
    }
}
