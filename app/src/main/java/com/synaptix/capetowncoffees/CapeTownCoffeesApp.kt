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
            Timber.plant(CustomTimberTree())
        } else {
            // You can plant a ReleaseTree for production, or use the same CustomTimberTree
            Timber.plant(CustomTimberTree())
        }

        // Apply saved theme choice (defaults to SYSTEM)
        ThemeManager.applySavedTheme(this)
    }

    class CustomTimberTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val newTag = tag?.let { "Timber-$it" } ?: "Timber-Unknown"
            if (t != null) {
                Log.println(priority, newTag, "$message\n${Log.getStackTraceString(t)}")
            } else {
                Log.println(priority, newTag, message)
            }
        }
    }

}

