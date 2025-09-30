package com.synaptix.capetowncoffees.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
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