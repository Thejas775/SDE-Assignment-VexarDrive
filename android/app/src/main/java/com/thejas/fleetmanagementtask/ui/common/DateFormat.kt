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
