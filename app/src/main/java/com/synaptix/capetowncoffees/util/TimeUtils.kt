package com.synaptix.capetowncoffees.util

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Utility functions for time conversion, formatting, and comparison.
 * All timestamps are in UTC seconds unless otherwise specified.
 */
object TimeUtils {
    // -----------------------------
    // Formatters (constants)
    // -----------------------------
    private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy")
    private val SHORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    private val FULL_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")

    // -----------------------------
    // Conversions
    // -----------------------------

    /** Convert milliseconds since epoch to UTC seconds */
    fun millisToSeconds(millis: Long): Long = millis / 1000

    /** Convert UTC seconds to milliseconds */
    fun secondsToMillis(seconds: Long): Long = seconds * 1000

    /** Get current timestamp in UTC seconds */
    fun nowSeconds(): Long = Instant.now().epochSecond

    /** Convert a UTC seconds timestamp to device LocalDateTime */
    fun toLocalDateTime(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        Instant.ofEpochSecond(utcSeconds).atZone(zone).toLocalDateTime()

    /** Convert a UTC seconds timestamp to device LocalDate */
    fun toLocalDate(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochSecond(utcSeconds).atZone(zone).toLocalDate()

    /** Convert a UTC seconds timestamp to device LocalTime */
    fun toLocalTime(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochSecond(utcSeconds).atZone(zone).toLocalTime()

    // -----------------------------
    // Formatting
    // -----------------------------

    /** Format timestamp as short date "01 Oct 2025" */
    fun formatShortDate(utcSeconds: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (utcSeconds == null) return ""
        val date = toLocalDate(utcSeconds, zone)
        return date.format(SHORT_DATE_FORMATTER)
    }

    /** Format timestamp as short time "14:30" */
    fun formatShortTime(utcSeconds: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (utcSeconds == null) return ""
        val time = toLocalTime(utcSeconds, zone)
        return time.format(SHORT_TIME_FORMATTER)
    }

    /** Format timestamp as full datetime "01 Oct 2025 14:30" */
    fun formatFullDateTime(utcSeconds: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (utcSeconds == null) return ""
        val dateTime = toLocalDateTime(utcSeconds, zone)
        return dateTime.format(FULL_DATETIME_FORMATTER)
    }

    /**
     * Format timestamp as relative time (e.g., "5 minutes ago", "in 2 hours", "just now").
     * Handles future timestamps and uses Period for months/years.
     */
    fun formatRelativeTime(utcSeconds: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (utcSeconds == null) return ""
        val now = Instant.now()
        val time = Instant.ofEpochSecond(utcSeconds)
        val diffSeconds = ChronoUnit.SECONDS.between(time, now)
        val absSeconds = kotlin.math.abs(diffSeconds)
        val isPast = diffSeconds >= 0

        val label = when {
            absSeconds < 10 -> "just now"
            absSeconds < 60 -> "$absSeconds second${if (absSeconds == 1L) "" else "s"}"
            absSeconds < 3600 -> {
                val minutes = absSeconds / 60
                "$minutes minute${if (minutes == 1L) "" else "s"}"
            }
            absSeconds < 86400 -> {
                val hours = absSeconds / 3600
                "$hours hour${if (hours == 1L) "" else "s"}"
            }
            absSeconds < 2592000 -> {
                val days = absSeconds / 86400
                "$days day${if (days == 1L) "" else "s"}"
            }
            else -> {
                val nowDate = LocalDateTime.ofInstant(now, zone).toLocalDate()
                val timeDate = LocalDateTime.ofInstant(time, zone).toLocalDate()
                val period = Period.between(timeDate, nowDate)
                when {
                    kotlin.math.abs(period.years) >= 1 -> "${kotlin.math.abs(period.years)} year${if (kotlin.math.abs(period.years) == 1) "" else "s"}"
                    kotlin.math.abs(period.months) >= 1 -> "${kotlin.math.abs(period.months)} month${if (kotlin.math.abs(period.months) == 1) "" else "s"}"
                    else -> {
                        val days = kotlin.math.abs(period.days)
                        "$days day${if (days == 1) "" else "s"}"
                    }
                }
            }
        }
        return if (label == "just now") label else if (isPast) "$label ago" else "in $label"
    }

    // -----------------------------
    // Comparisons
    // -----------------------------

    /** Check if a UTC seconds timestamp is in the past */
    fun isPast(utcSeconds: Long): Boolean = Instant.ofEpochSecond(utcSeconds).isBefore(Instant.now())

    /** Check if a UTC seconds timestamp is today (device local date) */
    fun isToday(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(utcSeconds, zone) == LocalDate.now(zone)

    /** Check if two timestamps are on the same day (device local date) */
    fun isSameDay(utcSeconds1: Long, utcSeconds2: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(utcSeconds1, zone) == toLocalDate(utcSeconds2, zone)
}
