package com.synaptix.capetowncoffees.util

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.tasks.await

object LocationUtil {
    /**
     * Requests the user's current location and returns a LatLng if available, or null otherwise.
     * Requires ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION permissions.
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getCurrentLocation(context: Context): LatLng? {
        val fusedLocationProviderClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)
        val location = try {
            fusedLocationProviderClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
        return location?.let { LatLng(it.latitude, it.longitude) }
    }
}

