package com.synaptix.capetowncoffees.domain.usecase.auth

import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import javax.inject.Inject
import timber.log.Timber

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
    private val coffeeUserRepository: ICoffeeUserRepository
) {
    // Invokes the login process with email and password parameters
    // Returns a LoginResult indicating the outcome
    suspend operator fun invoke(email: String, password: String): LoginResult {
        Timber.d("LoginUserUseCase invoked: email=%s", email)
        return try {
            Timber.d("Attempting login for email: %s", email)
            coffeeUserRepository.loginUser(email, password)
                .onSuccess {
                    Timber.d("Login successful for email: %s", email)
                    return LoginResult.Success
                }
                .onFailure { exception ->
                    val message = exception.message ?: "Unknown error"
                    Timber.e(exception, "Login failed for email: %s, message: %s", email, message)
                    return when {
                        message.contains("no user record", ignoreCase = true) -> {
                            Timber.d("Invalid credentials: no user record for email: %s", email)
                            LoginResult.InvalidCredentials
                        }
                        message.contains("password is invalid", ignoreCase = true) -> {
                            Timber.d("Invalid credentials: password is invalid for email: %s", email)
                            LoginResult.InvalidCredentials
                        }
                        else -> LoginResult.Error(message)
                    }
                }
            Timber.e("Should not reach here in login flow")
            LoginResult.Error("Unknown error")
        } catch (e: Exception) {
            Timber.e(e, "Exception during login for email: %s", email)
            LoginResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}