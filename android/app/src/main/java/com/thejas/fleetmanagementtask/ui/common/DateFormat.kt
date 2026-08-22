package com.thejas.fleetmanagementtask.ui.common

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * MaterialDatePicker hands back UTC midnight. Formatting in the device's zone
 * shifts the date by a day for anyone west of Greenwich.
 */
fun formatUtcDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(epochMillis)

/**
 * Combines a date from MaterialDatePicker (UTC midnight) with a wall-clock time
 * the user picked locally, and renders the resulting instant as UTC ISO-8601.
 */
fun toUtcIso(datePickerMillis: Long, hour: Int, minute: Int): String {
    val picked = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = datePickerMillis
    }
    val local = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, picked.get(java.util.Calendar.YEAR))
        set(java.util.Calendar.MONTH, picked.get(java.util.Calendar.MONTH))
        set(java.util.Calendar.DAY_OF_MONTH, picked.get(java.util.Calendar.DAY_OF_MONTH))
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(local.time)
}

/** "2026-08-23T08:00:00Z" -> "23 Aug 2026, 13:30" in the device's zone. */
fun formatInstant(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (pattern in patterns) {
        runCatching {
            val parsed = SimpleDateFormat(pattern, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(iso) ?: return@runCatching
            return SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(parsed)
        }
    }
    return iso
}
