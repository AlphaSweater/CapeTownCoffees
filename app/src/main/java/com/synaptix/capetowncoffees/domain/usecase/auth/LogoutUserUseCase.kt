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
    private val ICoffeeUserRepository: ICoffeeUserRepository
) {
    suspend operator fun invoke(): LogoutResult {
        Timber.d("LogoutUserUseCase invoked")
        return try {
            Timber.d("Attempting logout")
            ICoffeeUserRepository.logoutUser()
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
