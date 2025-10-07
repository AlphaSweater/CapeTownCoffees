package com.synaptix.capetowncoffees.ui.profile.editProfile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.UpdateAuthCredentialsUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.UpdateUserProfileUseCase
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────── UI State ───────────
// Compact result for the screen; Fragment reacts to this.
public sealed class EditProfileUiState {
    public object Loading : EditProfileUiState()
    public data class Success(
        val user: CoffeeUser,
        val isFormValid: Boolean = false,
        val successMessage: String? = null,
        val profilePictureUri: Uri? = null
    ) : EditProfileUiState()
    public data class Error(val message: String) : EditProfileUiState()
}

// ─────────── ViewModel ───────────
// Loads the profile, tracks draft edits, and applies updates.
@HiltViewModel
public class EditProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val updateProfileUseCase: UpdateUserProfileUseCase,
    private val updateAuthCredentialsUseCase: UpdateAuthCredentialsUseCase
) : ViewModel() {

    // ─────────── State & Model ───────────
    private val _uiState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Loading)
    public val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var currentUser: CoffeeUser? = null
    private var profilePictureUri: Uri? = null
    private var currentPassword: String = ""
    private var newPassword: String = ""
    private var confirmPassword: String = ""

    // ─────────── Init ───────────
    init {
        loadUserProfile()
    }

    // ─────────── Mutators (called by Fragment) ───────────
    // We store draft edits and recompute basic validation flags.

    public fun setProfilePictureUri(uri: Uri) {
        profilePictureUri = uri
        updateUiState()
    }

    public fun setFullName(name: String) {
        currentUser = (currentUser ?: return).copy(fullName = name)
        updateUiState()
    }

    public fun setCurrentPassword(password: String) {
        currentPassword = password
        updateUiState()
    }

    public fun setNewPassword(password: String) {
        newPassword = password
        updateUiState()
    }

    public fun setConfirmPassword(password: String) {
        confirmPassword = password
        updateUiState()
    }

    // ─────────── Loading ───────────
    // Fetches the profile once and seeds the Success state.
    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = EditProfileUiState.Loading
            try {
                val result = getUserProfileUseCase()
                result
                    .onSuccess { user ->
                        currentUser = user
                        _uiState.value = EditProfileUiState.Success(
                            user = user,
                            isFormValid = isFormValid(),
                            profilePictureUri = profilePictureUri
                        )
                    }
                    .onFailure { e ->
                        _uiState.value = EditProfileUiState.Error("Failed to load profile: ${e.message}")
                    }
            } catch (e: Exception) {
                _uiState.value = EditProfileUiState.Error("An error occurred: ${e.message}")
            }
        }
    }

    // ─────────── Actions ───────────
    // Applies profile + optional password/photo changes.
    public fun updateProfile(context: Context) {
        viewModelScope.launch {
            _uiState.value = EditProfileUiState.Loading

            val user = currentUser ?: run {
                _uiState.value = EditProfileUiState.Error("User not available")
                return@launch
            }

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

                updateProfileUseCase.execute(params)
                    .onSuccess {
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                        _uiState.value = EditProfileUiState.Success(
                            user = updatedUser,
                            isFormValid = true,
                            successMessage = "Profile updated successfully",
                            profilePictureUri = profilePictureUri
                        )
                    }
                    .onFailure { e ->
                        _uiState.value = EditProfileUiState.Error("Failed to update profile: ${e.message}")
                    }
            } catch (e: Exception) {
                _uiState.value = EditProfileUiState.Error("An error occurred: ${e.message}")
            }
        }
    }

    // ─────────── Validation ───────────
    // Minimal client-side guard; server remains source of truth.
    private fun isFormValid(): Boolean =
        currentUser?.fullName?.isNotBlank() == true

    // ─────────── State Propagation ───────────
    // Pushes small edits into the current Success state.
    private fun updateUiState() {
        val current = _uiState.value
        if (current is EditProfileUiState.Success) {
            val user = currentUser ?: return
            _uiState.value = current.copy(
                user = user,
                isFormValid = isFormValid(),
                profilePictureUri = profilePictureUri
            )
        }
    }
}
