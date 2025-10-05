package com.synaptix.capetowncoffees.domain.usecase.auth

import kotlinx.coroutines.flow.Flow
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import javax.inject.Inject

class AuthManager @Inject constructor(
    private val loginUserUseCase: LoginUserUseCase,
    private val registerUserUseCase: RegisterUserUseCase,
    private val logoutUserUseCase: LogoutUserUseCase,
    private val coffeeUserRepository: ICoffeeUserRepository // Inject UserRepository for auth state observation
) {
    suspend fun login(email: String, password: String): LoginResult {
        return loginUserUseCase(email, password)
    }

    suspend fun register(email: String, password: String, fullName: String): RegistrationResult {
        return registerUserUseCase(email, password, fullName)
    }

    suspend fun logout(): LogoutResult {
        return logoutUserUseCase()
    }

    fun observeAuthState(): Flow<Boolean> {
        return coffeeUserRepository.observeAuthState()
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            coffeeUserRepository.deleteUserAccount()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
