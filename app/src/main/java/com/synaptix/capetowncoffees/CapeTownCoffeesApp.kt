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

package com.synaptix.capetowncoffees

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class CapeTownCoffeesApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            // Plant the custom tree
            Timber.plant(Timber.DebugTree())
        }

        CoffeeTimeUtils.defaultPrefsProvider = { CoffeeTimeUtils.DisplayPrefs.from(applicationContext) }

        // Apply saved theme choice (defaults to SYSTEM)
        ThemeManager.applySavedTheme(this)

        // Fetch and persist initial FCM token (if user logged in)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            try {
                val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
                CoroutineScope(Dispatchers.IO).launch {
                    Timber.d("FCM initial token: $token")
                    runCatching { entryPoint.userRepository().updateFcmToken(token) }
                        .onSuccess { Timber.d("Saved FCM token on app start") }
                        .onFailure { Timber.w(it, "Failed to update FCM token on app start (likely no user yet)") }
                }
            } catch (t: Throwable) {
                Timber.w(t, "Hilt entry point not available yet for token update")
            }
        }

        // Also reactively save token once a user logs in
        try {
            val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
            CoroutineScope(Dispatchers.IO).launch {
                entryPoint.userRepository().observeAuthState().collectLatest { loggedIn ->
                    if (loggedIn) {
                        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            Timber.d("FCM token on login: $token")
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching { entryPoint.userRepository().updateFcmToken(token) }
                                    .onSuccess { Timber.d("Saved FCM token after login") }
                                    .onFailure { Timber.w(it, "Failed to save FCM token after login") }
                            }
                        }.addOnFailureListener { e ->
                            Timber.w(e, "Failed to get FCM token after login")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "Auth observer setup failed")
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun userRepository(): ICoffeeUserRepository
}

