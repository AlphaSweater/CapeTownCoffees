package com.synaptix.capetowncoffees

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var permissionDialogShown = false
    private var permissionRequestInProgress = false
    private var sentToSettingsOnce = false
    private var permissionRequestedOnce = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ensure that after the splash, we use the main app theme on pre-Android 12
        setTheme(R.style.Theme_CapeTownCoffees)
        super.onCreate(savedInstanceState)

//        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContentView(R.layout.activity_main)

        // ---- Set the spacer height exactly once (no stacking on resumes) ----
        setStatusSpacerOnce(rootId = R.id.root_container, spacerId = R.id.status_spacer)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

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
        // Reset permissionRequestedOnce if permission was revoked in settings
        if (!hasLocationPermission()) {
            permissionRequestedOnce = false
        }
        // Only check permissions if no dialog/request is active
        if (!permissionDialogShown && !permissionRequestInProgress) {
            checkLocationPermissionOnResume()
        }

        dumpEdgeToEdge("onResume")
    }

    /** Very small inline debugger: logs flags, colors and current insets. */
    private fun dumpEdgeToEdge(where: String) {
        val w = window
        val v = w.decorView
        val flags = w.attributes.flags
        val translucentStatus = (flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) != 0
        val translucentNav = (flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION) != 0
        val noLimits = (flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0

        val sysUi = v.systemUiVisibility
        val layoutFullscreen = (sysUi and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0
        val layoutStable = (sysUi and View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0

        val rootInsets = ViewCompat.getRootWindowInsets(v)
        val status = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())
        val nav = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())
        val cut = rootInsets?.displayCutout?.boundingRects

        Timber.tag("E2E-DUMP").i("[$where] statusBarColor=#%08X navBarColor=#%08X", w.statusBarColor, w.navigationBarColor)
        Timber.tag("E2E-DUMP").i("[$where] flags: translucentStatus=%s translucentNav=%s noLimits=%s",
            translucentStatus, translucentNav, noLimits)
        Timber.tag("E2E-DUMP").i("[$where] sysUi: LAYOUT_FULLSCREEN=%s LAYOUT_STABLE=%s", layoutFullscreen, layoutStable)
        Timber.tag("E2E-DUMP").i("[$where] insets: statusTop=%d navBottom=%d cutout=%s",
            status?.top ?: -1, nav?.bottom ?: -1, cut?.toString() ?: "[]")
    }

    private fun checkLocationPermissionOnResume() {
        val hasPerm = hasLocationPermission()
        Timber.i("Checking location permission: hasLocationPermission() = $hasPerm")
        val shouldShowFine = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val shouldShowCoarse = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        Timber.i("shouldShowRequestPermissionRationale: fine=$shouldShowFine, coarse=$shouldShowCoarse")

        if (hasPerm) {
            permissionDialogShown = false
            permissionRequestInProgress = false
            permissionRequestedOnce = false
            Timber.i("Location permission already granted. Requesting location.")
            requestLocationAccess()
            return
        }

        if (!shouldShowFine && !shouldShowCoarse) {
            if (permissionRequestedOnce) {
                Timber.i("Permission denied permanently or 'Don't ask again' selected. Showing guide dialog.")
                permissionDialogShown = true
                showPermissionSettingsDialog()
                return
            } else {
                Timber.i("First launch or permission never requested. Requesting permission.")
                permissionRequestInProgress = true
                permissionRequestedOnce = true
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        // Permission denied, can show rationale
        Timber.i("Permission denied, showing rationale and requesting permission.")
        permissionRequestInProgress = true
        permissionRequestedOnce = true
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestLocationAccess() {
        // Always use lastLocation to trigger permission dialog if needed
        try {
            fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    Timber.i("Location access successful.")
                } else {
                    Timber.i("Location access attempted, but no location available.")
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException when requesting location.")
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        Timber.i("hasLocationPermission() check: fine=$fine, coarse=$coarse")
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
                permissionRequestedOnce = false
                requestLocationAccess()
            } else {
                Timber.i("User denied location permission.")
                permissionDialogShown = true
                showPermissionSettingsDialog()
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

    /**
     * Measures status/cutout inset once at startup and sets the spacer height.
     * If inset is 0 (Samsung case), spacer remains 0. If > 0 (Pixel emulator), it adds that gap.
     */
    private fun setStatusSpacerOnce(rootId: Int, spacerId: Int) {
        val root = findViewById<View>(rootId) ?: return
        val spacer = findViewById<View>(spacerId) ?: return

        // One-shot listener: apply then remove so it won't run on every resume/config change.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val topInset = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top

            // Idempotent: set exact height (no accumulation)
            spacer.layoutParams = spacer.layoutParams.apply { height = topInset }
            spacer.requestLayout()

            Timber.tag("E2E-ONCE").i("Startup spacerTop=%d", topInset)

            // Remove listener after first application so it won't re-apply/stack
            ViewCompat.setOnApplyWindowInsetsListener(root, null)

            // Return insets unchanged
            insets
        }

        // Kick off first insets dispatch
        ViewCompat.requestApplyInsets(root)
    }
}