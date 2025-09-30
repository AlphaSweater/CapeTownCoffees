package com.synaptix.capetowncoffees.domain.usecase.auth

import com.synaptix.capetowncoffees.domain.repository.UserRepository
import javax.inject.Inject

// Sealed class representing the result of a logout attempt
sealed class LogoutResult {
    object Success : LogoutResult()
    data class Error(val message: String) : LogoutResult()
}

class LogoutUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(): LogoutResult {
        return try {
            userRepository.logoutUser()
                .onSuccess { return LogoutResult.Success }
                .onFailure { exception ->
                    val message = exception.message ?: "Unknown error"
                    return LogoutResult.Error(message)
                }
            // Should not reach here
            LogoutResult.Error("Unknown error")
        } catch (e: Exception) {
            LogoutResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}

