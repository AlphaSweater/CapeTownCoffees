package com.synaptix.capetowncoffees.domain.usecase.coffeeUser

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import javax.inject.Inject

/**
 * Use case for editing the current user's profile.
 */
class UpdateUserProfileUseCase @Inject constructor(
    private val userRepository: ICoffeeUserRepository
) {
    /**
     * Updates the current user's profile, including profile picture if provided.
     *
     * @param updatedUser The user object with updated fields.
     * @param profilePictureUri Optional Uri for new profile picture.
     * @param context Optional context for reading the image.
     * @return Result<Unit> indicating success or failure.
     */
    suspend fun execute(
        updatedUser: CoffeeUser,
        profilePictureUri: Uri? = null,
        context: Context? = null
    ): Result<Unit> {
        val currentUser = userRepository.getCurrentUserProfile().getOrElse {
            return Result.failure(it)
        } ?: return Result.failure(IllegalStateException("No current user profile found"))

        var userToUpdate = updatedUser
        if (profilePictureUri != null && context != null) {
            val inputStream = context.contentResolver.openInputStream(profilePictureUri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            val base64Image = if (bytes != null) Base64.encodeToString(bytes, Base64.DEFAULT) else null
            userToUpdate = userToUpdate.copy(
                photoBase64 = base64Image,
                updatedAt = System.currentTimeMillis()
            )
        }

        if (currentUser == userToUpdate) {
            // No changes to apply
            return Result.success(Unit)
        }

        if (currentUser.id.isBlank()) {
            return Result.failure(IllegalStateException("User ID is missing"))
        }

        return userRepository.updateUserProfile(currentUser.id, userToUpdate)
    }
}
