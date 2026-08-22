package com.thejas.fleetmanagementtask.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TripDto(
    val id: String,
    @SerialName("trip_number") val tripNumber: String,
    val vehicle: VehicleSummaryDto,
    val driver: AssignmentDriverDto,
    val source: String,
    val destination: String,
    @SerialName("scheduled_start") val scheduledStart: String,
    @SerialName("scheduled_end") val scheduledEnd: String,
    val status: String,
    @SerialName("actual_start") val actualStart: String? = null,
    @SerialName("actual_end") val actualEnd: String? = null,
    @SerialName("start_odometer") val startOdometer: Int? = null,
    @SerialName("end_odometer") val endOdometer: Int? = null,
    @SerialName("start_latitude") val startLatitude: String? = null,
    @SerialName("start_longitude") val startLongitude: String? = null,
    @SerialName("end_latitude") val endLatitude: String? = null,
    @SerialName("end_longitude") val endLongitude: String? = null,
    // Decimal on the wire, so a string; never parse it just to display it.
    @SerialName("distance_km") val distanceKm: String? = null,
    val notes: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
) {
    val route: String get() = "$source → $destination"
    val isActive: Boolean get() = status == "STARTED" || status == "IN_PROGRESS"
    val isCancellable: Boolean get() = status != "COMPLETED" && status != "CANCELLED"
}

@Serializable
data class TripCreateRequest(
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("driver_id") val driverId: String,
    val source: String,
    val destination: String,
    @SerialName("scheduled_start") val scheduledStart: String,
    @SerialName("scheduled_end") val scheduledEnd: String,
    val notes: String? = null,
)

@Serializable
data class TripStatusRequest(val status: String)

@Serializable
data class TripStartRequest(
    @SerialName("start_odometer") val startOdometer: Int,
    val latitude: String? = null,
    val longitude: String? = null,
)

@Serializable
data class TripCompleteRequest(
    @SerialName("end_odometer") val endOdometer: Int,
    val latitude: String? = null,
    val longitude: String? = null,
    val notes: String? = null,
)

@Serializable
data class TripCancelRequest(val reason: String? = null)

@Serializable
data class LocationPointDto(
    val id: String,
    val latitude: String,
    val longitude: String,
    @SerialName("speed_kmph") val speedKmph: String? = null,
    @SerialName("recorded_at") val recordedAt: String,
)
