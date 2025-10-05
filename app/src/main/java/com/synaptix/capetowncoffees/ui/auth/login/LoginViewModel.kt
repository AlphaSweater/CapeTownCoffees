package com.synaptix.capetowncoffees.ui.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginResult
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

// Sealed class to manage the UI state for Login
sealed class LoginUiState {
    object Idle : LoginUiState() // The idle state, waiting for user input
    object Loading : LoginUiState() // The loading state, showing progress
    object Success : LoginUiState() // The success state, login successful
    data class Error(val message: String) : LoginUiState() // The error state with an error message
    data class ValidationError(
        val emailError: String? = null,
        val passwordError: String? = null
    ) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUserUseCase: LoginUserUseCase, // Injected use case to handle the login logic
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase // Injected use case for Google login
) : ViewModel() {
    // LiveData to hold the current login state
    private val _loginState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val loginState: LiveData<LoginUiState> get() = _loginState

    fun validateEmail(email: String): String? {
        Timber.d("validateEmail called with: %s", email)
        return when {
            email.isBlank() -> {
                Timber.d("Email validation failed: blank email")
                "Email is required"
            }
            else -> null
        }
    }

    fun validatePassword(password: String): String? {
        Timber.d("validatePassword called with: %s", password)
        return when {
            password.isBlank() -> {
                Timber.d("Password validation failed: blank password")
                "Password is required"
            }
            else -> null
        }
    }

    fun validateInputs(email: String, password: String): Boolean {
        Timber.d("validateInputs called with email=%s, password=%s", email, password)
        val emailError = validateEmail(email)
        val passwordError = validatePassword(password)

        if (emailError != null || passwordError != null) {
            Timber.d("Validation errors: emailError=%s, passwordError=%s", emailError, passwordError)
            _loginState.value = LoginUiState.ValidationError(emailError, passwordError)
            return false
        }
        Timber.d("Validation passed")
        return true
    }

    // Function to handle the login process
    fun loginUser(email: String, password: String) {
        Timber.d("loginUser called with email=%s", email)
        if (!validateInputs(email, password)) {
            Timber.d("loginUser aborted due to validation errors")
            return
        }

        viewModelScope.launch {
            try {
                Timber.d("Login process started for email=%s", email)
                _loginState.value = LoginUiState.Loading
                val result = loginUserUseCase(email, password)
                Timber.d("Login result for email=%s: %s", email, result)
                _loginState.value = when (result) {
                    is LoginResult.Success -> {
                        Timber.d("Login successful for email=%s", email)
                        LoginUiState.Success
                    }
                    is LoginResult.InvalidCredentials -> {
                        Timber.d("Login failed: Invalid credentials for email=%s", email)
                        LoginUiState.Error("Incorrect email or password")
                    }
                    is LoginResult.Error -> {
                        Timber.d("Login failed: Error for email=%s, message=%s", email, result.message)
                        LoginUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during login for email=%s", email)
                _loginState.value = LoginUiState.Error(
                    e.localizedMessage ?: "An unexpected error occurred"
                )
            }
        }
    }
    fun loginWithGoogleToken(idToken: String) {
        Timber.d("loginWithGoogleToken called")
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            try {
                when (val result = loginWithGoogleUseCase(idToken)) {
                    is LoginResult.Success -> {
                        Timber.d("Google login success")
                        _loginState.value = LoginUiState.Success
                    }
                    is LoginResult.Error -> {
                        Timber.d("Google login error: %s", result.message)
                        _loginState.value = LoginUiState.Error(result.message)
                    }
                    is LoginResult.InvalidCredentials -> {
                        // Rare for Google; still handle generically
                        _loginState.value = LoginUiState.Error("Could not sign in with Google")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during Google login")
                _loginState.value = LoginUiState.Error(e.localizedMessage ?: "Unexpected error")
            }
        }
    }

    // Function to reset the state back to idle
    fun resetState() {
        Timber.d("resetState called")
        _loginState.value = LoginUiState.Idle
    }
}