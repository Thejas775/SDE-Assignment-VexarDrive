package com.thejas.fleetmanagementtask.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.core.PingBuffer
import com.thejas.fleetmanagementtask.core.latitudeString
import com.thejas.fleetmanagementtask.core.longitudeString
import com.thejas.fleetmanagementtask.data.remote.dto.LocationPingRequest
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.auth.LoginActivity
import com.thejas.fleetmanagementtask.ui.common.isoUtc
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Streams the vehicle's position while a trip is running.
 *
 * A foreground service, not a background job: Android stops delivering location
 * to a backgrounded app within minutes, and a trip can last hours. The ongoing
 * notification is the price of that, and it is also honest — the driver can see
 * that tracking is on.
 */
class LocationTrackingService : LifecycleService(), LocationListener {

    private val buffer = PingBuffer()
    private var tripId: String? = null
    private var flushJob: Job? = null
    private var lastResultText: String? = null

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            else -> {
                val id = intent?.getStringExtra(EXTRA_TRIP_ID)
                if (id == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTracking(id)
            }
        }
        // Restarted by the system if killed; the trip is still running.
        return START_REDELIVER_INTENT
    }

    private fun startTracking(id: String) {
        if (tripId == id) return
        tripId = id
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.tracking_active)))
        requestUpdates()
        flushJob?.cancel()
        flushJob = lifecycleScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }
        val manager = ContextCompat.getSystemService(this, LocationManager::class.java) ?: return
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .forEach { provider ->
                manager.requestLocationUpdates(
                    provider,
                    MIN_INTERVAL_MS,
                    MIN_DISTANCE_M,
                    this,
                    mainLooper,
                )
            }
    }

    override fun onLocationChanged(location: Location) {
        buffer.add(
            LocationPingRequest(
                latitude = location.latitudeString(),
                longitude = location.longitudeString(),
                recordedAt = isoUtc(location.time),
                speedKmph = if (location.hasSpeed()) {
                    String.format(java.util.Locale.US, "%.2f", location.speed * MPS_TO_KMPH)
                } else {
                    null
                },
                heading = if (location.hasBearing()) {
                    String.format(java.util.Locale.US, "%.2f", location.bearing)
                } else {
                    null
                },
                accuracyM = if (location.hasAccuracy()) {
                    String.format(java.util.Locale.US, "%.2f", location.accuracy)
                } else {
                    null
                },
            )
        )
        if (buffer.size >= BATCH_TRIGGER) lifecycleScope.launch { flush() }
    }

    private suspend fun flush() {
        val id = tripId ?: return
        val batch = buffer.peek()
        if (batch.isEmpty()) return

        when (val result = ServiceLocator.locationRepository.send(id, batch)) {
            is ApiResult.Success -> {
                // Only drop what the server took; a partial failure retries.
                buffer.confirm(batch.size)
                lastResultText = getString(
                    R.string.tracking_sent,
                    result.data.accepted,
                    buffer.size,
                )
            }
            is ApiResult.Failure -> {
                // The trip ended or was cancelled: keep buffering is pointless.
                if (result.error.isConflict) {
                    buffer.clear()
                    stopTracking()
                    return
                }
                lastResultText = getString(R.string.tracking_queued, buffer.size)
            }
        }
        updateNotification(lastResultText ?: getString(R.string.tracking_active))
    }

    private fun stopTracking() {
        flushJob?.cancel()
        ContextCompat.getSystemService(this, LocationManager::class.java)
            ?.removeUpdates(this)
        tripId = null
        buffer.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        flushJob?.cancel()
        ContextCompat.getSystemService(this, LocationManager::class.java)?.removeUpdates(this)
        super.onDestroy()
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildNotification(text: String): Notification {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.tracking_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LoginActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_trips)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification(text: String) {
        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val ACTION_STOP = "stop_tracking"

        private const val MIN_INTERVAL_MS = 30_000L
        private const val MIN_DISTANCE_M = 25f
        private const val FLUSH_INTERVAL_MS = 60_000L
        private const val BATCH_TRIGGER = 5
        private const val MPS_TO_KMPH = 3.6f

        fun start(context: Context, tripId: String) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_TRIP_ID, tripId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
