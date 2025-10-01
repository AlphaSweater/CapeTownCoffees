package com.synaptix.capetowncoffees.util

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Utility functions for time conversion, formatting, and comparison.
 * All timestamps are in UTC seconds internally unless otherwise specified.
 */
object TimeUtils {
    // -----------------------------
    // Formatters (constants)
    // -----------------------------
    private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy")
    private val SHORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    private val FULL_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")

    // -----------------------------
    // Parsing / Conversion
    // -----------------------------

    /** Parse ISO 8601 string to UTC seconds */
    fun parseIsoToSeconds(isoString: String?): Long? {
        if (isoString.isNullOrEmpty()) return null
        return try {
            Instant.parse(isoString).epochSecond
        } catch (e: Exception) {
            null
        }
    }

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

    fun formatShortDate(utcSeconds: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        utcSeconds?.let { toLocalDate(it, zone).format(SHORT_DATE_FORMATTER) } ?: ""

    fun formatShortTime(utcSeconds: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        utcSeconds?.let { toLocalTime(it, zone).format(SHORT_TIME_FORMATTER) } ?: ""

    fun formatFullDateTime(utcSeconds: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        utcSeconds?.let { toLocalDateTime(it, zone).format(FULL_DATETIME_FORMATTER) } ?: ""

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
            absSeconds < 3600 -> "${absSeconds / 60} minute${if (absSeconds / 60 == 1L) "" else "s"}"
            absSeconds < 86400 -> "${absSeconds / 3600} hour${if (absSeconds / 3600 == 1L) "" else "s"}"
            absSeconds < 2592000 -> "${absSeconds / 86400} day${if (absSeconds / 86400 == 1L) "" else "s"}"
            else -> {
                val nowDate = LocalDateTime.ofInstant(now, zone).toLocalDate()
                val timeDate = LocalDateTime.ofInstant(time, zone).toLocalDate()
                val period = Period.between(timeDate, nowDate)
                when {
                    kotlin.math.abs(period.years) >= 1 -> "${kotlin.math.abs(period.years)} year${if (kotlin.math.abs(period.years) == 1) "" else "s"}"
                    kotlin.math.abs(period.months) >= 1 -> "${kotlin.math.abs(period.months)} month${if (kotlin.math.abs(period.months) == 1) "" else "s"}"
                    else -> "${kotlin.math.abs(period.days)} day${if (period.days == 1) "" else "s"}"
                }
            }
        }

        return when (label) {
            "just now" -> label
            else -> if (isPast) "$label ago" else "in $label"
        }
    }

    /**
     * Returns a "smart" display string for a UTC seconds timestamp:
     * - Today → shows time (HH:mm)
     * - Within the last 7 days → shows relative time ("2 days ago")
     * - Older → shows short date ("01 Oct 2025")
     */
    fun formatSmart(utcSeconds: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (utcSeconds == null) return ""

        val now = LocalDate.now(zone)
        val date = toLocalDate(utcSeconds, zone)
        val daysDiff = ChronoUnit.DAYS.between(date, now)

        return when {
            daysDiff == 0L -> formatShortTime(utcSeconds, zone)                     // today → time
            daysDiff in 1..7 -> formatRelativeTime(utcSeconds, zone)          // last 7 days → relative
            else -> formatShortDate(utcSeconds, zone)                               // older → date
        }
    }

    // -----------------------------
    // Comparisons
    // -----------------------------

    fun isPast(utcSeconds: Long): Boolean = Instant.ofEpochSecond(utcSeconds).isBefore(Instant.now())

    fun isToday(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(utcSeconds, zone) == LocalDate.now(zone)

    fun isSameDay(utcSeconds1: Long, utcSeconds2: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(utcSeconds1, zone) == toLocalDate(utcSeconds2, zone)
}
