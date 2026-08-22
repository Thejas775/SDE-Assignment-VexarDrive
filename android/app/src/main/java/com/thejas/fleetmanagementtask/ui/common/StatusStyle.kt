package com.thejas.fleetmanagementtask.ui.common

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.FleetEnums

/**
 * One colour vocabulary for every status and severity chip in the app, so the
 * same state reads the same way on the dashboard, a list row and a detail
 * sheet.
 */
enum class StatusTone(
    @ColorRes val text: Int,
    @ColorRes val background: Int,
    @ColorRes val textDark: Int,
    @ColorRes val backgroundDark: Int,
) {
    POSITIVE(
        R.color.status_available, R.color.status_available_bg,
        R.color.status_available_dark, R.color.status_available_bg_dark,
    ),
    ACTIVE(
        R.color.status_on_trip, R.color.status_on_trip_bg,
        R.color.status_on_trip_dark, R.color.status_on_trip_bg_dark,
    ),
    ATTENTION(
        R.color.status_maintenance, R.color.status_maintenance_bg,
        R.color.status_maintenance_dark, R.color.status_maintenance_bg_dark,
    ),
    NEUTRAL(
        R.color.status_inactive, R.color.status_inactive_bg,
        R.color.status_inactive_dark, R.color.status_inactive_bg_dark,
    ),
    CRITICAL(
        R.color.status_critical, R.color.status_critical_bg,
        R.color.status_critical_dark, R.color.status_critical_bg_dark,
    ),
}

fun toneFor(value: String): StatusTone = when (value) {
    "AVAILABLE", "ACTIVE", "COMPLETED", "RESOLVED", "LOW" -> StatusTone.POSITIVE
    "ON_TRIP", "STARTED", "IN_PROGRESS", "SCHEDULED", "MEDIUM" -> StatusTone.ACTIVE
    "IN_MAINTENANCE", "SUSPENDED", "OPEN", "HIGH" -> StatusTone.ATTENTION
    "CRITICAL" -> StatusTone.CRITICAL
    else -> StatusTone.NEUTRAL
}

/** Sets the label and tints the chip from the shared vocabulary. */
fun Chip.applyStatus(value: String) {
    val tone = toneFor(value)
    val night = (resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    text = FleetEnums.label(value)
    setTextColor(color(context, if (night) tone.textDark else tone.text))
    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
        color(context, if (night) tone.backgroundDark else tone.background)
    )
    chipStrokeWidth = 0f
}

private fun color(context: Context, @ColorRes res: Int) = ContextCompat.getColor(context, res)
