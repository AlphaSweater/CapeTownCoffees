package com.synaptix.capetowncoffees.domain.usecase.auth

import com.synaptix.capetowncoffees.data.model.UserDTO
import com.synaptix.capetowncoffees.domain.repository.UserRepository
import javax.inject.Inject

// Sealed class representing the result of a registration attempt
sealed class RegistrationResult {
    // Success indicates the user was registered successfully
    object Success : RegistrationResult()
    // EmailExists indicates the email is already registered
    object EmailExists : RegistrationResult()
    // Error indicates a failure with an associated message
    data class Error(val message: String) : RegistrationResult()
}

// Use case class for registering a new user
class RegisterUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(email: String, password: String, firstName: String? = null, lastName: String? = null): RegistrationResult {
        return try {
            // 1. Check if email exists
            userRepository.emailExists(email)
                .onSuccess { exists ->
                    if (exists) return RegistrationResult.EmailExists
                }
                .onFailure { exception ->
                    return RegistrationResult.Error(exception.message ?: "Failed to check email")
                }

            // 2. Create user DTO
            val userData = UserDTO(
                email = email,
                firstName = firstName,
                lastName = lastName
            )

            // 3. Register user
            userRepository.registerUser(email, password, userData)
                .onSuccess { return RegistrationResult.Success }
                .onFailure { exception ->
                    val msg = exception.message ?: "Failed to register user"
                    return if (msg.contains("email", ignoreCase = true)) {
                        RegistrationResult.EmailExists
                    } else {
                        RegistrationResult.Error(msg)
                    }
                }
            // Should not reach here
            RegistrationResult.Error("Unknown error")
        } catch (e: Exception) {
            RegistrationResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}