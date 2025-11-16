package com.synaptix.capetowncoffees.util

import android.content.SharedPreferences
import androidx.core.content.edit

class UserPrefs(
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val PREF_KEY_USER_OFFLINE_MODE = "user_forced_offline_mode"
    }

    fun isUserOfflineMode(): Boolean = sharedPreferences.getBoolean(PREF_KEY_USER_OFFLINE_MODE, false)

    fun setUserOfflineMode(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_KEY_USER_OFFLINE_MODE, enabled) }
    }
}
