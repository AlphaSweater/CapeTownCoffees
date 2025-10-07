package com.synaptix.capetowncoffees.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Locale

/* ──────────────────────────────────────────────────────────────────────────────
 * LOCATION UTIL — Fetch user location
 * ────────────────────────────────────────────────────────────────────────────── */
class LocationUtil @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fused: FusedLocationProviderClient
) {

    /* ─────────────────────────────────────────────────────────────────────
     * Public: Fetch user's LatLng (suspend)
     * ───────────────────────────────────────────────────────────────────── */

    /**
     * Get current user coordinates using **fresh→fallback** strategy.
     *
     * 1) Try fresh fix with timeout.
     * 2) If not good enough, fall back to last known.
     * 3) Apply freshness/accuracy gates.
     */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    suspend fun getCurrentLatLng(
        timeoutMs: Long = 2_500L,
        highAccuracyWhenFine: Boolean = false,
        maxAgeMs: Long = 60_000L,   // accept fixes up to 60s old
        maxAccuracyM: Float = 100f  // ~city-block accuracy
    ): Result<LatLng> {
        if (!hasAnyPermission()) {
            return Result.failure(SecurityException("Location permission not granted"))
        }

        // 1) Try fresh
        freshLocation(timeoutMs, highAccuracyWhenFine).onSuccess { loc ->
            if (isGoodEnough(loc, maxAgeMs, maxAccuracyM)) return Result.success(loc.toLatLng())
        }

        // 2) Fallback to last known
        return lastKnownLocation().mapCatching { loc ->
            if (!isGoodEnough(loc, maxAgeMs, maxAccuracyM)) {
                throw NoSuchElementException("Last known location not fresh/accurate enough")
            }
            loc.toLatLng()
        }
    }

    /* ─────────────────────────────────────────────────────────────────────
     * Public: Fetch user's LatLng (callback variant)
     * ───────────────────────────────────────────────────────────────────── */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    fun getCurrentLatLng(
        scope: CoroutineScope,
        timeoutMs: Long = 2_500L,
        highAccuracyWhenFine: Boolean = false,
        maxAgeMs: Long = 60_000L,
        maxAccuracyM: Float = 100f,
        onResult: (Result<LatLng>) -> Unit
    ) = scope.async {
        val r = getCurrentLatLng(timeoutMs, highAccuracyWhenFine, maxAgeMs, maxAccuracyM)
        onResult(r)
        r
    }

    /* ─────────────────────────────────────────────────────────────────────
     * Internals
     * ───────────────────────────────────────────────────────────────────── */

    /** True if either FINE or COARSE permission is granted. */
    private fun hasAnyPermission(): Boolean =
        has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** True if FINE permission is granted. */
    private fun hasFine(): Boolean = has(Manifest.permission.ACCESS_FINE_LOCATION)

    /** Permission check helper. */
    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Attempt a fresh location fix with timeout and dynamic priority.
     * - FINE + highAccuracyWhenFine → HIGH_ACCURACY
     * - FINE only → BALANCED_POWER_ACCURACY
     * - COARSE only → LOW_POWER
     */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    private suspend fun freshLocation(
        timeoutMs: Long,
        highAccuracyWhenFine: Boolean
    ): Result<Location> = runCatching {
        val priority = when {
            hasFine() && highAccuracyWhenFine -> Priority.PRIORITY_HIGH_ACCURACY
            hasFine() -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            else -> Priority.PRIORITY_LOW_POWER
        }
        val token = CancellationTokenSource()
        try {
            withTimeout(timeoutMs) {
                @SuppressLint("MissingPermission")
                fused.getCurrentLocation(priority, token.token).await()
                    ?: throw NoSuchElementException("Fresh location unavailable")
            }
        } catch (t: TimeoutCancellationException) {
            throw t
        } finally {
            token.cancel() // ensure Play services stops if we timed out/cancelled
        }
    }

    /** Get last known location or fail if unavailable. */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    private suspend fun lastKnownLocation(): Result<Location> = runCatching {
        @SuppressLint("MissingPermission")
        fused.lastLocation.await() ?: throw NoSuchElementException("No last known location")
    }

    /** Return true if `loc` meets freshness (`maxAgeMs`) and accuracy (`maxAccuracyM`) gates. */
    private fun isGoodEnough(loc: Location, maxAgeMs: Long, maxAccuracyM: Float): Boolean {
        val ageOk = locationAgeMs(loc) <= maxAgeMs
        val accOk = loc.hasAccuracy() && loc.accuracy <= maxAccuracyM
        return ageOk && accOk
    }

    /** Compute fix age in ms using elapsedRealtimeNanos when available. */
    private fun locationAgeMs(loc: Location): Long {
        val nowNs = android.os.SystemClock.elapsedRealtimeNanos()
        return if (loc.elapsedRealtimeNanos != 0L && nowNs != 0L) {
            (nowNs - loc.elapsedRealtimeNanos) / 1_000_000L
        } else {
            System.currentTimeMillis() - loc.time
        }
    }

    /** Convert `Location` to `LatLng`. */
    private fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)
}

/* ──────────────────────────────────────────────────────────────────────────────
 * LOCATION FORMATTING
 * ────────────────────────────────────────────────────────────────────────────── */
object LocationFormattingUtil {

    /** Unit system for display. */
    enum class UnitSystem { METRIC, IMPERIAL }

    /** Travel mode with nominal speeds for rough ETAs. */
    enum class TravelMode(val metersPerSecond: Float) {
        WALK(1.4f),   // ≈ 5 km/h
        DRIVE(13.9f), // ≈ 50 km/h
        BIKE(4.1f),   // ≈ 15 km/h
        RUN(3.0f)     // ≈ 10.8 km/h
    }

    /** Choose unit system from locale (`US`, `LR`, `MM` → imperial; else metric). */
    fun preferredUnitSystem(locale: Locale = Locale.getDefault()): UnitSystem =
        if (locale.country in setOf("US", "LR", "MM")) UnitSystem.IMPERIAL else UnitSystem.METRIC

    /**
     * Format a human-friendly distance.
     * Metric: "850 m" / "1.2 km"; Imperial: "900 ft" / "0.6 mi".
     */
    fun formatDistance(
        metersInput: Float,
        locale: Locale = Locale.getDefault(),
        unit: UnitSystem = preferredUnitSystem(locale),
        includeSuffix: Boolean = false,
        suffix: String = "away",
        approx: Boolean = false
    ): String {
        val meters = metersInput.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f

        val label = when (unit) {
            UnitSystem.METRIC -> {
                if (meters < 1000f) {
                    val rounded = ((meters / 10f).roundToInt() * 10)
                    String.format(locale, "%d m", rounded)
                } else {
                    val km = meters / 1000f
                    val base = if (km < 10f) String.format(locale, "%.1f km", km)
                    else String.format(locale, "%.0f km", km)
                    if (approx && base.contains('.')) "~ $base" else base
                }
            }
            UnitSystem.IMPERIAL -> {
                // 1 m = 3.28084 ft, 1 mi = 1609.344 m
                val feet = meters * 3.28084f
                if (feet < 1000f) {
                    val rounded = ((feet / 10f).roundToInt() * 10)
                    String.format(locale, "%d ft", rounded)
                } else {
                    val miles = meters / 1609.344f
                    val base = if (miles < 10f) String.format(locale, "%.1f mi", miles)
                    else String.format(locale, "%.0f mi", miles)
                    if (approx && base.contains('.')) "~ $base" else base
                }
            }
        }

        return if (includeSuffix) "$label $suffix" else label
    }

    /** Compute distance in meters between two `LatLng` points. */
    fun distanceMeters(a: LatLng?, b: LatLng?): Float {
        if (a == null || b == null) return Float.NaN
        val out = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
        return out[0]
    }

    /**
     * Quick ETA label (straight-line + nominal speed): "<1 min", "12 min", "1 hr 5 min".
     */
    fun etaLabel(
        distanceMeters: Float,
        mode: TravelMode = TravelMode.DRIVE,
        locale: Locale = Locale.getDefault()
    ): String {
        val meters = distanceMeters.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val seconds = (meters / mode.metersPerSecond).toLong()
        val minutes = (seconds / 60).toInt().coerceAtLeast(0)
        return when {
            minutes < 1 -> String.format(locale, "<1 min")
            minutes < 60 -> String.format(locale, "%d min", minutes)
            else -> {
                val hrs = minutes / 60
                val rem = minutes % 60
                if (rem == 0) String.format(locale, "%d hr", hrs)
                else String.format(locale, "%d hr %d min", hrs, rem)
            }
        }
    }

    /** Convenience combo for list rows, e.g., "1.2 km • 15 min". */
    fun distanceAndEtaLabel(
        meters: Float,
        locale: Locale = Locale.getDefault(),
        unit: UnitSystem = preferredUnitSystem(locale),
        mode: TravelMode = TravelMode.DRIVE
    ): String = "${formatDistance(meters, locale, unit)} • ${etaLabel(meters, mode, locale)}"
}