package com.synaptix.capetowncoffees.ui.profile.editProfile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.User
import com.synaptix.capetowncoffees.domain.usecase.user.UpdateUserProfileUseCase
import com.synaptix.capetowncoffees.util.Resource
import com.synaptix.capetowncoffees.util.errorOf
import com.synaptix.capetowncoffees.util.loadingResource
import com.synaptix.capetowncoffees.util.successOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.synaptix.capetowncoffees.domain.usecase.user.GetUserProfileUseCase
import com.synaptix.capetowncoffees.domain.usecase.user.UpdateAuthCredentialsUseCase

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val updateProfileUseCase: UpdateUserProfileUseCase,
    private val updateAuthCredentialsUseCase: UpdateAuthCredentialsUseCase
) : ViewModel() {

    data class UiState(
        val firstName: String,
        val lastName: String,
        val email: String,
        val photoBase64: String? = null
    )

    private val _uiState = MutableStateFlow<Resource<UiState>>(loadingResource())
    val uiState: StateFlow<Resource<UiState>> = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow<Resource<String>>(loadingResource())
    val updateState: StateFlow<Resource<String>> = _updateState.asStateFlow()

    private var currentUser: User? = null

    private var profilePictureUri: Uri? = null

    init { loadUserProfile() }

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
                    _uiState.value = successOf(
                        UiState(user.firstName ?: "", user.lastName ?: "", user.email, user.photoBase64)
                    )
                }
                .onFailure { e -> _uiState.value = errorOf(e.message ?: "Failed to load profile") }
        }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        email: String?,
        currentPassword: String?,
        newPassword: String?,
        context: Context? = null
    ) {
        viewModelScope.launch {
            _updateState.value = loadingResource()
            val updatedUser = currentUser?.copy(
                firstName = firstName.ifEmpty { null },
                lastName = lastName.ifEmpty { null },
                email = email ?: currentUser!!.email,
                updatedAt = System.currentTimeMillis()
            ) ?: return@launch

            val safeEmail = email ?: currentUser?.email ?: ""
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
