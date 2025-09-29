package com.synaptix.capetowncoffees

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class CapeTownCoffeesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Force light theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
