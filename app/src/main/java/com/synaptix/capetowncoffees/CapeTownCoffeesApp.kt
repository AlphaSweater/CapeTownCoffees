package com.synaptix.capetowncoffees

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CapeTownCoffeesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Force light theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
