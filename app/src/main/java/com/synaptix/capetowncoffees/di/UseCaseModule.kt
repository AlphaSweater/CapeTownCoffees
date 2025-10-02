package com.synaptix.capetowncoffees.di

import com.synaptix.capetowncoffees.domain.repository.IListRepository
import com.synaptix.capetowncoffees.domain.repository.IUserRepository
import com.synaptix.capetowncoffees.domain.usecase.auth.LoginUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.LogoutUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.auth.RegisterUserUseCase
import com.synaptix.capetowncoffees.domain.usecase.savedLists.CreateListUseCase
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
    fun provideLoginUserUseCase(IUserRepository: IUserRepository): LoginUserUseCase {
        return LoginUserUseCase(IUserRepository)
    }

    @Provides
    @Singleton
    fun provideLogoutUserUseCase(IUserRepository: IUserRepository): LogoutUserUseCase {
        return LogoutUserUseCase(IUserRepository)
    }

    @Provides
    @Singleton
    fun provideRegisterUserUseCase(IUserRepository: IUserRepository): RegisterUserUseCase {
        return RegisterUserUseCase(IUserRepository)
    }

    @Provides
    @Singleton
    fun provideCreateUserUseCase(IListRepository: IListRepository): CreateListUseCase {
        return CreateListUseCase(IListRepository)
    }
    // =================== END AUTH USE CASES ===================

    // Add other use case groups below as needed
}