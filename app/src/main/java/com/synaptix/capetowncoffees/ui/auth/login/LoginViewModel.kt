package com.synaptix.capetowncoffees.ui.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginResult
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val loginUserUseCase: LoginUserUseCase // Injected use case to handle the login logic
) : ViewModel() {
    // LiveData to hold the current login state
    private val _loginState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val loginState: LiveData<LoginUiState> get() = _loginState

    fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "Email is required"
            else -> null
        }
    }

    fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> "Password is required"
            else -> null
        }
    }

    fun validateInputs(email: String, password: String): Boolean {
        val emailError = validateEmail(email)
        val passwordError = validatePassword(password)

        if (emailError != null || passwordError != null) {
            _loginState.value = LoginUiState.ValidationError(emailError, passwordError)
            return false
        }
        return true
    }

    // Function to handle the login process
    fun loginUser(email: String, password: String) {
        if (!validateInputs(email, password)) {
            return
        }

        viewModelScope.launch {
            try {
                _loginState.value = LoginUiState.Loading
                val result = loginUserUseCase(email, password)
                _loginState.value = when (result) {
                    is LoginResult.Success -> LoginUiState.Success
                    is LoginResult.InvalidCredentials -> LoginUiState.Error("Incorrect email or password")
                    is LoginResult.Error -> LoginUiState.Error(result.message)
                }
            } catch (e: Exception) {
                _loginState.value = LoginUiState.Error(
                    e.localizedMessage ?: "An unexpected error occurred"
                )
            }
        }
    }

    // Function to reset the state back to idle
    fun resetState() {
        _loginState.value = LoginUiState.Idle
    }
}