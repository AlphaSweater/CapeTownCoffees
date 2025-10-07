package com.synaptix.capetowncoffees.di

import com.synaptix.capetowncoffees.data.repository.InMemoryParamsRepository
import com.synaptix.capetowncoffees.domain.repository.ParamsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ParamsModule {
    @Binds
    @Singleton
    abstract fun bindParamsRepository(impl: InMemoryParamsRepository): ParamsRepository
}