package com.synaptix.capetowncoffees.domain.usecase.auth

import kotlinx.coroutines.flow.Flow
import com.synaptix.capetowncoffees.domain.repository.UserRepository
import javax.inject.Inject

class AuthManager @Inject constructor(
    private val loginUserUseCase: LoginUserUseCase,
    private val registerUserUseCase: RegisterUserUseCase,
    private val logoutUserUseCase: LogoutUserUseCase,
    private val userRepository: UserRepository // Inject UserRepository for auth state observation
) {
    suspend fun login(email: String, password: String): LoginResult {
        return loginUserUseCase(email, password)
    }

    suspend fun register(email: String, password: String, firstName: String? = null, lastName: String? = null): RegistrationResult {
        return registerUserUseCase(email, password, firstName, lastName)
    }

    suspend fun logout(): LogoutResult {
        return logoutUserUseCase()
    }

    fun observeAuthState(): Flow<Boolean> {
        return userRepository.observeAuthState()
    }
}
