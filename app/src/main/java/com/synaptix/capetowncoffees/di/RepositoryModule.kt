package com.synaptix.capetowncoffees.di

import com.synaptix.capetowncoffees.data.repository.PlacesApiRepository
import com.synaptix.capetowncoffees.data.repository.UserListRepository
import com.synaptix.capetowncoffees.domain.repository.IUserListRepository
import com.synaptix.capetowncoffees.data.repository.UserRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import com.synaptix.capetowncoffees.domain.repository.IUserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
// ===================== REPOSITORY BINDINGS =====================
    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepository
    ): IUserRepository

    @Binds
    @Singleton
    abstract fun bindSavedListRepository(
        impl: UserListRepository
    ): IUserListRepository

    @Binds
    @Singleton
    abstract fun bindPlacesApiRepository(
        impl: PlacesApiRepository
    ): IPlacesApiRepository

}