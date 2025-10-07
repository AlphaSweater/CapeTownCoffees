package com.synaptix.capetowncoffees.ui.auth.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginResult
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginWithGoogleUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.RegisterUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.RegistrationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────── UI State ───────────
// Small state machine for the register screen; keeps rendering logic simple.
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
    // Use cases hold the domain logic; VM coordinates inputs/outputs
    private val registerUserUseCase: RegisterUserUseCase,
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase
) : ViewModel() {

    // ─────────── Constants ───────────
    // Centralize messages to avoid typos and make reuse easier.
    private companion object {
        private const val MSG_EMAIL_REQUIRED = "Email is required"
        private const val MSG_EMAIL_INVALID = "Invalid email format"
        private const val MSG_PASSWORD_REQUIRED = "Password is required"
        private const val MSG_PASSWORD_LENGTH = "Password must be at least 8 characters"
        private const val MSG_PASSWORD_UPPER = "Password must contain at least one uppercase letter"
        private const val MSG_PASSWORD_NUMBER = "Password must contain at least one number"
        private const val MSG_PASSWORD_SPECIAL = "Password must contain at least one special character"
        private const val MSG_CONFIRM_REQUIRED = "Please confirm your password"
        private const val MSG_CONFIRM_MISMATCH = "Passwords do not match"
        private const val MSG_NAME_REQUIRED = "Name is required"
        private const val MSG_NAME_LENGTH = "Name must be at least 2 characters"
        private const val MSG_EMAIL_EXISTS = "Email already in use"
        private const val MSG_UNKNOWN = "Unknown error"
        private const val MSG_GOOGLE_GENERIC = "Could not sign in with Google"
    }

    // ─────────── State ───────────
    // Backing state is mutable; expose immutable view to UI.
    private val _registerState = MutableLiveData<RegisterUiState>(RegisterUiState.Idle)
    public val registerState: LiveData<RegisterUiState> get() = _registerState

    // ─────────── Validation ───────────
    // Straightforward field validation; we keep it readable and specific per field.
    public fun validateEmail(email: String): String? {
        return when {
            email.isBlank() -> MSG_EMAIL_REQUIRED
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> MSG_EMAIL_INVALID
            else -> null
        }
    }

    public fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> MSG_PASSWORD_REQUIRED
            password.length < 8 -> MSG_PASSWORD_LENGTH
            !password.matches(Regex(".*[A-Z].*")) -> MSG_PASSWORD_UPPER
            !password.matches(Regex(".*[0-9].*")) -> MSG_PASSWORD_NUMBER
            !password.matches(Regex(".*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) -> MSG_PASSWORD_SPECIAL
            else -> null
        }
    }

    public fun validatePasswordConfirmation(password: String, confirmPassword: String): String? {
        return when {
            confirmPassword.isBlank() -> MSG_CONFIRM_REQUIRED
            confirmPassword != password -> MSG_CONFIRM_MISMATCH
            else -> null
        }
    }

    public fun validateName(name: String): String? {
        return when {
            name.isBlank() -> MSG_NAME_REQUIRED
            name.length < 2 -> MSG_NAME_LENGTH
            else -> null
        }
    }

    // Returns true when all fields pass validation; also emits field-level errors for the UI.
    public fun validateInputs(name: String, email: String, password: String, confirmPassword: String): Boolean {
        val nameError = validateName(name)
        val emailError = validateEmail(email)
        val passwordError = validatePassword(password)
        val confirmPasswordError = validatePasswordConfirmation(password, confirmPassword)

        if (nameError != null || emailError != null || passwordError != null || confirmPasswordError != null) {
            _registerState.value = RegisterUiState.ValidationError(
                nameError = nameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError
            )
            return false
        }
        return true
    }

    // ─────────── Actions: Email/Password Register ───────────
    // Triggers registration and posts UI states along the way.
    public fun registerUser(name: String, email: String, password: String, confirmPassword: String) {
        if (!validateInputs(name, email, password, confirmPassword)) return

        viewModelScope.launch {
            try {
                _registerState.value = RegisterUiState.Loading
                val fullName = name.trim()
                when (val result = registerUserUseCase(email, password, fullName)) {
                    is RegistrationResult.Success -> _registerState.value = RegisterUiState.Success
                    is RegistrationResult.EmailExists -> _registerState.value = RegisterUiState.Error(MSG_EMAIL_EXISTS)
                    is RegistrationResult.Error -> _registerState.value = RegisterUiState.Error(result.message)
                }
            } catch (e: Exception) {
                _registerState.value = RegisterUiState.Error(e.localizedMessage ?: MSG_UNKNOWN)
            }
        }
    }

    // ─────────── Actions: Google Register/Login ───────────
    // Exchanges Google ID token for app credentials; mirrors the same state machine.
    public fun registerWithGoogleToken(idToken: String) {
        _registerState.value = RegisterUiState.Loading
        viewModelScope.launch {
            try {
                when (val result: LoginResult = loginWithGoogleUseCase(idToken)) {
                    is LoginResult.Success -> _registerState.value = RegisterUiState.Success
                    is LoginResult.Error -> _registerState.value = RegisterUiState.Error(result.message)
                    is LoginResult.InvalidCredentials -> _registerState.value = RegisterUiState.Error(MSG_GOOGLE_GENERIC)
                }
            } catch (e: Throwable) {
                _registerState.value = RegisterUiState.Error(e.localizedMessage ?: MSG_UNKNOWN)
            }
        }
    }

    // ─────────── Utilities ───────────
    // Helpful after a one-off success/error to avoid repeat handling on re-subscribe.
    public fun resetState() {
        _registerState.value = RegisterUiState.Idle
    }
}
