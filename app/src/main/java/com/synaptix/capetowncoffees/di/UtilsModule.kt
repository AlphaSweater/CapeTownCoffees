package com.synaptix.capetowncoffees.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.libraries.places.api.net.PlacesClient
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilsModule {

    @Provides
    @Singleton
    fun provideCoffeePlaceUtilsUseCase(
        @ApplicationContext context: Context,
        placesClient: PlacesClient,
        coffeePlaceRepository: ICoffeePlaceRepository
    ): CoffeePlaceUtilsUseCase {
        return CoffeePlaceUtilsUseCase(context, placesClient, coffeePlaceRepository)
    }

    @Provides
    @Singleton
    fun provideLocationUtil(
        @ApplicationContext context: Context,
        fusedLocationProviderClient: FusedLocationProviderClient
    ): LocationUtil {
        return LocationUtil(context, fusedLocationProviderClient)
    }
}
