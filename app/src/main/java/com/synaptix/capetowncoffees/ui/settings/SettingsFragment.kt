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
//* ChatGPT assisted in designing and structuring this Fragment, including lifecycle
//handling, navigation setup, and interaction with the ViewModel.
//* It also provided guidance on ConstraintLayout usage and UI event handling.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.ThemeManager
import com.synaptix.capetowncoffees.data.connectivity.OfflineModeManager
import com.synaptix.capetowncoffees.databinding.FragmentSettingsBinding
import com.synaptix.capetowncoffees.domain.usecase.auth.AuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject
    lateinit var authManager: AuthManager
    @Inject
    lateinit var offlineModeManager: OfflineModeManager

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar back
        binding.root.findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // --- Toggles ---
        // Dark mode (ON = dark, OFF = light; keep SYSTEM by long-press if you want later)
        val darkSwitch: MaterialSwitch = binding.switchDarkMode
        darkSwitch.isChecked = when (ThemeManager.getSavedMode(requireContext())) {
            ThemeManager.ThemeMode.DARK -> true
            ThemeManager.ThemeMode.LIGHT -> false
            ThemeManager.ThemeMode.SYSTEM -> {
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
            }
        }
        darkSwitch.setOnCheckedChangeListener { _, checked ->
            ThemeManager.setTheme(
                requireContext(),
                if (checked) ThemeManager.ThemeMode.DARK else ThemeManager.ThemeMode.LIGHT
            )
        }

        // Push notifications (wire to your pref/FCM manager if needed)
        val pushSwitch: MaterialSwitch = binding.switchPushNotifications
        // Example: loadSavedPushEnabled()
        pushSwitch.isChecked = true
        pushSwitch.setOnCheckedChangeListener { _, enabled ->
            // save to prefs / enable-disable notifications
            Snackbar.make(binding.settingsRoot, if (enabled) R.string.enabled else R.string.disabled, Snackbar.LENGTH_SHORT).show()
        }

        // Offline Mode toggle
        val offlineSwitch: MaterialSwitch = binding.switchOfflineMode
        offlineSwitch.isChecked = offlineModeManager.getUserOfflineMode()
        offlineSwitch.setOnCheckedChangeListener { _, enabled ->
            lifecycleScope.launch {
                offlineModeManager.setUserOfflineMode(enabled)
                val messageRes = if (enabled) R.string.offline_mode_enabled else R.string.offline_mode_disabled
                Snackbar.make(binding.settingsRoot, messageRes, Snackbar.LENGTH_SHORT).show()
            }
        }

        // --- Row clicks ---
        binding.layoutPrivacySettings.setOnClickListener {
            findNavController().navigate(R.id.privacyPolicyFragment)
        }

        binding.layoutClearCache.setOnClickListener { confirmClearCache() }

        binding.layoutDeleteAccount.setOnClickListener { confirmDeleteAccount() }

        binding.layoutLogout.setOnClickListener { confirmLogout() }

        // Language Selection
        updateSelectedLanguage()
        binding.layoutLanguage.setOnClickListener { showLanguageSelectionDialog() }
    }

    private fun updateSelectedLanguage() {
        val currentLocale = AppCompatDelegate.getApplicationLocales()[0]
        val language = currentLocale?.displayLanguage ?: "English"
        binding.selectedLanguage.text = language
    }


    private fun showLanguageSelectionDialog() {
        val languages = arrayOf("English", "Afrikaans", "Xhosa")
        val languageCodes = arrayOf("en", "af", "xh")

        val currentLocale = AppCompatDelegate.getApplicationLocales()[0]
        val currentLanguageCode = currentLocale?.language ?: "en"
        val checkedItem = languageCodes.indexOf(currentLanguageCode).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_display_language)
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val selectedLanguageCode = languageCodes[which]
                val locale = Locale(selectedLanguageCode)
                val localeList = LocaleListCompat.create(locale)
                AppCompatDelegate.setApplicationLocales(localeList)
                updateSelectedLanguage()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // region Confirmations

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.logout) { _, _ -> performLogout() }
            .show()
    }

    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_account)
            .setMessage(R.string.delete_account_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> deleteAccount() }
            .show()
    }

    private fun confirmClearCache() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_cache)
            .setMessage(R.string.clear_cache_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear_cache) { _, _ -> clearCache() }
            .show()
    }

    // endregion

    // region Actions

    private fun performLogout() {
        uiScope.launch {
            try {
                when (authManager.logout()) {
                    is com.synaptix.capetowncoffees.domain.usecase.auth.LogoutResult.Success -> {
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                        findNavController().navigate(R.id.signInFragment, null, navOptions)
                    }
                    else -> snackError(getString(R.string.logout_failed))
                }
            } catch (e: Exception) {
                snackError(getString(R.string.generic_error))
            }
        }
    }

    private fun deleteAccount() {
        uiScope.launch {
            try {
                val result = authManager.deleteAccount()
                if (result.isSuccess) {
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                    findNavController().navigate(R.id.signInFragment, null, navOptions)
                    Snackbar.make(binding.settingsRoot, R.string.account_deleted, Snackbar.LENGTH_LONG).show()
                } else {
                    snackError(getString(R.string.delete_failed))
                }
            } catch (e: Exception) {
                snackError(getString(R.string.generic_error))
            }
        }
    }

    private fun clearCache() {
        uiScope.launch(Dispatchers.IO) {
            try {
                // Clear internal cache
                requireContext().cacheDir?.let { clearDirChildren(it) }
                // Clear external cache
                requireContext().externalCacheDir?.let { clearDirChildren(it) }

                launch(Dispatchers.Main) {
                    Snackbar.make(binding.settingsRoot, R.string.cache_cleared, Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    snackError(getString(R.string.generic_error))
                }
            }
        }
    }

    private fun clearDirChildren(dir: File) {
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                clearDirChildren(child)
            }
            // Delete children; keep the root directory
            runCatching { child.delete() }
        }
    }

    // endregion

    private fun snackError(message: String) {
        Snackbar.make(binding.settingsRoot, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
