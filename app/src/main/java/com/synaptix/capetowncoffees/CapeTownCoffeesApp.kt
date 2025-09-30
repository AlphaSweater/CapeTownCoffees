package com.synaptix.capetowncoffees

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class CapeTownCoffeesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply saved theme choice (defaults to SYSTEM)
        ThemeManager.applySavedTheme(this)
    }
}
