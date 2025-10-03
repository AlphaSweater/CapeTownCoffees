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

/* ──────────────────────────────────────────────────────────────────────────────
 * COFFEE TIME UTILS — PUBLIC SURFACE
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Time and date helpers used across the app.
 * Centralizes epoch conversions, parsing, and locale/format-aware rendering.
 *
 * Details:
 * - Uses `DisplayPrefs` to control locale, zone, and patterns (or system defaults).
 * - Formatter instances are cached per-locale/style for performance.
 * - Rendering can mirror device 12/24-hour preference when a `Context` is provided.
 *
 * Gotchas:
 * - Formatting is English-only **only** for `formatRelativeTime` labels; others use locale-aware
 *   `DateTimeFormatter`. Consider i18n if you surface relative strings to users.
 * - All `utcSeconds` refer to **UTC epoch seconds**. Convert first if your input is local.
 */
object CoffeeTimeUtils {

    /**
     * Supplier for default `DisplayPrefs` used by formatting functions when `prefs` is omitted.
     * Set this once in `Application` (or tests) to unify formatting across the app.
     *
     * Details:
     * - Evaluated at **call time** (not captured), so updates take effect immediately.
     * - If you want to mirror device 12/24h, pass an Android `Context` to the formatters.
     *
     * Gotchas:
     * - Don’t capture an `Activity` in the provider. Keep it static or use application context.
     */
    var defaultPrefsProvider: () -> DisplayPrefs = { DisplayPrefs.systemDefaults() }

    /* ──────────────────────────────────────────────────────────────────────────
     * DISPLAY PREFS
     * ────────────────────────────────────────────────────────────────────────── */

    /**
     * User/display-facing preferences controlling locale, zone, and formatting style/patterns.
     * Prefer system defaults unless you have a strong reason to pin patterns.
     *
     * Details:
     * - `useDeviceTimeFormat=true` lets formatters mirror device 12/24-hour when a `Context`
     *   is provided. Otherwise falls back to `timeStyle`.
     * - `overridePattern*` takes precedence over style and device settings.
     *
     * Gotchas:
     * - Overriding patterns bypasses locale-specific conventions. Keep patterns minimal.
     *
     * @property locale BCP-47 locale used by `DateTimeFormatter`.
     * @property zone Zone for converting UTC epoch seconds into local date/time.
     * @property dateStyle Fallback date style when no override pattern is provided.
     * @property timeStyle Fallback time style when no override pattern is provided.
     * @property useDeviceTimeFormat If `true`, tries to mirror device 12/24h when `Context` supplied.
     * @property overridePatternDate Optional explicit date pattern.
     * @property overridePatternTime Optional explicit time pattern.
     * @property overridePatternDateTime Optional explicit date-time pattern.
     */
    data class DisplayPrefs(
        val locale: Locale = Locale.getDefault(),
        val zone: ZoneId = ZoneId.systemDefault(),
        val dateStyle: FormatStyle = FormatStyle.MEDIUM,
        val timeStyle: FormatStyle = FormatStyle.SHORT,
        val useDeviceTimeFormat: Boolean = true,
        val overridePatternDate: String? = null,
        val overridePatternTime: String? = null,
        val overridePatternDateTime: String? = null
    ) {
        companion object {
            /** System locale/zone with style defaults. */
            fun systemDefaults(): DisplayPrefs = DisplayPrefs()

            /**
             * Builds prefs using the app/device configuration.
             *
             * Details:
             * - Mirrors device 12/24h when formatters receive the same `context`.
             * - Uses the first configured locale from `Configuration.locales`.
             */
            fun from(context: Context): DisplayPrefs = DisplayPrefs(
                locale = context.resources.configuration.locales.let {
                    if (it.isEmpty) Locale.getDefault() else it[0]
                },
                zone = ZoneId.systemDefault(),
                useDeviceTimeFormat = true
            )

            /**
             * Builds prefs with explicit patterns. Disables device 12/24h mirroring.
             *
             * Gotchas:
             * - Patterns must match the chosen `locale`. Avoid mixing symbols across locales.
             */
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

    /* ──────────────────────────────────────────────────────────────────────────
     * BASICS & EPOCH CONVERSIONS
     * ────────────────────────────────────────────────────────────────────────── */

    /** Current UTC epoch seconds. */
    fun nowSeconds(): Long = Instant.now().epochSecond

    /** Convert milliseconds to seconds (floor). */
    fun millisToSeconds(millis: Long): Long = millis / 1000

    /** Convert seconds to milliseconds. */
    fun secondsToMillis(seconds: Long): Long = seconds * 1000

    /**
     * Parses an ISO-8601 instant into epoch seconds or `null` if invalid/blank.
     * @see isoToSeconds
     */
    fun parseIsoToSeconds(isoString: String?): Long? = isoToSeconds(isoString)

    /**
     * Parses an ISO-8601 instant into epoch seconds or `null` if invalid/blank.
     *
     * Edge cases:
     * - Returns `null` for empty/whitespace strings or parse failures.
     */
    fun isoToSeconds(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try { Instant.parse(isoString.trim()).epochSecond } catch (_: Exception) { null }
    }

    /**
     * Formats UTC epoch seconds into an ISO-8601 `Instant` string or returns `null` if input is `null`.
     */
    fun toIsoInstantString(utcSeconds: Long?): String? =
        utcSeconds?.let { Instant.ofEpochSecond(it).toString() }

    /* ──────────────────────────────────────────────────────────────────────────
     * LOCAL DATE/TIME CONVERSIONS
     * ────────────────────────────────────────────────────────────────────────── */

    /**
     * Parses a clock time into `LocalTime`.
     *
     * Details:
     * - Accepts `h:mm a` (e.g., `"7:00 am"`, case-insensitive, dots stripped) and `HH:mm` (e.g., `"07:00"`).
     * - Returns `null` if the time cannot be parsed.
     *
     * Edge cases:
     * - `"7.00 am"` works (dots removed). `"07:00:30"` is not supported.
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

    /** Converts UTC epoch seconds to `LocalDateTime` in `zone`. */
    fun toLocalDateTime(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        Instant.ofEpochSecond(utcSeconds).atZone(zone).toLocalDateTime()

    /** Converts UTC epoch seconds to `LocalDate` in `zone`. */
    fun toLocalDate(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochSecond(utcSeconds).atZone(zone).toLocalDate()

    /** Converts UTC epoch seconds to `LocalTime` in `zone`. */
    fun toLocalTime(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochSecond(utcSeconds).atZone(zone).toLocalTime()

    /* ──────────────────────────────────────────────────────────────────────────
     * FORMATTERS (PUBLIC)
     * ────────────────────────────────────────────────────────────────────────── */

    /**
     * Renders a short **date** string (no time) using `prefs` and optional device patterns.
     *
     * Details:
     * - If `prefs.overridePatternDate` is set, it is used directly.
     * - If `prefs.useDeviceTimeFormat` and `context != null`, uses Android’s best date pattern.
     * - Otherwise uses `DateTimeFormatter.ofLocalizedDate(prefs.dateStyle)`.
     *
     * Edge cases:
     * - Returns `""` if `utcSeconds == null`.
     *
     * ### Examples
     * ```kotlin
     * val s1 = CoffeeTimeUtils.formatShortDate(orderUtc, CoffeeTimeUtils.DisplayPrefs.systemDefaults())
     * val s2 = CoffeeTimeUtils.formatShortDate(orderUtc, CoffeeTimeUtils.defaultPrefsProvider(), appContext)
     * ```
     *
     * @see formatShortTime
     * @see formatFullDateTime
     */
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

    /**
     * Renders a short **time** string (no date) using `prefs` and optional device 12/24h.
     *
     * Details:
     * - If `overridePatternTime` present, uses it.
     * - If `useDeviceTimeFormat` and `context != null`, mirrors device 12/24h.
     * - Otherwise uses `DateTimeFormatter.ofLocalizedTime(prefs.timeStyle)`.
     *
     * Edge cases:
     * - Returns `""` if `utcSeconds == null`.
     *
     * ### Examples
     * ```kotlin
     * val t = CoffeeTimeUtils.formatShortTime(messageUtc, prefs = CoffeeTimeUtils.defaultPrefsProvider(), context)
     * ```
     *
     * @see formatShortDate
     * @see formatFullDateTime
     */
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

    /**
     * Renders a localized **date + time** string using `prefs` and optional device patterns.
     *
     * Details:
     * - Prefers `overridePatternDateTime` if provided.
     * - Otherwise applies Android best pattern when `useDeviceTimeFormat && context != null`.
     * - Falls back to `ofLocalizedDateTime(dateStyle, timeStyle)`.
     *
     * Edge cases:
     * - Returns `""` if `utcSeconds == null`.
     *
     * ### Examples
     * ```kotlin
     * val full = CoffeeTimeUtils.formatFullDateTime(updatedUtc, prefs, context)
     * ```
     */
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

    /**
     * Renders a smart string:
     * - **Today** → time only (e.g., `"14:05"` or `"2:05 PM"`).
     * - **Within 7 days** → relative (e.g., `"3 days ago"`).
     * - **Older** → date only.
     *
     * Details:
     * - Relies on `prefs.zone` to determine “today”.
     * - Uses `formatRelativeTime` for the middle bucket.
     *
     * Edge cases:
     * - Returns `""` if `utcSeconds == null`.
     *
     * Gotchas:
     * - Relative labels are English-only. Consider a localized alternative if needed.
     *
     * ### Examples
     * ```kotlin
     * val label = CoffeeTimeUtils.formatSmart(lastSeenUtc, prefs, context)
     * ```
     *
     * @see formatShortDate
     * @see formatShortTime
     * @see formatRelativeTime
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

    /**
     * Produces human-readable relative time like `"just now"`, `"5 minutes ago"`, `"in 2 hours"`.
     *
     * Details:
     * - Chooses units in seconds/minutes/hours/days; for ≥ ~30 days, uses months/years via `Period`.
     * - Always compares to `Instant.now()`; zone affects month/year via `prefs.zone`.
     *
     * Edge cases:
     * - Returns `""` if `utcSeconds == null`.
     * - `"just now"` for < 10s difference.
     *
     * Gotchas:
     * - English-only strings and simple pluralization. If you need proper i18n, wrap this function.
     *
     * ### Examples
     * ```kotlin
     * val rel = CoffeeTimeUtils.formatRelativeTime(messageUtc, prefs)
     * ```
     */
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
                    kotlin.math.abs(period.years) >= 1 ->
                        "${kotlin.math.abs(period.years)} year${if (kotlin.math.abs(period.years) == 1) "" else "s"}"
                    kotlin.math.abs(period.months) >= 1 ->
                        "${kotlin.math.abs(period.months)} month${if (kotlin.math.abs(period.months) == 1) "" else "s"}"
                    else ->
                        "${kotlin.math.abs(period.days)} day${if (kotlin.math.abs(period.days) == 1) "" else "s"}"
                }
            }
        }
        return when (label) {
            "just now" -> label
            else -> if (isPast) "$label ago" else "in $label"
        }
    }

    /** True if the given UTC epoch seconds are strictly before `Instant.now()`. */
    fun isPast(utcSeconds: Long): Boolean =
        Instant.ofEpochSecond(utcSeconds).isBefore(Instant.now())

    /** True if `utcSeconds` occurs on the same calendar day as *today* in `zone`. */
    fun isToday(utcSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(utcSeconds, zone) == LocalDate.now(zone)

    /** True if both instants fall on the same calendar day in `zone`. */
    fun isSameDay(utcSeconds1: Long, utcSeconds2: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(utcSeconds1, zone) == toLocalDate(utcSeconds2, zone)

    /* ──────────────────────────────────────────────────────────────────────────
     * INTERNALS (PRIVATE HELPERS)
     * ────────────────────────────────────────────────────────────────────────── */

    /** 12/24h parsing patterns (private). */
    private val FORMATTER_12H = DateTimeFormatter.ofPattern("h:mm a")
    /** 12/24h parsing patterns (private). */
    private val FORMATTER_24H = DateTimeFormatter.ofPattern("HH:mm")

    /** Internal formatter kind key (private). */
    private enum class FmtKind { DATE, TIME, DATETIME }

    /** Cache key for formatters (private). */
    private data class FmtKey(
        val kind: FmtKind,
        val localeTag: String,
        val is24: Boolean?,
        val dateStyle: FormatStyle?,
        val timeStyle: FormatStyle?,
        val overridePattern: String?
    )

    /** Formatter cache keyed by locale/style/device 12/24h (private). */
    private val formatterCache = ConcurrentHashMap<FmtKey, DateTimeFormatter>()

    /** Returns cached formatter or creates/stores a new one (private). */
    private fun cachedFormatter(key: FmtKey, build: () -> DateTimeFormatter): DateTimeFormatter =
        formatterCache.getOrPut(key) { build() }

    /** Builds a time formatter honoring device 12/24h when possible (private). */
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

    /** Builds a date formatter using override/device/best-fit fallbacks (private). */
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

    /** Builds a date-time formatter using override/device/best-fit fallbacks (private). */
    private fun dateTimeFormatter(prefs: DisplayPrefs, context: Context? = null): DateTimeFormatter {
        prefs.overridePatternDateTime?.let {
            val key = FmtKey(FmtKind.DATETIME, prefs.locale.toLanguageTag(), null, null, null, it)
            return cachedFormatter(key) { DateTimeFormatter.ofPattern(it, prefs.locale) }
        }
        if (prefs.useDeviceTimeFormat && context != null) {
            val is24 = DateFormat.is24HourFormat(context)
            val timeSkell = if (is24) "Hm" else "hm"
            val pattern = DateFormat.getBestDateTimePattern(prefs.locale, "yMMMd $timeSkell")
            val key = FmtKey(
                FmtKind.DATETIME, prefs.locale.toLanguageTag(), is24, prefs.dateStyle, prefs.timeStyle, pattern
            )
            return cachedFormatter(key) { DateTimeFormatter.ofPattern(pattern, prefs.locale) }
        }
        val key = FmtKey(
            FmtKind.DATETIME, prefs.locale.toLanguageTag(), null, prefs.dateStyle, prefs.timeStyle, null
        )
        return cachedFormatter(key) {
            DateTimeFormatter.ofLocalizedDateTime(prefs.dateStyle, prefs.timeStyle).withLocale(prefs.locale)
        }
    }
}
