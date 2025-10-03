package com.synaptix.capetowncoffees.util

import android.location.Location
import com.google.android.gms.maps.model.LatLng

/**
 * Extension function to calculate distance between current location and a target LatLng
 * @param currentLocation The current location as LatLng
 * @return Formatted distance string (e.g., "500 m away" or "1.2 km away")
 */
fun LatLng?.calculateDistanceTo(target: LatLng?): String {
    if (this == null || target == null) return ""
    
    val results = FloatArray(1)
    Location.distanceBetween(
        this.latitude,
        this.longitude,
        target.latitude,
        target.longitude,
        results
    )
    
    val distanceInMeters = results[0]
    return if (distanceInMeters < 1000) {
        "${String.format("%.0f", distanceInMeters)} m away"
    } else {
        val distanceInKm = distanceInMeters / 1000
        "${String.format("%.1f", distanceInKm)} km away"
    }
}
