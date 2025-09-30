package com.synaptix.capetowncoffees.domain.usecase.auth

import com.synaptix.capetowncoffees.domain.repository.UserRepository
import javax.inject.Inject

// Sealed class representing the result of a login attempt
sealed class LoginResult {
    // Success indicates the login was successful
    object Success : LoginResult()
    // InvalidCredentials indicates the provided credentials (email or password) are incorrect
    object InvalidCredentials : LoginResult()
    // Error indicates a general error occurred during the login process, with an associated message
    data class Error(val message: String) : LoginResult()
}

// UseCase class for handling user login logic
class LoginUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    // Invokes the login process with email and password parameters
    // Returns a LoginResult indicating the outcome
    suspend operator fun invoke(email: String, password: String): LoginResult {
        return try {
            userRepository.loginUser(email, password)
                .onSuccess { return LoginResult.Success }
                .onFailure { exception ->
                    val message = exception.message ?: "Unknown error"
                    return when {
                        message.contains("no user record", ignoreCase = true) -> LoginResult.InvalidCredentials
                        message.contains("password is invalid", ignoreCase = true) -> LoginResult.InvalidCredentials
                        else -> LoginResult.Error(message)
                    }
                }
            // Should not reach here
            LoginResult.Error("Unknown error")
        } catch (e: Exception) {
            LoginResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}