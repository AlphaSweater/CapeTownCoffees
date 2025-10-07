//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.domain.usecase.auth

import kotlinx.coroutines.flow.Flow
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import javax.inject.Inject

class AuthManager @Inject constructor(
    private val loginUserUseCase: LoginUserUseCase,
    private val registerUserUseCase: RegisterUserUseCase,
    private val logoutUserUseCase: LogoutUserUseCase,
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase,
    private val coffeeUserRepository: ICoffeeUserRepository // Inject UserRepository for auth state observation
) {
    suspend fun login(email: String, password: String): LoginResult {
        return loginUserUseCase(email, password)
    }

    suspend fun register(email: String, password: String, fullName: String): RegistrationResult {
        return registerUserUseCase(email, password, fullName)
    }

    suspend fun loginWithGoogle(idToken: String): LoginResult =
        loginWithGoogleUseCase(idToken)

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
