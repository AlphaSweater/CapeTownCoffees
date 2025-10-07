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

package com.synaptix.capetowncoffees.domain.usecase.auth

import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import javax.inject.Inject
import timber.log.Timber

// Sealed class representing the result of a logout attempt
sealed class LogoutResult {
    object Success : LogoutResult()
    data class Error(val message: String) : LogoutResult()
}

class LogoutUserUseCase @Inject constructor(
    private val coffeeUserRepository: ICoffeeUserRepository
) {
    suspend operator fun invoke(): LogoutResult {
        Timber.d("LogoutUserUseCase invoked")
        return try {
            Timber.d("Attempting logout")
            coffeeUserRepository.logoutUser()
                .onSuccess {
                    Timber.d("Logout successful")
                    return LogoutResult.Success
                }
                .onFailure { exception ->
                    val message = exception.message ?: "Unknown error"
                    Timber.e(exception, "Logout failed: %s", message)
                    return LogoutResult.Error(message)
                }
            Timber.e("Should not reach here in logout flow")
            LogoutResult.Error("Unknown error")
        } catch (e: Exception) {
            Timber.e(e, "Exception during logout")
            LogoutResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}
