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
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.domain.usecase.coffeeUser

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Use case for editing the current user's profile.
 *
 * @property coffeeUserRepository The repository for user-related operations.
 */
class UpdateUserProfileUseCase @Inject constructor(
    private val coffeeUserRepository: ICoffeeUserRepository
) {
    /**
     * Data class for update profile parameters.
     */
    data class Params(
        val updatedUser: CoffeeUser,
        val profilePictureUri: Uri? = null,
        val currentPassword: String? = null,
        val newPassword: String? = null,
        val context: Context? = null
    )

    /**
     * Updates the current user's profile, including profile picture and password if provided.
     *
     * @param params The parameters for the update operation.
     * @return Result<Unit> indicating success or failure.
     */
    suspend fun execute(params: Params): Result<Unit> {
        val currentUser = coffeeUserRepository.getCurrentUserProfile().getOrElse {
            return Result.failure(it)
        } ?: return Result.failure(IllegalStateException("No current user profile found"))

        // Handle password change if new password is provided
        if (params.newPassword != null) {
            if (params.currentPassword.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("Current password is required to change password"))
            }

            // Re-authenticate user
            coffeeUserRepository.loginUser(currentUser.email, params.currentPassword).onFailure {
                return Result.failure(IllegalArgumentException("Current password is incorrect"))
            }

            // Update the user's password using Firebase's built-in method
            val firebaseUser = coffeeUserRepository.getCurrentUser()
            try {
                firebaseUser?.updatePassword(params.newPassword)?.await()
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to update password: ${e.message}"))
            }
        }

        // Handle profile picture update
        var userToUpdate = params.updatedUser.copy(
            updatedAt = System.currentTimeMillis()
        )

        if (params.profilePictureUri != null && params.context != null) {
            val inputStream = params.context.contentResolver.openInputStream(params.profilePictureUri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            val base64Image = if (bytes != null) {
                Base64.encodeToString(bytes, Base64.DEFAULT)
            } else {
                null
            }
            userToUpdate = userToUpdate.copy(
                photoBase64 = base64Image
            )
        }

        // If no changes to user data (excluding password), return success
        if (currentUser == userToUpdate) {
            return Result.success(Unit)
        }

        if (currentUser.id.isBlank()) {
            return Result.failure(IllegalStateException("User ID is missing"))
        }

        // Update user profile data
        return coffeeUserRepository.updateUserProfile(currentUser.id, userToUpdate)
    }
}
