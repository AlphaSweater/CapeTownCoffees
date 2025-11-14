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

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
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

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    // =================== END FIREBASE SERVICES ===================

    // ===================== APP CONTEXT ===========================
    @Provides
    @Singleton
    fun provideAppContext(@ApplicationContext context: Context): Context = context

    // Example: SharedPreferences
//    @Provides
//    @Singleton
//    fun provideSharedPreferences(@ApplicationContext context: Context) =
//        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // =================== END APP CONTEXT =========================
}