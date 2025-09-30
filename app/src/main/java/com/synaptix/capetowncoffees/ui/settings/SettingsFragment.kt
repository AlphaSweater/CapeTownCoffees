package com.synaptix.capetowncoffees.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.ThemeManager
import android.widget.Switch
import android.content.res.Configuration

class SettingsFragment : Fragment() {

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
    }
}
