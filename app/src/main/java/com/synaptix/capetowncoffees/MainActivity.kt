package com.synaptix.capetowncoffees

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Ensure that after the splash, we use the main app theme on pre-Android 12
        setTheme(R.style.Theme_CapeTownCoffees)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // No need to set up action bar with navigation controller
        // as we're not using an ActionBar in this app

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
}