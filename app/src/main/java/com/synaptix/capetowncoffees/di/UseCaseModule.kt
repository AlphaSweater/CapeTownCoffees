package com.synaptix.capetowncoffees.di

import com.synaptix.capetowncoffees.domain.repository.UserRepository
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.LogoutUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.RegisterUserUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    // ===================== AUTH USE CASES =====================
    @Provides
    @Singleton
    fun provideLoginUserUseCase(userRepository: UserRepository): LoginUserUseCase {
        return LoginUserUseCase(userRepository)
    }

    @Provides
    @Singleton
    fun provideLogoutUserUseCase(userRepository: UserRepository): LogoutUserUseCase {
        return LogoutUserUseCase(userRepository)
    }

    @Provides
    @Singleton
    fun provideRegisterUserUseCase(userRepository: UserRepository): RegisterUserUseCase {
        return RegisterUserUseCase(userRepository)
    }
    // =================== END AUTH USE CASES ===================

    // Add other use case groups below as needed
}