package com.synaptix.capetowncoffees.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ===================== API KEYS =============================
    @Provides
    @Singleton
    @Named("PLACES_API_KEY")
    fun providePlacesApiKey(): String = BuildConfig.PLACES_API_KEY
    // =================== END API KEYS ============================

    // ===================== FIREBASE SERVICES =====================
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    // =================== END FIREBASE SERVICES ===================

    // Example: SharedPreferences
//    @Provides
//    @Singleton
//    fun provideSharedPreferences(@ApplicationContext context: Context) =
//        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
}