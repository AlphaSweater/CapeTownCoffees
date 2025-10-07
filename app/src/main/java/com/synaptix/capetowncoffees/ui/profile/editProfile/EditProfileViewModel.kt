//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT provided assistance in designing ViewModel logic, LiveData handling, and
//implementing clean MVVM architecture principles.
//* It also helped refine data flow between repositories and UI layers.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.profile.editProfile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.UpdateAuthCredentialsUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.UpdateUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val updateProfileUseCase: UpdateUserProfileUseCase,
    private val updateAuthCredentialsUseCase: UpdateAuthCredentialsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Loading)
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var currentUser: CoffeeUser? = null
    private var profilePictureUri: Uri? = null
    private var currentPassword = ""
    private var newPassword = ""
    private var confirmPassword = ""

    init {
        loadUserProfile()
    }

    fun setProfilePictureUri(uri: Uri) {
        profilePictureUri = uri
        updateUiState()
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
                        isFormValid = isFormValid(),
                        profilePictureUri = profilePictureUri
                    )
                }.onFailure { exception ->
                    _uiState.value = EditProfileUiState.Error("Failed to load profile: ${exception.message}")
                }
            } catch (e: Exception) {
                _uiState.value = EditProfileUiState.Error("An error occurred: ${e.message}")
            }
        }
    }

    fun updateProfile(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = EditProfileUiState.Loading
            val user = currentUser ?: return@launch
            
            val updatedUser = user.copy(
                fullName = user.fullName,
                updatedAt = CoffeeTimeUtils.nowSeconds()
            )
            
            try {
                val params = UpdateUserProfileUseCase.Params(
                    updatedUser = updatedUser,
                    profilePictureUri = profilePictureUri,
                    currentPassword = if (newPassword.isNotBlank()) currentPassword else null,
                    newPassword = newPassword.ifBlank { null },
                    context = context
                )
                
                updateProfileUseCase.execute(params).onSuccess {
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                    _uiState.value = EditProfileUiState.Success(
                        user = updatedUser,
                        isFormValid = true,
                        successMessage = "Profile updated successfully",
                        profilePictureUri = profilePictureUri
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
        return currentUser?.fullName?.isNotBlank() == true
    }

    private fun updateUiState() {
        val currentState = _uiState.value
        if (currentState is EditProfileUiState.Success) {
            _uiState.value = currentState.copy(
                user = currentUser ?: return,
                isFormValid = isFormValid(),
                profilePictureUri = profilePictureUri
            )
        }
    }
}

sealed class EditProfileUiState {
    object Loading : EditProfileUiState()
    data class Success(
        val user: CoffeeUser,
        val isFormValid: Boolean = false,
        val successMessage: String? = null,
        val profilePictureUri: Uri? = null
    ) : EditProfileUiState()
    data class Error(val message: String) : EditProfileUiState()
}