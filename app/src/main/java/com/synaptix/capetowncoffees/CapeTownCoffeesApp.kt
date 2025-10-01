package com.synaptix.capetowncoffees

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
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

        // Apply saved theme choice (defaults to SYSTEM)
        ThemeManager.applySavedTheme(this)
    }
}

