package com.synaptix.capetowncoffees

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var permissionDialogShown = false
    private var permissionRequestInProgress = false
    private var sentToSettingsOnce = false

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

    override fun onResume() {
        super.onResume()
        // Only check permissions if no dialog/request is active
        if (!permissionDialogShown && !permissionRequestInProgress) {
            checkLocationPermissionOnResume()
        }
    }

    private fun checkLocationPermissionOnResume() {
        if (hasLocationPermission()) {
            permissionDialogShown = false
            permissionRequestInProgress = false
            Timber.i("Location permission already granted. Proceeding as normal.")
            // Continue as normal
            return
        }
        // Always prompt for permission if not granted
        permissionRequestInProgress = true
        Timber.i("Prompting user for location permission.")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionRequestInProgress = false
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                Timber.i("User granted location permission.")
                permissionDialogShown = false
                sentToSettingsOnce = false
                recreate()
            } else {
                Timber.i("User denied location permission.")
                permissionDialogShown = true
                val canPromptFine = ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                )
                val canPromptCoarse = ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if ((!canPromptFine && !canPromptCoarse) || sentToSettingsOnce) {
                    Timber.i("Showing guide dialog to user for location permission in settings.")
                    showPermissionSettingsDialog()
                }
            }
        }
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs location access to function. Please enable location permission in settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                Timber.i("User chose to open app settings from guide dialog.")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
                permissionDialogShown = false
                sentToSettingsOnce = true // Track that user was sent to settings
            }
            .setNegativeButton("Close App") { _, _ ->
                Timber.i("User chose to close the app from guide dialog.")
                finish()
            }
            .setCancelable(false)
            .show()
    }
}