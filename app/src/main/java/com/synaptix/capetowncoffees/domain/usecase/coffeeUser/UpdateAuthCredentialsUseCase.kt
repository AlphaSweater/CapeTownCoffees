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

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Use case for updating authentication credentials (email and/or password).
 */
class UpdateAuthCredentialsUseCase @Inject constructor(
    private val auth: FirebaseAuth
) {
    /**
     * Reauthenticates the user with the current password,
     * then updates email and/or password if provided.
     */
    suspend operator fun invoke(
        currentPassword: String,
        newEmail: String? = null,
        newPassword: String? = null
    ): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("No authenticated user"))

        val email = user.email
            ?: return Result.failure(IllegalStateException("Current email not available"))

        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        return try {
            // Reauthenticate first
            user.reauthenticate(credential).await()

            // Apply updates if given
            if (!newEmail.isNullOrBlank()) {
                user.updateEmail(newEmail).await()
            }
            if (!newPassword.isNullOrBlank()) {
                user.updatePassword(newPassword).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
