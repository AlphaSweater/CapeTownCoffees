package com.synaptix.capetowncoffees.domain.usecase.auth

import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import javax.inject.Inject
import timber.log.Timber

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
    private val coffeeUserRepository: ICoffeeUserRepository
) {
    suspend operator fun invoke(email: String, password: String, fullName: String): RegistrationResult {
        Timber.d("RegisterUserUseCase invoked: email=%s, fullName=%s", email, fullName)
        return try {
            Timber.d("Checking if email exists: %s", email)
            coffeeUserRepository.emailExists(email)
                .onSuccess { exists ->
                    Timber.d("Email exists result: %s", exists)
                    if (exists) {
                        Timber.d("Email already registered: %s", email)
                        return RegistrationResult.EmailExists
                    }
                }
                .onFailure { exception ->
                    Timber.e(exception, "Failed to check if email exists: %s", email)
                    return RegistrationResult.Error(exception.message ?: "Failed to check email")
                }

            Timber.d("Registering user: %s", fullName)
            coffeeUserRepository.registerUser(email, password, fullName)
                .onSuccess {
                    Timber.d("User registered successfully: %s", email)
                    return RegistrationResult.Success
                }
                .onFailure { exception ->
                    val msg = exception.message ?: "Failed to register user"
                    Timber.e(exception, "Failed to register user: %s", email)
                    return if (msg.contains("email", ignoreCase = true)) {
                        Timber.d("Registration failed due to email already existing: %s", email)
                        RegistrationResult.EmailExists
                    } else {
                        RegistrationResult.Error(msg)
                    }
                }
            Timber.e("Should not reach here in registration flow")
            RegistrationResult.Error("Unknown error")
        } catch (e: Exception) {
            Timber.e(e, "Exception during registration for email: %s", email)
            RegistrationResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}