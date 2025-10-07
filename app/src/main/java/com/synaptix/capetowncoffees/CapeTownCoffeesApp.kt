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
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils
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

        CoffeeTimeUtils.defaultPrefsProvider = { CoffeeTimeUtils.DisplayPrefs.from(applicationContext) }

        // Apply saved theme choice (defaults to SYSTEM)
        ThemeManager.applySavedTheme(this)
    }
}

