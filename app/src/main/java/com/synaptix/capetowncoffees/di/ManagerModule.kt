package com.synaptix.capetowncoffees.di

import com.synaptix.capetowncoffees.domain.usecase.auth.AuthManager
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.RegisterUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.LogoutUserUseCase
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {
    // ===================== AUTH MANAGER =====================
    @Provides
    @Singleton
    fun provideAuthManager(
        loginUserUseCase: LoginUserUseCase,
        registerUserUseCase: RegisterUserUseCase,
        logoutUserUseCase: LogoutUserUseCase,
        ICoffeeUserRepository: ICoffeeUserRepository
    ): AuthManager {
        return AuthManager(
            loginUserUseCase,
            registerUserUseCase,
            logoutUserUseCase,
            ICoffeeUserRepository
        )
    }
    // =================== END AUTH MANAGER ===================
    // Add other managers below as needed
}