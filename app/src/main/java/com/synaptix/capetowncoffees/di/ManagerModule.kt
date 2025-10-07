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

package com.synaptix.capetowncoffees.di

import com.synaptix.capetowncoffees.domain.usecase.auth.AuthManager
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.RegisterUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.LogoutUserUseCase
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginWithGoogleUseCase
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
        loginWithGoogleUseCase: LoginWithGoogleUseCase,
        ICoffeeUserRepository: ICoffeeUserRepository
    ): AuthManager {
        return AuthManager(
            loginUserUseCase,
            registerUserUseCase,
            logoutUserUseCase,
            loginWithGoogleUseCase,
            ICoffeeUserRepository
        )
    }
    // =================== END AUTH MANAGER ===================
    // Add other managers below as needed
}