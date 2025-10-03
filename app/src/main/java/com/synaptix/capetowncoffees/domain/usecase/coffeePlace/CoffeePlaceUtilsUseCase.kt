package com.synaptix.capetowncoffees.domain.usecase.coffeePlace

import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class CoffeePlaceUtilsUseCase @Inject constructor(
    private val coffeePlaceRepository: ICoffeePlaceRepository
) {
    /**
     * Checks if a coffee place exists in the database by its ID.
     */
    suspend fun checkIfPlaceExists(placeId: String): Boolean {
        val result = coffeePlaceRepository.checkCoffeePlaceExists(placeId)
        return result.isSuccess && result.getOrNull() == true
    }

    /**
     * Parses an opening hours string and checks if the place is open right now.
     * Example format: "Mon-Fri 08:00-17:00; Sat-Sun 09:00-15:00"
     * This is a simple implementation, you can extend for more complex cases.
     */
    fun isPlaceOpenNow(openingHours: String): Boolean {
        val now = java.time.LocalTime.now()
        val today = java.time.LocalDate.now()
        val dayOfWeek = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        // Example: "Mon-Fri 08:00-17:00; Sat-Sun 09:00-15:00"
        val entries = openingHours.split(";").map { it.trim() }
        for (entry in entries) {
            val parts = entry.split(" ")
            if (parts.size != 2) continue
            val days = parts[0]
            val times = parts[1]
            // Handle day ranges like "Mon-Fri"
            if (days.contains("-")) {
                val range = days.split("-")
                if (range.size == 2) {
                    val startDay = range[0]
                    val endDay = range[1]
                    val daysList = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    val startIdx = daysList.indexOf(startDay)
                    val endIdx = daysList.indexOf(endDay)
                    val todayIdx = daysList.indexOf(dayOfWeek)
                    if (todayIdx in startIdx..endIdx) {
                        val timeParts = times.split("-")
                        if (timeParts.size == 2) {
                            val openTime = java.time.LocalTime.parse(timeParts[0], java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                            val closeTime = java.time.LocalTime.parse(timeParts[1], java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                            return now.isAfter(openTime) && now.isBefore(closeTime)
                        }
                    }
                }
            } else if (days == dayOfWeek) {
                val timeParts = times.split("-")
                if (timeParts.size == 2) {
                    val openTime = java.time.LocalTime.parse(timeParts[0], java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    val closeTime = java.time.LocalTime.parse(timeParts[1], java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    return now.isAfter(openTime) && now.isBefore(closeTime)
                }
            }
        }
        return false
    }

    // Add more utility methods as needed
}
