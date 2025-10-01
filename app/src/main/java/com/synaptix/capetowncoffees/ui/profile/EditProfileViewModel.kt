package com.synaptix.capetowncoffees.ui.profile

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.data.model.UserDTO
import com.synaptix.capetowncoffees.domain.model.User
import com.synaptix.capetowncoffees.domain.repository.IUserRepository
import com.synaptix.capetowncoffees.util.Resource
import com.synaptix.capetowncoffees.util.errorOf
import com.synaptix.capetowncoffees.util.loadingResource
import com.synaptix.capetowncoffees.util.successOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val userRepository: IUserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<Resource<UiState>>(loadingResource())
    val uiState: StateFlow<Resource<UiState>> = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow<Resource<String>>(loadingResource())
    val updateState: StateFlow<Resource<String>> = _updateState.asStateFlow()

    private var currentUser: User? = null

    data class UiState(
        val firstName: String,
        val lastName: String,
        val email: String
    )

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = loadingResource()
            try {
                val userId = userRepository.getCurrentUserId()
                if (userId == null) {
                    _uiState.value = errorOf("User not authenticated")
                    return@launch
                }

                val result = userRepository.getUserProfile(userId)
                result.onSuccess { userDtoOrNull ->
                    val userDto = userDtoOrNull
                    if (userDto == null) {
                        _uiState.value = errorOf("User profile not found")
                    } else {
                        currentUser = User(
                            id = userDto.id,
                            email = userDto.email,
                            firstName = userDto.firstName,
                            lastName = userDto.lastName,
                            createdAt = userDto.createdAt,
                            updatedAt = userDto.updatedAt,
                            lastLoginAt = userDto.lastLoginAt
                        )
                        _uiState.value = successOf(
                            UiState(
                                firstName = userDto.firstName ?: "",
                                lastName = userDto.lastName ?: "",
                                email = userDto.email
                            )
                        )
                    }
                }.onFailure { e ->
                    _uiState.value = errorOf(e.message ?: "Failed to load profile")
                }
            } catch (e: Exception) {
                Log.e("EditProfileViewModel", "Error loading user profile", e)
                _uiState.value = errorOf("Failed to load profile: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        email: String? = null,
        currentPassword: String? = null,
        newPassword: String? = null
    ) {
        viewModelScope.launch {
            _updateState.value = loadingResource()

            try {
                val userId = userRepository.getCurrentUserId()
                if (userId == null) {
                    _updateState.value = errorOf("User not authenticated")
                    return@launch
                }

                // Update profile data
                val updatedUser = currentUser?.copy(
                    firstName = firstName.ifEmpty { null },
                    lastName = lastName.ifEmpty { null },
                    updatedAt = System.currentTimeMillis()
                )

                if (updatedUser == null) {
                    _updateState.value = errorOf("User data not loaded")
                    return@launch
                }

                // Decide if auth-sensitive changes are requested
                val wantsEmailChange = !email.isNullOrBlank() && email != (currentUser?.email ?: "")
                val wantsPasswordChange = !currentPassword.isNullOrBlank() && !newPassword.isNullOrBlank()

                // If changing email or password, re-authenticate and apply changes in Firebase Auth first
                if (wantsEmailChange || wantsPasswordChange) {
                    val auth = FirebaseAuth.getInstance()
                    val fbUser = auth.currentUser ?: run {
                        _updateState.value = errorOf("No authenticated user")
                        return@launch
                    }
                    val existingEmail = fbUser.email
                    if (existingEmail.isNullOrBlank()) {
                        _updateState.value = errorOf("Cannot get current email for re-authentication")
                        return@launch
                    }
                    if (currentPassword.isNullOrBlank()) {
                        _updateState.value = errorOf("Current password required for sensitive changes")
                        return@launch
                    }

                    // Re-authenticate
                    val credential = EmailAuthProvider.getCredential(existingEmail, currentPassword)
                    fbUser.reauthenticate(credential).await()

                    // Update email if requested
                    if (wantsEmailChange) {
                        fbUser.updateEmail(email!!).await()
                    }

                    // Update password if requested
                    if (wantsPasswordChange) {
                        fbUser.updatePassword(newPassword!!).await()
                    }
                }

                // Convert to DTO for repository (persist profile fields in Firestore)
                val userDto = UserDTO(
                    id = updatedUser.id,
                    email = email?.ifBlank { null } ?: updatedUser.email,
                    firstName = updatedUser.firstName,
                    lastName = updatedUser.lastName,
                    createdAt = updatedUser.createdAt,
                    updatedAt = updatedUser.updatedAt,
                    lastLoginAt = updatedUser.lastLoginAt
                )

                // Update profile in repository
                val result = userRepository.updateUserProfile(userId, userDto)
                result.onSuccess {
                    // Update in-memory user and UI state to reflect latest values
                    val finalEmail = email?.ifBlank { null } ?: updatedUser.email
                    currentUser = updatedUser.copy(email = finalEmail)

                    _uiState.value = successOf(
                        UiState(
                            firstName = currentUser?.firstName ?: "",
                            lastName = currentUser?.lastName ?: "",
                            email = currentUser?.email ?: ""
                        )
                    )

                    _updateState.value = successOf("Profile updated successfully")
                }.onFailure { e ->
                    _updateState.value = errorOf(e.message ?: "Failed to update profile")
                }
            } catch (e: Exception) {
                Log.e("EditProfileViewModel", "Error updating profile", e)
                _updateState.value =
                    errorOf("Failed to update profile: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun updateProfilePicture(imageUri: Uri) {
        viewModelScope.launch {
            // In a real app, you would upload the image to a server and update the user's profile picture URL
            // This is a placeholder for the actual implementation
            Log.d("EditProfileViewModel", "Profile picture update would happen here: $imageUri")
        }
    }

    fun resetUpdateState() {
        _updateState.value = loadingResource()
    }
}
