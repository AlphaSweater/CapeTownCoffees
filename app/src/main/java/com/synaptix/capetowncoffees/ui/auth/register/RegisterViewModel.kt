package com.synaptix.capetowncoffees.ui.auth.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginWithGoogleUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.RegisterUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.RegistrationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
sealed class RegisterUiState {
    object Idle : RegisterUiState()
    object Loading : RegisterUiState()
    object Success : RegisterUiState()
    data class Error(val message: String) : RegisterUiState()
    data class ValidationError(
        val nameError: String? = null,
        val emailError: String? = null,
        val passwordError: String? = null,
        val confirmPasswordError: String? = null
    ) : RegisterUiState()
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUserUseCase: RegisterUserUseCase,
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase
) : ViewModel() {
    // Internal MutableLiveData for register state
    private val _registerState = MutableLiveData<RegisterUiState>(RegisterUiState.Idle)
    val registerState: LiveData<RegisterUiState> get() = _registerState

    fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Invalid email format"
            else -> null
        }
    }

    fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> "Password is required"
            password.length < 8 -> "Password must be at least 8 characters"
            !password.matches(Regex(".*[A-Z].*")) -> "Password must contain at least one uppercase letter"
            !password.matches(Regex(".*[0-9].*")) -> "Password must contain at least one number"
            !password.matches(Regex(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) ->
                "Password must contain at least one special character"
            else -> null
        }
    }

    fun validatePasswordConfirmation(password: String, confirmPassword: String): String? {
        return when {
            confirmPassword.isBlank() -> "Please confirm your password"
            confirmPassword != password -> "Passwords do not match"
            else -> null
        }
    }

    fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "Name is required"
            name.length < 2 -> "Name must be at least 2 characters"
            else -> null
        }
    }

    fun validateInputs(name: String, email: String, password: String, confirmPassword: String): Boolean {
        val nameError = validateName(name)
        val emailError = validateEmail(email)
        val passwordError = validatePassword(password)
        val confirmPasswordError = validatePasswordConfirmation(password, confirmPassword)

        if (nameError != null || emailError != null || passwordError != null || confirmPasswordError != null) {
            _registerState.value = RegisterUiState.ValidationError(
                nameError,
                emailError,
                passwordError,
                confirmPasswordError
            )
            return false
        }
        return true
    }

    // Function that handles user sign-up
    // Takes name, email and password, hashes the password securely
    // and creates a new UserEntity object to register the user
    fun registerUser(name: String, email: String, password: String, confirmPassword: String) {
        if (!validateInputs(name, email, password, confirmPassword)) {
            return
        }

        viewModelScope.launch {
            try {
                _registerState.value = RegisterUiState.Loading
                val fullName = name.trim()
                
                val result = registerUserUseCase.invoke(email, password, fullName)
                _registerState.value = when (result) {
                    is RegistrationResult.Success -> RegisterUiState.Success
                    is RegistrationResult.EmailExists -> RegisterUiState.Error("Email already in use")
                    is RegistrationResult.Error -> RegisterUiState.Error(result.message)
                }
            } catch (e: Exception) {
                _registerState.value = RegisterUiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun registerWithGoogleToken(idToken: String) {
        _registerState.value = RegisterUiState.Loading
        viewModelScope.launch {
            try {
                when (val r = loginWithGoogleUseCase(idToken)) {
                    is RegistrationResult -> {}
                    else -> {}
                }
            } catch (_: Throwable) {}

            when (val result = loginWithGoogleUseCase(idToken)) {
                is com.synaptix.capetowncoffees.domain.usecase.auth.LoginResult.Success ->
                    _registerState.value = RegisterUiState.Success
                is com.synaptix.capetowncoffees.domain.usecase.auth.LoginResult.Error ->
                    _registerState.value = RegisterUiState.Error(result.message)
                is com.synaptix.capetowncoffees.domain.usecase.auth.LoginResult.InvalidCredentials ->
                    _registerState.value = RegisterUiState.Error("Could not sign in with Google")
            }
        }
    }

    // Function to reset the state back to idle
    fun resetState() {
        _registerState.value = RegisterUiState.Idle
    }
}