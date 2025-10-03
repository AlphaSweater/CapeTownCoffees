package com.synaptix.capetowncoffees.ui.profile.editProfile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.User
import com.synaptix.capetowncoffees.domain.usecase.user.GetUserProfileUseCase
import com.synaptix.capetowncoffees.domain.usecase.user.UpdateUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val updateProfileUseCase: UpdateUserProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Loading)
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var currentUser: User? = null
    private var profilePictureUri: Uri? = null
    
    fun setProfilePictureUri(uri: Uri) {
        profilePictureUri = uri
        updateUiState()
    }
    
    private var currentPassword = ""
    private var newPassword = ""
    private var confirmPassword = ""

    init {
        loadUserProfile()
    }

    fun setFullName(name: String) {
        currentUser = currentUser?.copy(fullName = name)
        updateUiState()
    }

    fun setCurrentPassword(password: String) {
        currentPassword = password
        updateUiState()
    }

    fun setNewPassword(password: String) {
        newPassword = password
        updateUiState()
    }

    fun setConfirmPassword(password: String) {
        confirmPassword = password
        updateUiState()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = EditProfileUiState.Loading
            try {
                val result = getUserProfileUseCase()
                result.onSuccess { user ->
                    currentUser = user
                    _uiState.value = EditProfileUiState.Success(
                        user = user,
                        isFormValid = isFormValid()
                    )
                }.onFailure { exception ->
                    _uiState.value = EditProfileUiState.Error("Failed to load profile: ${exception.message}")
                }
            } catch (e: Exception) {
                _uiState.value = EditProfileUiState.Error("An unexpected error occurred")
            }
        }
    }

    fun updateProfile(context: android.content.Context) {
        viewModelScope.launch {
            val user = currentUser ?: run {
                _uiState.value = EditProfileUiState.Error("User not found")
                return@launch
            }

            _uiState.value = EditProfileUiState.Loading

            try {
                // Create the Params object with all required parameters
                val params = UpdateUserProfileUseCase.Params(
                    updatedUser = user.copy(
                        fullName = user.fullName, // This should be the new full name from the UI
                        // Add any other fields that can be updated
                    ),
                    profilePictureUri = profilePictureUri,
                    currentPassword = if (newPassword.isNotBlank()) currentPassword else null,
                    newPassword = if (newPassword.isNotBlank()) newPassword else null,
                    context = context // Pass the context for file operations
                )

                // Execute the use case with the Params object
                updateProfileUseCase.execute(params).onSuccess {
                    // Clear password fields on success
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""

                    _uiState.value = EditProfileUiState.Success(
                        user = user,
                        isFormValid = true,
                        successMessage = "Profile updated successfully"
                    )
                }.onFailure { exception ->
                    _uiState.value = EditProfileUiState.Error("Failed to update profile: ${exception.message}")
                }
            } catch (e: Exception) {
                _uiState.value = EditProfileUiState.Error("An error occurred: ${e.message}")
            }
        }
    }

    private fun isFormValid(): Boolean {
        // Add your validation logic here
        return currentUser?.fullName?.isNotBlank() == true
    }

    private fun updateUiState() {
        val currentState = _uiState.value
        if (currentState is EditProfileUiState.Success) {
            _uiState.value = currentState.copy(
                user = currentUser ?: return,
                isFormValid = isFormValid()
            )
        }
    }
}

sealed class EditProfileUiState {
    object Loading : EditProfileUiState()
    data class Success(
        val user: User,
        val isFormValid: Boolean = false,
        val successMessage: String? = null
    ) : EditProfileUiState()
    data class Error(val message: String) : EditProfileUiState()
}