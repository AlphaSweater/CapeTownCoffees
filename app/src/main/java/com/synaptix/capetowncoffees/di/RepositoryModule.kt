package com.synaptix.capetowncoffees.di

import com.synaptix.capetowncoffees.data.repository.IUserRepositoryImpl
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
        impl: IUserRepositoryImpl
    ): IUserRepository

}