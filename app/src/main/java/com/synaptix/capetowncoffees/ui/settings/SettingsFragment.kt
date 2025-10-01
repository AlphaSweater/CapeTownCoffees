package com.synaptix.capetowncoffees.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.NavOptions
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.ThemeManager
import com.synaptix.capetowncoffees.domain.usecase.auth.AuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject
    lateinit var authManager: AuthManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up back button click listener
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            findNavController().navigateUp()
        }

        // Appearance toggle: ON = Dark, OFF = Light
        val switchDark = view.findViewById<Switch>(R.id.switchDarkMode)
        switchDark?.let { sw ->
            val saved = ThemeManager.getSavedMode(requireContext())
            val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val initialChecked = when (saved) {
                ThemeManager.ThemeMode.DARK -> true
                ThemeManager.ThemeMode.LIGHT -> false
                ThemeManager.ThemeMode.SYSTEM -> isSystemDark
            }
            sw.isChecked = initialChecked

            sw.setOnCheckedChangeListener { _, checked ->
                val mode = if (checked) ThemeManager.ThemeMode.DARK else ThemeManager.ThemeMode.LIGHT
                ThemeManager.setTheme(requireContext(), mode)
            }
        }

        // Set up logout button
        view.findViewById<View>(R.id.layoutLogout)?.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLogoutConfirmation() {
        // You can use a DialogFragment or a simple dialog here
        // For simplicity, we'll use a basic dialog
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = authManager.logout()
                if (result is com.synaptix.capetowncoffees.domain.usecase.auth.LogoutResult.Success) {
                    // Navigate back to sign in screen and clear the back stack
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                    findNavController().navigate(R.id.signInFragment, null, navOptions)
                } else {
                    showError("Failed to logout. Please try again.")
                }
            } catch (e: Exception) {
                showError("An error occurred: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
