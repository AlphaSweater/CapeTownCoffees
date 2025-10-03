package com.synaptix.capetowncoffees.di

import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.LogoutUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.RegisterUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeList.CreateListUseCase
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
    fun provideLoginUserUseCase(ICoffeeUserRepository: ICoffeeUserRepository): LoginUserUseCase {
        return LoginUserUseCase(ICoffeeUserRepository)
    }

    @Provides
    @Singleton
    fun provideLogoutUserUseCase(ICoffeeUserRepository: ICoffeeUserRepository): LogoutUserUseCase {
        return LogoutUserUseCase(ICoffeeUserRepository)
    }

    @Provides
    @Singleton
    fun provideRegisterUserUseCase(ICoffeeUserRepository: ICoffeeUserRepository): RegisterUserUseCase {
        return RegisterUserUseCase(ICoffeeUserRepository)
    }

    @Provides
    @Singleton
    fun provideCreateUserUseCase(ICoffeeListRepository: ICoffeeListRepository): CreateListUseCase {
        return CreateListUseCase(ICoffeeListRepository)
    }
    // =================== END AUTH USE CASES ===================

    // Add other use case groups below as needed
}