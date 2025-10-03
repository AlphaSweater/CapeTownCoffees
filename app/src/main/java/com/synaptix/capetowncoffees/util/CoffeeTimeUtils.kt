package com.synaptix.capetowncoffees.util

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.CheckResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * CoffeeTimeUtils
 *
 * Goals:
 * 1) STORAGE STANDARDIZATION: Persist timestamps as UTC epoch seconds (Long) or ISO-8601 UTC strings.
 * 2) DEVICE-AWARE DISPLAY by DEFAULT: Respect user locale + 12/24h when a Context is provided.
 * 3) EASY OVERRIDES: Per-call DisplayPrefs (optional) and a global default provider.
 */
object CoffeeTimeUtils {

    // ---------------------------------------------------------------------
    // Defaults — can be overridden at app startup (e.g., in Application.onCreate)
    // ---------------------------------------------------------------------

    /** Default provider for DisplayPrefs. Override this once to steer the whole app. */
    @Volatile
    var defaultPrefsProvider: () -> DisplayPrefs = { DisplayPrefs.systemDefaults() }

    // ---------------------------------------------------------------------
    // Display preferences (override-friendly)
    // ---------------------------------------------------------------------

    data class DisplayPrefs(
        val locale: Locale = Locale.getDefault(),
        val zone: ZoneId = ZoneId.systemDefault(),
        val dateStyle: FormatStyle = FormatStyle.MEDIUM,
        val timeStyle: FormatStyle = FormatStyle.SHORT,
        val useDeviceTimeFormat: Boolean = true, // mirror 12/24h if context provided
        val overridePatternDate: String? = null, // e.g. "dd MMM yyyy"
        val overridePatternTime: String? = null, // e.g. "HH:mm" or "h:mm a"
        val overridePatternDateTime: String? = null // e.g. "dd MMM yyyy HH:mm"
    ) {
        companion object {
            /** Re-evaluates Locale/ZoneId at call-time (not frozen at class-load). */
            fun systemDefaults(): DisplayPrefs = DisplayPrefs()

            /** Build prefs directly from an Android Context (locale + device 12/24 intent). */
            fun from(context: Context): DisplayPrefs = DisplayPrefs(
                locale = context.resources.configuration.locales.let { if (it.isEmpty) Locale.getDefault() else it[0] },
                zone = ZoneId.systemDefault(),
                useDeviceTimeFormat = true
            )

            /** Quick helpers to force patterns without losing current locale/zone. */
            fun forcePatterns(
                date: String? = null,
                time: String? = null,
                dateTime: String? = null,
                locale: Locale = Locale.getDefault(),
                zone: ZoneId = ZoneId.systemDefault()
            ): DisplayPrefs = DisplayPrefs(
                locale = locale,
                zone = zone,
                useDeviceTimeFormat = false,
                overridePatternDate = date,
                overridePatternTime = time,
                overridePatternDateTime = dateTime
            )
        }
    }

    // ---------------------------------------------------------------------
    // STORAGE HELPERS — standardize on UTC seconds and ISO-8601 UTC strings
    // ---------------------------------------------------------------------

    /** Current timestamp in UTC seconds */
    fun nowSeconds(): Long = Instant.now().epochSecond

    /** Convert milliseconds since epoch to UTC seconds */
    fun millisToSeconds(millis: Long): Long = millis / 1000

    /** Convert UTC seconds to milliseconds */
    fun secondsToMillis(seconds: Long): Long = seconds * 1000

    /** Parse ISO-8601 to UTC seconds (expects UTC or offset-bearing string) */
    fun parseIsoToSeconds(isoString: String?): Long? = isoToSeconds(isoString)

    /** Preferred name */
    fun isoToSeconds(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try { Instant.parse(isoString.trim()).epochSecond } catch (_: Exception) { null }
    }

    /** UTC seconds -> ISO-8601 UTC (e.g., "2025-10-03T16:45:00Z") */
    fun toIsoInstantString(utcSeconds: Long?): String? = utcSeconds?.let { Instant.ofEpochSecond(it).toString() }

    // ---------------------------------------------------------------------
    // PARSING / CONVERSION
    // ---------------------------------------------------------------------

    private val FORMATTER_12H = DateTimeFormatter.ofPattern("h:mm a")   // e.g. "7:00 AM"
    private val FORMATTER_24H = DateTimeFormatter.ofPattern("HH:mm")    // e.g. "07:00"

    /**
     * Parses a raw time string into a LocalTime.
     * Supports both 12-hour ("7:00 am") and 24-hour ("07:00", "19:00") formats.
     */
    fun parseTimeToLocalTime(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        val input = raw.trim().lowercase()
        return try {
            LocalTime.parse(input.replace(".", ""), FORMATTER_12H)
        } catch (_: Exception) {
            try { LocalTime.parse(input, FORMATTER_24H) } catch (_: Exception) { null }
        }
    }

    /** Convert a UTC seconds timestamp to device LocalDateTime */
    fun toLocalDateTime(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        Instant.ofEpochSecond(utcSeconds).atZone(zone).toLocalDateTime()

    /** Convert a UTC seconds timestamp to device LocalDate */
    fun toLocalDate(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochSecond(utcSeconds).atZone(zone).toLocalDate()

    /** Convert a UTC seconds timestamp to device LocalTime */
    fun toLocalTime(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochSecond(utcSeconds).atZone(zone).toLocalTime()

    // ---------------------------------------------------------------------
    // FORMATTER CACHE (for perf) — keyed by Locale, is24, style, and overrides
    // ---------------------------------------------------------------------

    private enum class FmtKind { DATE, TIME, DATETIME }

    private data class FmtKey(
        val kind: FmtKind,
        val localeTag: String,
        val is24: Boolean?,
        val dateStyle: FormatStyle?,
        val timeStyle: FormatStyle?,
        val overridePattern: String?
    )

    private val formatterCache = ConcurrentHashMap<FmtKey, DateTimeFormatter>()

    private fun cachedFormatter(key: FmtKey, build: () -> DateTimeFormatter): DateTimeFormatter =
        formatterCache.getOrPut(key) { build() }

    // ---------------------------------------------------------------------
    // INTERNAL — choose the best formatters (device-aware if context provided)
    // ---------------------------------------------------------------------

    private fun timeFormatter(prefs: DisplayPrefs, context: Context? = null): DateTimeFormatter {
        prefs.overridePatternTime?.let {
            val key = FmtKey(FmtKind.TIME, prefs.locale.toLanguageTag(), null, null, null, it)
            return cachedFormatter(key) { DateTimeFormatter.ofPattern(it, prefs.locale) }
        }

        if (prefs.useDeviceTimeFormat && context != null) {
            val is24 = DateFormat.is24HourFormat(context)
            val skeleton = if (is24) "Hm" else "hm"
            val pattern = DateFormat.getBestDateTimePattern(prefs.locale, skeleton)
            val key = FmtKey(FmtKind.TIME, prefs.locale.toLanguageTag(), is24, null, null, pattern)
            return cachedFormatter(key) { DateTimeFormatter.ofPattern(pattern, prefs.locale) }
        }

        val key = FmtKey(FmtKind.TIME, prefs.locale.toLanguageTag(), null, null, prefs.timeStyle, null)
        return cachedFormatter(key) { DateTimeFormatter.ofLocalizedTime(prefs.timeStyle).withLocale(prefs.locale) }
    }

    private fun dateFormatter(prefs: DisplayPrefs, context: Context? = null): DateTimeFormatter {
        prefs.overridePatternDate?.let {
            val key = FmtKey(FmtKind.DATE, prefs.locale.toLanguageTag(), null, null, null, it)
            return cachedFormatter(key) { DateTimeFormatter.ofPattern(it, prefs.locale) }
        }

        if (prefs.useDeviceTimeFormat && context != null) {
            val pattern = DateFormat.getBestDateTimePattern(prefs.locale, "yMMMd")
            val key = FmtKey(FmtKind.DATE, prefs.locale.toLanguageTag(), null, prefs.dateStyle, null, pattern)
            return cachedFormatter(key) { DateTimeFormatter.ofPattern(pattern, prefs.locale) }
        }

        val key = FmtKey(FmtKind.DATE, prefs.locale.toLanguageTag(), null, prefs.dateStyle, null, null)
        return cachedFormatter(key) { DateTimeFormatter.ofLocalizedDate(prefs.dateStyle).withLocale(prefs.locale) }
    }

    private fun dateTimeFormatter(prefs: DisplayPrefs, context: Context? = null): DateTimeFormatter {
        prefs.overridePatternDateTime?.let {
            val key = FmtKey(FmtKind.DATETIME, prefs.locale.toLanguageTag(), null, null, null, it)
            return cachedFormatter(key) { DateTimeFormatter.ofPattern(it, prefs.locale) }
        }

        if (prefs.useDeviceTimeFormat && context != null) {
            val is24 = DateFormat.is24HourFormat(context)
            val timeSkell = if (is24) "Hm" else "hm"
            val pattern = DateFormat.getBestDateTimePattern(prefs.locale, "yMMMd $timeSkell")
            val key = FmtKey(FmtKind.DATETIME, prefs.locale.toLanguageTag(), is24, prefs.dateStyle, prefs.timeStyle, pattern)
            return cachedFormatter(key) { DateTimeFormatter.ofPattern(pattern, prefs.locale) }
        }

        val key = FmtKey(FmtKind.DATETIME, prefs.locale.toLanguageTag(), null, prefs.dateStyle, prefs.timeStyle, null)
        return cachedFormatter(key) {
            DateTimeFormatter.ofLocalizedDateTime(prefs.dateStyle, prefs.timeStyle).withLocale(prefs.locale)
        }
    }

    // ---------------------------------------------------------------------
    // PUBLIC — Formatting (prefs/context are OPTIONAL)
    // ---------------------------------------------------------------------

    @CheckResult
    fun formatShortDate(
        utcSeconds: Long?,
        prefs: DisplayPrefs = defaultPrefsProvider(),
        context: Context? = null
    ): String {
        if (utcSeconds == null) return ""
        val dt = toLocalDate(utcSeconds, prefs.zone)
        return dt.format(dateFormatter(prefs, context))
    }

    @CheckResult
    fun formatShortTime(
        utcSeconds: Long?,
        prefs: DisplayPrefs = defaultPrefsProvider(),
        context: Context? = null
    ): String {
        if (utcSeconds == null) return ""
        val t = toLocalTime(utcSeconds, prefs.zone)
        return t.format(timeFormatter(prefs, context))
    }

    @CheckResult
    fun formatFullDateTime(
        utcSeconds: Long?,
        prefs: DisplayPrefs = defaultPrefsProvider(),
        context: Context? = null
    ): String {
        if (utcSeconds == null) return ""
        val dt = toLocalDateTime(utcSeconds, prefs.zone)
        return dt.format(dateTimeFormatter(prefs, context))
    }

    /** Smart display:
     *  - Today → time
     *  - Last 7 days → relative ("2 days ago")
     *  - Older → short date
     */
    @CheckResult
    fun formatSmart(
        utcSeconds: Long?,
        prefs: DisplayPrefs = defaultPrefsProvider(),
        context: Context? = null
    ): String {
        if (utcSeconds == null) return ""
        val now = LocalDate.now(prefs.zone)
        val date = toLocalDate(utcSeconds, prefs.zone)
        val daysDiff = ChronoUnit.DAYS.between(date, now)
        return when (daysDiff) {
            0L -> formatShortTime(utcSeconds, prefs, context)
            in 1..7 -> formatRelativeTime(utcSeconds, prefs)
            else -> formatShortDate(utcSeconds, prefs, context)
        }
    }

    @CheckResult
    fun formatRelativeTime(
        utcSeconds: Long?,
        prefs: DisplayPrefs = defaultPrefsProvider()
    ): String {
        if (utcSeconds == null) return ""
        val now = Instant.now()
        val time = Instant.ofEpochSecond(utcSeconds)
        val diffSeconds = ChronoUnit.SECONDS.between(time, now)
        val absSeconds = kotlin.math.abs(diffSeconds)
        val isPast = diffSeconds >= 0

        val label = when {
            absSeconds < 10 -> "just now"
            absSeconds < 60 -> "$absSeconds second${"s"}"
            absSeconds < 3600 -> {
                val m = absSeconds / 60
                "$m minute${if (m == 1L) "" else "s"}"
            }
            absSeconds < 86400 -> {
                val h = absSeconds / 3600
                "$h hour${if (h == 1L) "" else "s"}"
            }
            absSeconds < 2592000 -> {
                val d = absSeconds / 86400
                "$d day${if (d == 1L) "" else "s"}"
            }
            else -> {
                val nowDate = LocalDateTime.ofInstant(now, prefs.zone).toLocalDate()
                val timeDate = LocalDateTime.ofInstant(time, prefs.zone).toLocalDate()
                val period = Period.between(timeDate, nowDate)
                when {
                    kotlin.math.abs(period.years) >= 1 -> "${kotlin.math.abs(period.years)} year${if (kotlin.math.abs(period.years) == 1) "" else "s"}"
                    kotlin.math.abs(period.months) >= 1 -> "${kotlin.math.abs(period.months)} month${if (kotlin.math.abs(period.months) == 1) "" else "s"}"
                    else -> "${kotlin.math.abs(period.days)} day${if (kotlin.math.abs(period.days) == 1) "" else "s"}"
                }
            }
        }
        return when (label) {
            "just now" -> label
            else -> if (isPast) "$label ago" else "in $label"
        }
    }

    // ---------------------------------------------------------------------
    // COMPARISONS
    // ---------------------------------------------------------------------

    fun isPast(utcSeconds: Long): Boolean = Instant.ofEpochSecond(utcSeconds).isBefore(Instant.now())

    fun isToday(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(utcSeconds, zone) == LocalDate.now(zone)

    fun isSameDay(utcSeconds1: Long, utcSeconds2: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(utcSeconds1, zone) == toLocalDate(utcSeconds2, zone)
}
