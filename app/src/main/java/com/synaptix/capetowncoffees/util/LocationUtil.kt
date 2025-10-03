package com.synaptix.capetowncoffees.util

import android.Manifest
import android.content.Context
import android.location.Location
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

    /**
     * Calculates the distance between two LatLng points in meters
     */
    fun calculateDistance(latLng1: LatLng, latLng2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            latLng1.latitude, latLng1.longitude,
            latLng2.latitude, latLng2.longitude,
            results
        )
        return results[0]
    }

    /**
     * Formats the distance in meters to a user-friendly string (e.g., "1.2 km away" or "500 m away")
     */
    fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()} m away"
        } else {
            val km = distanceMeters / 1000
            "%.1f km away".format(km)
        }
    }

    /**
     * Gets the formatted distance between two points
     */
    fun getFormattedDistance(latLng1: LatLng, latLng2: LatLng): String {
        val distance = calculateDistance(latLng1, latLng2)
        return formatDistance(distance)
    }
}

