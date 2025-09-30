package com.synaptix.capetowncoffees

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.appbar.MaterialToolbar
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Ensure that after the splash, we use the main app theme on pre-Android 12
        setTheme(R.style.Theme_CapeTownCoffees)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        // Show bottom nav only on main destinations
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.signInFragment, R.id.signUpFragment -> bottomNav.visibility =
                    android.view.View.GONE

                else -> bottomNav.visibility = android.view.View.VISIBLE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.theme_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme_light -> {
                ThemeManager.setTheme(this, ThemeManager.ThemeMode.LIGHT)
                true
            }
            R.id.action_theme_dark -> {
                ThemeManager.setTheme(this, ThemeManager.ThemeMode.DARK)
                true
            }
            R.id.action_theme_system -> {
                ThemeManager.setTheme(this, ThemeManager.ThemeMode.SYSTEM)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}