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
import timber.log.Timber
import javax.inject.Inject

// ─────────── UI State ───────────
// Models all states the screen can be in; kept small and explicit for easy rendering.
sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
    data class ValidationError(
        val emailError: String? = null,
        val passwordError: String? = null
    ) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    // Use cases encapsulate domain logic for credentials and Google auth
    private val loginUserUseCase: LoginUserUseCase,
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase
) : ViewModel() {

    // ─────────── Constants ───────────
    // Keep user-facing messages in one place to avoid typos and ease reuse.
    private companion object {
        private const val MSG_EMAIL_REQUIRED = "Email is required"
        private const val MSG_PASSWORD_REQUIRED = "Password is required"
        private const val MSG_INVALID_CREDS = "Incorrect email or password"
        private const val MSG_GOOGLE_GENERIC = "Could not sign in with Google"
        private const val MSG_UNEXPECTED = "An unexpected error occurred"
        private const val MSG_UNEXPECTED_GENERIC = "Unexpected error"
    }

    // ─────────── State ───────────
    // Mutable backing state; expose as LiveData to the UI.
    private val _loginState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    public val loginState: LiveData<LoginUiState> get() = _loginState

    // ─────────── Validation ───────────
    // Basic non-empty checks; we log for traceability during debugging.
    public fun validateEmail(email: String): String? {
        Timber.d("validateEmail called with: %s", email)
        return if (email.isBlank()) {
            Timber.d("Email validation failed: blank email")
            MSG_EMAIL_REQUIRED
        } else {
            null
        }
    }

    public fun validatePassword(password: String): String? {
        Timber.d("validatePassword called with: %s", password)
        return if (password.isBlank()) {
            Timber.d("Password validation failed: blank password")
            MSG_PASSWORD_REQUIRED
        } else {
            null
        }
    }

    // Returns true only when both inputs are valid; also emits field-level errors.
    public fun validateInputs(email: String, password: String): Boolean {
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

    // ─────────── Actions: Credentials Login ───────────
    // Launches the login flow and posts UI states as the result progresses.
    public fun loginUser(email: String, password: String) {
        Timber.d("loginUser called with email=%s", email)
        if (!validateInputs(email, password)) return

        viewModelScope.launch {
            try {
                Timber.d("Login process started for email=%s", email)
                _loginState.value = LoginUiState.Loading
                when (val result = loginUserUseCase(email, password)) {
                    is LoginResult.Success -> {
                        Timber.d("Login successful for email=%s", email)
                        _loginState.value = LoginUiState.Success
                    }
                    is LoginResult.InvalidCredentials -> {
                        Timber.d("Login failed: Invalid credentials for email=%s", email)
                        _loginState.value = LoginUiState.Error(MSG_INVALID_CREDS)
                    }
                    is LoginResult.Error -> {
                        Timber.d("Login failed: Error for email=%s, message=%s", email, result.message)
                        _loginState.value = LoginUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during login for email=%s", email)
                _loginState.value = LoginUiState.Error(e.localizedMessage ?: MSG_UNEXPECTED)
            }
        }
    }

    // ─────────── Actions: Google Login ───────────
    // Exchanges a Google ID token for app credentials; mirrors the same state machine.
    public fun loginWithGoogleToken(idToken: String) {
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
                        _loginState.value = LoginUiState.Error(MSG_GOOGLE_GENERIC)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during Google login")
                _loginState.value = LoginUiState.Error(e.localizedMessage ?: MSG_UNEXPECTED_GENERIC)
            }
        }
    }

    // ─────────── Utilities ───────────
    // Helpful after a one-off success/error to avoid repeat handling on re-subscription.
    public fun resetState() {
        Timber.d("resetState called")
        _loginState.value = LoginUiState.Idle
    }
}
