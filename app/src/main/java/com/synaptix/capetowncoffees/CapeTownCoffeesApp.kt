package com.synaptix.capetowncoffees

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CapeTownCoffeesApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            // Debug logging
            Timber.plant(Timber.DebugTree())
        }

        // Apply saved theme choice (defaults to SYSTEM)
        ThemeManager.applySavedTheme(this)
    }
}
