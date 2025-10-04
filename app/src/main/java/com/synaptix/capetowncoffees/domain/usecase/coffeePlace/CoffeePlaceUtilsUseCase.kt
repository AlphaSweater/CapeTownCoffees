package com.synaptix.capetowncoffees.domain.usecase.coffeePlace

import android.content.Context
import android.net.Uri
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.net.FetchResolvedPhotoUriRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils.nowSeconds
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils.parseTimeToLocalTime
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils.toLocalDate
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils.toLocalTime
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoffeePlaceUtilsUseCase @Inject constructor(
    private val context: Context,
    private val placesClient: PlacesClient,
    private val coffeePlaceRepository: ICoffeePlaceRepository
) {

    suspend fun checkIfPlaceExists(placeId: String): Boolean {
        val result = coffeePlaceRepository.checkCoffeePlaceExists(placeId)
        return result.isSuccess && result.getOrNull() == true
    }

    /**
     * Minimal: returns true if open "right now" based on Google Places weekday_text.
     * Uses CoffeeTimeUtils' global default prefs (locale/zone), no explicit prefs needed.
     */
    fun isPlaceOpenNow(weekdayText: List<String>): Boolean {
        val nowSec = nowSeconds()
        val todayDate: LocalDate = toLocalDate(nowSec) // uses defaultPrefsProvider()
        val nowTime: LocalTime = toLocalTime(nowSec)   // uses defaultPrefsProvider()
        val today = todayDate.dayOfWeek
        val yesterday = todayDate.minusDays(1).dayOfWeek

        val dayMap = mapOf(
            "monday" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY
        )

        for (line in weekdayText) {
            val clean = normalize(line)
            val parts = clean.split(":", limit = 2)
            if (parts.size != 2) continue

            val dayStr = parts[0].trim().lowercase(Locale.ENGLISH)
            val timesStr = parts[1].trim()

            val lineDay = dayMap[dayStr] ?: continue
            if (lineDay != today && lineDay != yesterday) continue
            if (timesStr.equals("closed", ignoreCase = true)) continue

            val ranges = timesStr.split(",").map { it.trim() }
            for (range in ranges) {
                val bounds = range.split('–', '—', '-', '−').map { it.trim() }
                if (bounds.size != 2) continue

                val open = parseTimeToLocalTime(bounds[0]) ?: continue
                val close = parseTimeToLocalTime(bounds[1]) ?: continue

                if (close == LocalTime.MIDNIGHT) {
                    if (lineDay == today && nowTime >= open) return true
                    continue
                }

                if (close.isAfter(open)) {
                    if (lineDay == today && nowTime >= open && nowTime <= close) return true
                } else {
                    if (lineDay == today && nowTime >= open) return true
                    if (lineDay == yesterday && nowTime <= close) return true
                }
            }
        }

        return false
    }

    private fun normalize(s: String): String =
        s.replace('\u2009', ' ')
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .trim()

    /**
     * Helper: Retrieves the photo URI for a given PhotoMetadata using PlacesClient.
     * Converts dp to pixels for maxHeight/maxWidth.
     * @param photoMetadata The PhotoMetadata object from Google Places API.
     * @param maxWidthDp Optional max width in dp.
     * @param maxHeightDp Optional max height in dp.
     * @return The Uri of the photo, or null if failed.
     */
    suspend fun getPhotoUriFromMetadata(
        photoMetadata: PhotoMetadata,
        maxWidthDp: Int? = null,
        maxHeightDp: Int? = null
    ): Uri? = suspendCancellableCoroutine { cont ->
        val density = context.resources.displayMetrics.density
        val builder = FetchResolvedPhotoUriRequest.builder(photoMetadata)
        maxWidthDp?.let { builder.setMaxWidth((it * density).toInt()) }
        maxHeightDp?.let { builder.setMaxHeight((it * density).toInt()) }
        val request = builder.build()
        placesClient.fetchResolvedPhotoUri(request)
            .addOnSuccessListener { response ->
                cont.resume(response.uri)
            }
            .addOnFailureListener { exception ->
                cont.resume(null)
            }
    }
}
