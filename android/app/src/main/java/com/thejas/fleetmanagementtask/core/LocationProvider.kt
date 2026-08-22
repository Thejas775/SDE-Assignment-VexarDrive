package com.thejas.fleetmanagementtask.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot location fix using the platform LocationManager.
 *
 * Deliberately not Google Play Services: a fleet runs on cheap handsets that
 * may not have Play Services at all, and the framework API is enough for
 * recording where a trip started and ended.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = FIX_TIMEOUT_MS): Location? {
        if (!hasPermission()) return null
        val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
            ?: return null

        return withTimeoutOrNull(timeoutMs) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                suspendCancellableCoroutine { continuation ->
                    val signal = android.os.CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(
                        LocationManager.GPS_PROVIDER,
                        signal,
                        context.mainExecutor,
                    ) { location -> continuation.resume(location) }
                }
            } else {
                lastKnown(manager)
            }
        } ?: lastKnown(manager)
    }

    /** Fallback for older devices, and for a GPS fix that never arrives indoors. */
    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

    private companion object {
        const val FIX_TIMEOUT_MS = 8_000L
    }
}

/** Six decimals is roughly 0.1 m; the API stores NUMERIC(9,6). */
fun Location.latitudeString(): String = String.format(java.util.Locale.US, "%.6f", latitude)

fun Location.longitudeString(): String = String.format(java.util.Locale.US, "%.6f", longitude)
