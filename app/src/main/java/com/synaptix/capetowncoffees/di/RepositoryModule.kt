package com.synaptix.capetowncoffees.di

import com.synaptix.capetowncoffees.data.repository.PlacesApiRepository
import com.synaptix.capetowncoffees.data.repository.CoffeeListRepository
import com.synaptix.capetowncoffees.data.repository.CoffeePlaceRepository
import com.synaptix.capetowncoffees.data.repository.CoffeeReviewRepository
import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import com.synaptix.capetowncoffees.data.repository.CoffeeUserRepository
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import com.synaptix.capetowncoffees.domain.repository.ICoffeeReviewRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
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
    abstract fun bindCoffeeListRepository(
        impl: CoffeeListRepository
    ): ICoffeeListRepository

    @Binds
    @Singleton
    abstract fun bindCoffeePlaceRepository(
        impl: CoffeePlaceRepository
    ): ICoffeePlaceRepository

    @Binds
    @Singleton
    abstract fun bindCoffeeReviewRepository(
        impl: CoffeeReviewRepository
    ): ICoffeeReviewRepository

    @Binds
    @Singleton
    abstract fun bindCoffeeUserRepository(
        impl: CoffeeUserRepository
    ): ICoffeeUserRepository

    @Binds
    @Singleton
    abstract fun bindPlacesApiRepository(
        impl: PlacesApiRepository
    ): IPlacesApiRepository

}